# Deploy: amadya.alphatech.ro

Amadya runs on the shared server, next to medic-buget and event-planner, behind the shared proxy in `repo/proxy`.
The server has 1 vCPU and 2 GB of RAM, which is too small to compile Amadya. So images are built on GitHub Actions,
pushed to **GitHub Container Registry** (`ghcr.io/balmus-apps/amadya-*`, private) and only pulled on the server.
Why deploys work the way they do: [ADR 0012](adr/0012-automated-deploy.md).

```
merge to main ─▶ GitHub Actions "Release"
                  └─ build & push ghcr.io/balmus-apps/amadya-{api,menu-web,admin,kitchen,waiter,cashier,queue-display,table}:<tag>
on the server, by hand (root) ─▶ scripts/deploy.sh <tag>
                              backup ─▶ pull ─▶ restart ─▶ api ready? ── no ──▶ previous release again
Server: proxy (80/443, TLS) ──web network──▶ amadya-web (internal Caddy) ─▶ api, menu-web, admin, kitchen, waiter, queue-display, table, postgres
```

`<tag>` is always the first 7 characters of the commit sha. The server runs exactly that tag; `AMADYA_TAG` in `.env`
says which one, and `.deploy/current` / `.deploy/previous` in the checkout record the last two that went live.

## Releasing

**Automatic deploys are switched off for now** (out of scope; see ADR 0012). Releasing has two steps:

1. **Build.** Merge a pull request into `main`. The Release workflow builds and pushes the images, tagged with the first
   7 characters of the commit sha. This takes about 5 minutes. A push that only changes `docs/`, `openspec/` or
   Markdown files does not build. Actions → **Release** → **Run workflow** builds on demand.
2. **Deploy, by hand.** On the server, as root, run `scripts/deploy.sh <tag>`. The api is down for 30–60 s while it
   restarts. **Rollback** is the same command with an earlier tag; the previous one is in `.deploy/previous`.

**What a deploy does** (`scripts/deploy.sh`):
1. Waits if another deploy is running.
2. Backs up the database (`scripts/backup.sh`). If the backup fails, nothing changes.
3. Pulls the images of the tag. If they don't exist, nothing changes.
4. Sets `AMADYA_TAG` in `.env` and restarts.
5. Waits up to 3 minutes for the api to report ready. If it never does, the previous release is started again and the
   run fails. The log names the backup taken in step 2.
6. On success, records the tag and removes Amadya images no longer used. Other apps' images are never touched.

A migration is **not** undone by step 5: the previous release then runs on the migrated database. Flyway migrations
here only add, so that is normally fine. If it is not, restore the backup (see below).

**From the Mac, without GitHub Actions** (fallback): `scripts/release.sh` builds and pushes the same images (one-time
`gh auth token | docker login ghcr.io -u <github-user> --password-stdin`, token with `write:packages`). Then, on the
server, run `scripts/deploy.sh <tag>` as root.

## Server: first time

1. DNS: `amadya.alphatech.ro` → server IP. This must be live before step 6, or the certificate can't be issued.
2. Swap, as a safety net on 2 GB. Skip it if `free -m` already shows swap:
   ```
   fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
   echo '/swapfile none swap sw 0 0' >> /etc/fstab
   ```
3. Registry login, read-only. Create a classic PAT with only `read:packages`, then:
   `docker login ghcr.io -u <github-user>` and paste the token at the prompt.
4. Get the compose files: `git clone git@github.com:balmus-apps/amadya.git /root/repo/amadya`. Only
   `docker-compose*.yml`, `infra/caddy/`, `scripts/` and `.env` are used.
5. `cp .env.example .env` and set at least these values:
   - `AMADYA_DOMAIN=amadya.alphatech.ro`: the bare host, no `https://`
   - `SPRING_PROFILES_ACTIVE=prod`: `dev` loads the demo menu instead
   - `POSTGRES_PASSWORD`, `JWT_SECRET` (`openssl rand -base64 48`) and `AMADYA_ADMIN_PASSWORD`
6. Start:
   ```
   docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build
   ```
7. In `repo/proxy`, pull the Caddyfile that has the `amadya.alphatech.ro` block. Validate it with the command in that
   README, then `docker compose restart caddy`.
8. Set up backups (next section; automatic deploys are switched off for now).

## Server: automatic deploys and backups (one time)

All of this runs as root on the server, except where it says "on the Mac".

> **Automatic deploys are switched off for now.** The Release workflow has no deploy job, so steps 1–4 and 6–8 (the
> `deploy` user, its key, the gate, the GitHub secrets and the first run) are not needed until they are switched back
> on. Step 5 (nightly backups) and step 9 (protect `main`) still apply.

**1. The `deploy` user.** It has no password and is not in the `docker` group. Its key can only run the deploy gate.
```
useradd --create-home --shell /bin/bash deploy
passwd --lock deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
```

**2. The key** (on the Mac):
```
ssh-keygen -t ed25519 -N '' -C github-deploy-amadya -f ~/.ssh/amadya_deploy
cat ~/.ssh/amadya_deploy.pub
```
Back on the server, paste that public key into the line below. `restrict` turns off shells, port forwarding and
terminals; `command=` replaces whatever the client asks to run.
```
cat > /home/deploy/.ssh/authorized_keys <<'EOF'
restrict,command="sudo /usr/local/sbin/amadya-deploy" ssh-ed25519 AAAA...paste...here github-deploy-amadya
EOF
chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys
```

**3. The one command `deploy` may run as root.** `sudo` would otherwise drop `SSH_ORIGINAL_COMMAND`, which carries
the requested version.
```
cat > /etc/sudoers.d/amadya-deploy <<'EOF'
Defaults!/usr/local/sbin/amadya-deploy env_keep += "SSH_ORIGINAL_COMMAND"
deploy ALL=(root) NOPASSWD: /usr/local/sbin/amadya-deploy
EOF
chmod 440 /etc/sudoers.d/amadya-deploy && visudo -cf /etc/sudoers.d/amadya-deploy
```

**4. The gate.** It is copied **outside** the checkout, so a commit cannot change what the key may do. Reinstall it
only when `scripts/server/amadya-deploy` itself changes.
```
cd /root/repo/amadya && git status --short   # must print nothing: deploys check out commits here
git pull && install -m 755 -o root -g root scripts/server/amadya-deploy /usr/local/sbin/amadya-deploy
```

**5. Nightly backups** at 03:15. They are kept with the pre-deploy ones, the 14 newest in total.
```
install -d -m 700 /root/backups/amadya
(crontab -l 2>/dev/null; echo '15 3 * * * /root/repo/amadya/scripts/backup.sh nightly >> /var/log/amadya-backup.log 2>&1') | crontab -
/root/repo/amadya/scripts/backup.sh manual   # check it works now
```

**6. Test from the Mac.** Replace `<server>` and use the tag that runs now (`grep AMADYA_TAG /root/repo/amadya/.env`;
if it says `latest`, use the short sha of `git -C /root/repo/amadya rev-parse HEAD`).
```
ssh -i ~/.ssh/amadya_deploy deploy@<server>                  # refused: only 'deploy <commit sha>' is allowed
ssh -i ~/.ssh/amadya_deploy deploy@<server> deploy <tag>     # backup, pull, restart, "OK: <tag> is live"
```

**7. GitHub secrets** (on the Mac, in the repo):
```
gh secret set DEPLOY_HOST --body '<server>'
gh secret set DEPLOY_USER --body 'deploy'
gh secret set DEPLOY_SSH_KEY < ~/.ssh/amadya_deploy
ssh-keyscan -t ed25519 <server> | gh secret set DEPLOY_KNOWN_HOSTS
```
Compare the scanned key with the server's own fingerprint before trusting it. On the Mac,
`ssh-keyscan -t ed25519 <server> | ssh-keygen -lf -` must match `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on
the server. Then delete the private key from the Mac if you like; GitHub has it.

**8. First run.** Run Release by hand with `tag` = the release that runs now. It deploys the same version, so it
checks the whole path without changing anything. From then on, merges deploy themselves.

**9. Protect `main`** (GitHub → Settings → Branches): require a pull request and block force-pushes. Whatever reaches
`main` goes to production.

**The checkout is managed by deploys.** Deploys check out the deployed commit in `/root/repo/amadya`, which leaves git
in a "detached HEAD" state. That is expected. Never edit tracked files there, and don't run `git pull`, except
before reinstalling the gate (step 4). `.env` and `.deploy/` are not tracked and are safe.

## Restore a backup

Backups are in `/root/backups/amadya`, newest first with `ls -lt`. Each name says when and why it was taken, e.g.
`amadya-20261013T031500Z-nightly.dump` or `amadya-20261013T101200Z-pre-deploy-3598903.dump`.

The database is replaced as a whole, so everything after the backup is lost. Run the release that matches the backup:
for a `pre-deploy-<tag>` backup, that is the release that ran before `<tag>` (`.deploy/previous`).
```
cd /root/repo/amadya
C="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
F=/root/backups/amadya/<file>.dump
$C stop api
$C exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" --force "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
$C exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' < "$F"
$C start api
```
If you also need to change the release, run Release with that `tag` after restoring.

## Memory budget
Measured with 150 concurrent orders plus 450 reads: the api peaks at 430 MB, menu-web at about 105 MB, Postgres at about
75 MB and Caddy at about 60 MB. The admin, kitchen, waiter, queue-display and table apps use about 12 MB each, about 730 MB in total. The limits in
`docker-compose.prod.yml` are ceilings above these numbers.
