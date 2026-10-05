# Deploy: amadya.alphatech.ro

Amadya runs on the shared server, next to medic-buget and event-planner, behind the shared proxy in `repo/proxy`.
The server has 1 vCPU and 2 GB of RAM, which is too small to compile Amadya. So images are built on a development machine,
pushed to **GitHub Container Registry** (`ghcr.io/balmus-apps/amadya-*`, private) and only pulled on the server.

```
Mac: scripts/release.sh ──push──▶ ghcr.io/balmus-apps/amadya-{api,menu-web,admin,kitchen}:<sha>, :latest
                                          │ pull
Server: proxy (80/443, TLS) ──web network──▶ amadya-web (internal Caddy) ─▶ api, menu-web, admin, kitchen, postgres
```

## Release (on the Mac)
1. One-time setup: `gh auth token | docker login ghcr.io -u <github-user> --password-stdin`. The gh token needs `write:packages`.
2. Commit, then run `scripts/release.sh`. It builds for `linux/amd64` and pushes tags `<git sha>` and `latest`.
   `PUSH=0 scripts/release.sh` only builds, without pushing.

The images carry the repo's `org.opencontainers.image.source` label. GHCR links them to `balmus-apps/amadya`, so they
inherit its access: they are private, and readable by anyone who can read the repo.

## Server: first time
1. DNS: `amadya.alphatech.ro` → server IP. This must be live before step 6, or the certificate can't be issued.
2. Swap, as a safety net on 2 GB. Skip it if `free -m` already shows swap:
   ```
   fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
   echo '/swapfile none swap sw 0 0' >> /etc/fstab
   ```
3. Registry login, read-only. Create a classic PAT with only `read:packages`, then:
   `docker login ghcr.io -u <github-user>` and paste the token at the prompt.
4. Get the compose files: `git clone git@github.com:balmus-apps/amadya.git`. Only `docker-compose*.yml`, `infra/caddy/`
   and `.env` are used.
5. `cp .env.example .env` and set at least these values:
   - `AMADYA_DOMAIN=amadya.alphatech.ro`: the bare host, no `https://`
   - `SPRING_PROFILES_ACTIVE=prod`: `dev` loads the demo menu instead
   - `POSTGRES_PASSWORD`, `JWT_SECRET` (`openssl rand -base64 48`) and `AMADYA_ADMIN_PASSWORD`
   - `AMADYA_TAG`: optional. It pins a release sha; the default is `latest`.
6. Start:
   ```
   docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build
   ```
7. In `repo/proxy`, pull the Caddyfile that has the `amadya.alphatech.ro` block. Validate it with the command in that
   README, then `docker compose restart caddy`.

## Server: update
```
git pull   # only when compose or Caddy files changed
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build
docker image prune -f   # 25 GB disk: drop superseded images
```
Rollback: set `AMADYA_TAG=<previous sha>` and run the same two compose commands. Flyway migrations only move forward, so a
rollback across a migration needs the database restored from a backup.

## Memory budget
Measured with 150 concurrent orders plus 450 reads: the api peaks at 430 MB, menu-web at about 105 MB, Postgres at about
75 MB and Caddy at about 60 MB. The admin and kitchen apps use 12 MB each, about 700 MB in total. The limits in
`docker-compose.prod.yml` are ceilings above these numbers.
