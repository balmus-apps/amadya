# ADR 0012 — Automated deploy on merge

**Status:** Accepted — 2026-10-13. Planned and built as OpenSpec change
[`automate-deploy`](../../openspec/changes/automate-deploy/) (moves to `openspec/changes/archive/` once archived).

## Context
Deploying meant five manual steps on the shared server: release, SSH in, back up, pull and restart, check. The first
production deploy of a migration (V16) went out without its backup because that step was manual. The server (1 vCPU,
2 GB) also runs two other apps, so whatever GitHub can do there must be narrow.

## Decisions
1. **Merge to `main` = release.** The Release workflow builds the images and deploys them; documentation-only pushes
   do neither. The PR review is the release gate. "Run workflow" can build without deploying, or redeploy any earlier
   tag, which is the rollback.
2. **Exact versions.** Images are tagged with the first 7 characters of the commit sha. The server runs that tag, never
   `latest`, and records the current and previous tags.
3. **Every deploy backs up first, and undoes itself if the api does not come up.** The steps are backup, pull,
   restart, then wait up to 3 minutes for readiness. If the api is not ready, the previous release is started again and
   the run fails, pointing at the backup. A database migration is not undone automatically; restoring is a manual
   decision ([deploy.md](../deploy.md#restore-a-backup)). Backups also run nightly; the newest 14 are kept, on the
   server.
4. **GitHub's key can only deploy.**
   - It belongs to a `deploy` user, which is not in the `docker` group.
   - The key has `restrict` and a forced command: `sudo /usr/local/sbin/amadya-deploy`.
   - That gate is installed outside the git checkout. It accepts only `deploy <commit on main>`.
   - It uses the deploy scripts from the newest `main` and checks out the target commit for its compose files. So a
     rollback to any release, even one older than the scripts, runs current deploy logic.

## Consequences
- **Downtime:** a merge reaches production in about 5 minutes, with 30–60 s of api downtime. Zero-downtime deploys
  and staging are out of scope.
- **Trust:** code merged to `main` runs as root on the server, as the images it ships already do. `main` must be
  protected: PRs only, no force-push.
- **The server checkout is managed by deploys** and sits in "detached HEAD" state. Nobody edits it by hand.
- **Backups stay on the server.** Copying them off the server is a separate, later change.
