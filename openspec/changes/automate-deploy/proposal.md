# Proposal

## Why

A release takes five manual steps:
1. Run the Release workflow.
2. SSH to the server.
3. Back up the database.
4. Pull the new images and restart the stack.
5. Check that the API came up.

The backup was skipped on the first production deploy of the activity log (V16) because it had to be remembered. Each
manual step can be forgotten or done against the wrong version. The steps never change, so they should run by
themselves after every merge.

## What Changes

- **Trigger:** the Release workflow also runs on every push to `main`, not only from the "Run workflow" button. After
  the images are built it **deploys** them to the server. A docs-only commit (`docs/`, `openspec/`, `*.md`) neither
  builds nor deploys.
- **Manual run:** the "Run workflow" button gets two inputs:
  - `deploy` (yes/no): build without deploying.
  - `tag`: redeploy an existing release without building, which is also the rollback button.
- **Deploy script:** a new `scripts/deploy.sh` on the server runs every deploy. Its steps, in order:
  1. Back up the database.
  2. Bring the compose files up to the same commit.
  3. Pull that commit's images.
  4. Restart.
  5. Wait for the API to report healthy.
  6. If the API stays unhealthy, put the previous images back and fail the run.
  7. Otherwise record the deployed version and prune old images.
- **Exact versions:** deploys use the commit's image tag, never `latest`, so the server always runs a known commit.
- **Shared tag rule:** the image tag becomes exactly the first 7 characters of the commit sha, in both the workflow and
  `scripts/release.sh`. Today it is `git rev-parse --short`, which can be longer on some machines.
- **Restricted deploy login:** GitHub connects as a `deploy` user whose SSH key can only run the deploy, with a
  validated version argument. It cannot open a shell or run anything else on the server.
- **Nightly backups:** a nightly cron job backs up the database with the same script and keeps the last 14 backups.
- **Docs:** `docs/deploy.md` describes the one-time server and GitHub setup, the automatic flow, rollback and restore.

## Capabilities

### New Capabilities
- `deployment`: How a commit on `main` reaches production: when a deploy happens, what it does to the database and the
  running stack, how failures and rollbacks behave, and how backups are kept.

### Modified Capabilities

(none)

## Impact

- **Files:**
  - `.github/workflows/main.yml`: triggers, inputs, and a deploy job.
  - New `scripts/deploy.sh` and `scripts/backup.sh`.
  - `scripts/release.sh`: the shared tag rule.
  - `docs/deploy.md`.
- **Server, one-time:**
  - a `deploy` user with a restricted SSH key and one `sudo` rule
  - a backup directory
  - a cron line

  The stack itself (`/root/repo/amadya`, `.env`, compose files) stays where it is.
- **GitHub:** four repository secrets: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`.
- **Other apps on the server** (medic-buget, event-planner, the shared proxy) are not touched.
- **Behaviour change:** merging to `main` now changes production within about 5 minutes. Reviewing the PR becomes the
  release gate.

## Out of scope

- Staging environment, blue/green or zero-downtime deploys. The API restarts in place, as it does today, with about
  30–60 s of downtime.
- Copying backups off the server, e.g. to object storage. This is a later change.
- Automatic database restore. Restoring stays a documented manual step.
- Deploying the other apps on the server.

## Decisions for the owner

Each decision lists the recommended option, which this change assumes.

1. **When to deploy.** *Recommended: on every merge to `main`.* The alternative is button-only: keep the manual "Run
   workflow" step and deploy only from it.
2. **Approval click before production.** *Recommended: none; the PR review is the gate.* GitHub's "required reviewers"
   on an environment needs a paid plan for private repositories. If the organization has one, it can be added later by
   putting the deploy job in a `production` environment, without changing anything else.
3. **How GitHub reaches the server.** *Recommended: restricted `deploy` user.* The alternative is GitHub's key logging in
   as `root`. That is simpler, but a leaked key would control all three apps on the server.
4. **Backups.** *Recommended: nightly plus before every deploy, keep 14, on the server only (`/root/backups/amadya`).*
