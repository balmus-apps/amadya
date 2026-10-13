# Design

## Context

- **Workflow today:** `.github/workflows/main.yml` (Release) builds 7 images in a matrix and pushes them to GHCR, tagged
  `<short sha>` and `latest`. It runs only on `workflow_dispatch` and is serialized by `concurrency: release`.
- **Server:** the stack lives in `/root/repo/amadya` (a git checkout plus `.env`) and is run as `root` with
  `docker compose -f docker-compose.yml -f docker-compose.prod.yml`. `AMADYA_TAG` in `.env` selects the release.
  Postgres and the API publish no host port, but the internal Caddy can reach the API:
  `docker compose exec -T caddy wget -qO- http://api:8080/actuator/health/readiness` returns `{"status":"UP"}`.
  This was checked on the local stack. The API image has neither `curl` nor `wget`; the `caddy:2-alpine` image has
  `wget`.
- **Other apps:** medic-buget and event-planner run on the same 1 vCPU / 2 GB host. The shared proxy in `repo/proxy`
  owns ports 80 and 443.

## Goals / Non-Goals

**Goals:**
- Deploy logic is versioned in this repo and reviewed like code.
- The SSH credential stored in GitHub is useless for anything but triggering a deploy.
- Every deploy and failure is visible in the GitHub run log.

**Non-Goals:**
- Zero downtime.
- Multiple environments.
- Managing the other apps.

## Decisions

### 1. Workflow shape: `meta` → `image` (matrix) → `deploy`

- **`meta`** computes `tag`: `inputs.tag` if given, otherwise the first 7 characters of `github.sha`.
- **`image`** builds as today, using that `tag`. It is skipped when `inputs.tag` is set.
- **`deploy`** runs only when all of these hold:
  - the builds succeeded, or they were skipped because a tag was given
  - the event is a push, or `inputs.deploy` is true
  - the run was not cancelled
- **Triggers:**
  - `push: branches: [main]`, with `paths-ignore: [docs/**, openspec/**, '**/*.md']`
  - `workflow_dispatch` with inputs `deploy` (boolean, default true) and `tag` (string, optional)
- **Concurrency:** the existing `concurrency: { group: release, cancel-in-progress: false }` already queues a second
  run behind the first. That is one half of "one deploy at a time"; the server-side lock below is the other.
- **Tag rule:** `${GITHUB_SHA::7}` in the workflow and `git rev-parse HEAD | cut -c1-7` in `scripts/release.sh`.
  `git rev-parse --short` (today) may print 8 or more characters, depending on the clone; a fixed cut cannot drift.

*Alternative considered:* a separate deploy workflow triggered by `workflow_run`. Rejected: it adds a second file and
indirect triggers for no gain, and one run that shows build and deploy together is easier to read.

### 2. Transport: plain `ssh` from the runner, with pinned host keys

The deploy job writes `DEPLOY_SSH_KEY` to a 0600 file and `DEPLOY_KNOWN_HOSTS` to `~/.ssh/known_hosts`, then runs
`ssh deploy@$DEPLOY_HOST deploy <tag>`. Host key checking stays strict.

*Alternative considered:* a third-party SSH action. Rejected: it adds a dependency and trust in someone else's code for
about five lines of shell.

### 3. Server gate: forced command + one `sudo` rule + root-owned wrapper outside the checkout

- `deploy` is a normal user. It is not in the `docker` group, because docker group membership is equivalent to root.
- Its `authorized_keys` entry is:
  `command="sudo /usr/local/sbin/amadya-deploy",no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty <key>`
  Whatever the client asks for, only this command runs. The requested text arrives in `SSH_ORIGINAL_COMMAND`.
- The sudoers file `/etc/sudoers.d/amadya-deploy` holds two lines and nothing else:
  - `deploy ALL=(root) NOPASSWD: /usr/local/sbin/amadya-deploy`
  - `Defaults!/usr/local/sbin/amadya-deploy env_keep += "SSH_ORIGINAL_COMMAND"`

  `sudo` normally clears the environment, so without the second line the wrapper could not see what was requested.
- `/usr/local/sbin/amadya-deploy` is root-owned and copied once from `scripts/server/amadya-deploy` in the repo. It
  accepts exactly `deploy <7–40 hex chars>`; anything else exits 1. It then:
  1. `git fetch`es in `/root/repo/amadya`
  2. refuses a commit that is not on `main`
  3. copies `scripts/deploy.sh` and `scripts/backup.sh` **from the newest `origin/main`** to `/run/amadya-deploy-tools`
  4. checks out the target commit (detached), for its compose files, which match its images
  5. runs the copied `deploy.sh` with `AMADYA_ROOT` pointing at the checkout

  The gate lives outside the git checkout, so a pushed commit cannot widen what the key may do.

  *Revised during implementation:* the first version ran the *target* commit's own `deploy.sh`. A test showed that
  a rollback to a release older than these scripts then fails, because there is no script to run. It would also mean
  every rollback runs that release's old deploy logic. Taking the scripts from the newest `main` fixes both.

*Alternatives considered:*
- `root` login with the GitHub key: simplest, but one leaked secret means full control of a host shared by three
  apps.
- `deploy` in the `docker` group: equally root-equivalent.

### 4. `scripts/deploy.sh <tag>`

1. Takes `flock /run/amadya-deploy.lock`, the server-side half of "one at a time".
2. Runs `scripts/backup.sh pre-deploy-<tag>` and aborts if it fails.
3. Reads the current `AMADYA_TAG` from `.env` as *previous*; on the first run it may be `latest`.
4. Sets `AMADYA_TAG=<tag>` in `.env`, or adds the line if missing. Recording the tag in `.env` keeps manual
   `docker compose up` commands on the deployed release.
5. Runs `compose pull`, then `compose up -d --no-build`.
6. Polls readiness through the Caddy container every 5 s for 180 s.
7. If the API never becomes healthy:
   - sets `AMADYA_TAG` back to *previous*
   - runs `compose up -d --no-build`
   - prints the backup path and the restore command
   - exits 1
8. If the API becomes healthy:
   - writes `.deploy/current` and `.deploy/previous`
   - runs `docker image prune -af --filter label=org.opencontainers.image.source=https://github.com/balmus-apps/amadya`

   The label filter means only unused **Amadya** images are pruned, never the other apps'.

### 5. `scripts/backup.sh <reason>`

- Runs `docker compose exec -T postgres pg_dump -Fc` with the DB and user from `.env`.
- Writes to `/root/backups/amadya/amadya-<UTC timestamp>-<reason>.dump.tmp`, checks the file is non-empty, then
  renames it, so a broken dump never looks like a backup.
- Keeps the 14 newest `*.dump` files.
- Uses `-Fc` because it is already compressed and allows `pg_restore --clean` of single objects if needed.
- Nightly runs come from root's crontab: `15 3 * * * /root/repo/amadya/scripts/backup.sh nightly`, with output to
  `/var/log/amadya-backup.log`.

### 6. A migration is not undone by the automatic revert

The previous release is started against the *migrated* schema. Flyway migrations here only add, so that has worked so
far, but it is not guaranteed. The revert therefore always prints the pre-deploy backup and the exact `pg_restore`
command. Restoring stays a human decision.

## Risks / Trade-offs

- **[Code merged to `main` runs as root on the server]** → This is no wider than today: the same merge already ships
  images that run there. Mitigations:
  - enable branch protection on `main`: PR required, no force-push
  - the gate in `/usr/local/sbin` is not in the repo
- **[A bad migration plus auto-revert]** → The revert restarts old code on a new schema.
  - Mitigation: the backup is always taken first, and the failed run prints how to restore it.
  - Destructive migrations stay rare and need a manual plan, as already noted in `docs/deploy.md`.
- **[API restart causes 30–60 s of downtime on every merge]** → Same as today's manual deploy. Merging outside service
  hours avoids it; zero-downtime is out of scope.
- **[Docs-only filter misses a case]** → The worst outcome is an unnecessary redeploy of the same code: about 3
  minutes, with no functional effect.
- **[Disk fills with backups]** → Retention is capped at 14. The size is printed after each backup so growth is
  visible in the run log.
- **[The server checkout becomes detached]** → `git pull` in `/root/repo/amadya` no longer applies. `docs/deploy.md`
  says to deploy only through the workflow (or `amadya-deploy` by hand) and never to edit tracked files there. `.env`
  is untracked and unaffected.

## Migration Plan

1. Merge this change. It does not deploy automatically until the secrets exist; without them the deploy job fails
   with a clear message, and the images are still built.
2. One-time server setup, following the new `docs/deploy.md`:
   - create the user, key restriction and sudoers rule
   - install the wrapper
   - create the backup directory and cron line
   - test the wrapper by hand with the current tag
3. Add the four secrets in GitHub.
4. Run Release by hand with `tag` = the release already in production. This checks the whole path without changing
   versions.
5. From then on, merges deploy themselves.

Rollback of this change: delete the secrets (deploys stop) and keep releasing by hand as before. Nothing on the server
depends on the workflow.
