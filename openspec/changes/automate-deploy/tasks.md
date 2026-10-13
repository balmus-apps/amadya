# Tasks

## 1. Server scripts

- [ ] 1.1 Write `scripts/backup.sh <reason>`. Behaviour: a `pg_dump -Fc` into `/root/backups/amadya` (overridable by
  `BACKUP_DIR` for testing), written to a temp file then renamed, refused when empty, keeping the 14 newest. Verify
  against the local stack with `BACKUP_DIR` set to a scratch dir: three runs leave 3 files, and a fifteenth run deletes
  the oldest. Confirm one dump restores into a throwaway Postgres with `pg_restore --list`.
- [ ] 1.2 Write `scripts/deploy.sh <tag>`. Behaviour: lock, backup, `AMADYA_TAG` swap in `.env`, pull, up, readiness
  poll through Caddy, revert on failure, record current/previous, label-filtered prune. Make the compose files and the
  `.env` path overridable so it can run locally. Verify on the local stack:
  - (a) deploy of the current tag succeeds and writes `.deploy/current`
  - (b) a nonexistent tag fails at pull and leaves the running containers unchanged
  - (c) with `READY_TIMEOUT=5` and the API stopped, the revert path restores the previous tag and prints the backup
    path
- [ ] 1.3 Write `scripts/server/amadya-deploy`, the root-owned gate. It accepts only
  `SSH_ORIGINAL_COMMAND="deploy <7–40 hex>"`, then fetches, checks out the commit detached, and `exec`s that commit's
  `scripts/deploy.sh <first 7 chars>`. Verify with a shell test that runs it with `DRY_RUN=1`:
  - `deploy 3598903` passes
  - `deploy 3598903; rm -rf /`, an empty value, `bash` and `deploy latest` are all rejected with exit 1

## 2. Release workflow

- [ ] 2.1 Restructure `.github/workflows/main.yml` into the `meta` → `image` → `deploy` jobs from the design:
  - triggers: `push` to `main` with `paths-ignore`, and `workflow_dispatch` with the `deploy` and `tag` inputs
  - tag = `${GITHUB_SHA::7}`

  Verify with `actionlint` (run via its Docker image) and by reading the rendered job graph in the first real run.
- [ ] 2.2 Write the deploy job: a strict `known_hosts` from `DEPLOY_KNOWN_HOSTS`, the key from `DEPLOY_SSH_KEY`, then
  `ssh $DEPLOY_USER@$DEPLOY_HOST deploy <tag>`, streaming output into the run log. Fail fast with a clear message when
  a secret is missing. Verify with `actionlint`, and check that a run without secrets fails at the "missing secret"
  step, not later.
- [ ] 2.3 Change `scripts/release.sh` to tag with `git rev-parse HEAD | cut -c1-7`. Verify with `PUSH=0
  scripts/release.sh` printing 7-character tags.

## 3. Documentation

- [ ] 3.1 Rewrite `docs/deploy.md`. Sections:
  - the automatic flow
  - manual build-only and redeploy/rollback via the `tag` input
  - one-time server setup, as exact commands: `deploy` user, `authorized_keys` line, sudoers file checked with
    `visudo -cf`, wrapper install, backup dir, cron line
  - generating the key pair and the four GitHub secrets (`ssh-keyscan` for known hosts)
  - restoring a backup with `pg_restore`
  - the rule: no manual edits in the server checkout

  Verify every command block by running it against a scratch Ubuntu container
  (`docker run --rm -it ubuntu:24.04`), except the GitHub UI steps.
- [ ] 3.2 Add ADR `docs/adr/0012-automated-deploy.md`, with the decisions and the security model from design.md
  (short; it links to this change). Verify it is linked from `docs/deploy.md`.

## 4. Go live (owner + assistant, on the real server)

- [ ] 4.1 The owner runs the one-time server setup from `docs/deploy.md`. Verify with `ssh deploy@server` (no
  command): it is refused, and `ssh deploy@server deploy <current tag>` performs a no-op redeploy.
- [ ] 4.2 The owner adds the four secrets and runs Release with `tag` = the current production tag. Verify the run
  shows the backup line, `ready` within 3 minutes, and `.deploy/current` on the server holds that tag.
- [ ] 4.3 The owner enables branch protection on `main` (PR required, no force-push). Verify a direct push to `main`
  is rejected.

## Workflow follow-up

- After the first automatic deploy from a real merge succeeds, archive this change (`/opsx:archive`) so
  `openspec/specs/deployment/spec.md` becomes the living spec.
