# Spec Delta

## Purpose

Defines how a commit on `main` reaches production: when a deploy runs, how the database is protected, how the running
version is chosen and verified, and how a failed or unwanted release is undone.

## ADDED Requirements

### Requirement: Merging to main deploys it
A push to `main` SHALL build the release images and, when every image built, deploy that commit to production without
manual steps. A push that changes only documentation (`docs/`, `openspec/`, Markdown files) SHALL NOT build or deploy.

#### Scenario: Merged pull request reaches production
- **WHEN** a pull request that changes application code is merged into `main`
- **THEN** the Release workflow builds all images tagged with the commit
- **AND** production runs that commit once the workflow finishes successfully

#### Scenario: An image fails to build
- **WHEN** any image of the commit fails to build
- **THEN** no deploy runs and production keeps its current version

#### Scenario: Documentation-only change
- **WHEN** a push to `main` changes only files under `docs/` or `openspec/`, or Markdown files
- **THEN** the Release workflow does not run

### Requirement: Manual release controls
A manually started release SHALL let the owner build without deploying, and SHALL let the owner deploy an
already-built release by its tag without building anything.

#### Scenario: Build only
- **WHEN** the owner runs the Release workflow with `deploy` set to no
- **THEN** images are built and pushed, and production is not changed

#### Scenario: Redeploy or roll back to an earlier release
- **WHEN** the owner runs the Release workflow with `tag` set to an earlier release's tag
- **THEN** no image is built and production is switched to that release

### Requirement: Production runs an exact release
Every deploy SHALL run images identified by the commit's tag (the first 7 characters of the commit sha), never a
moving tag such as `latest`. The server SHALL record which tag is currently deployed and which tag was deployed before
it.

#### Scenario: Deployed version is known
- **WHEN** a deploy of commit `3598903…` succeeds
- **THEN** every Amadya service runs images tagged `3598903`
- **AND** the server records `3598903` as current and the prior tag as previous

### Requirement: Database backup before every deploy
A deploy SHALL take a complete database backup before it changes anything, and SHALL stop without changing anything
when the backup fails.

#### Scenario: Backup taken
- **WHEN** a deploy starts
- **THEN** a compressed backup of the Amadya database is written before any image is pulled or any service restarted

#### Scenario: Backup fails
- **WHEN** the backup cannot be written (for example, the disk is full)
- **THEN** the deploy fails, production keeps its current version, and the workflow run is marked failed

### Requirement: Failed release is reverted automatically
After restarting, a deploy SHALL wait for the API to report healthy. If it is not healthy within 3 minutes, the deploy
SHALL start the previously deployed release again and mark the run failed.

#### Scenario: Healthy release
- **WHEN** the API reports healthy within 3 minutes of the restart
- **THEN** the deploy succeeds and old images no longer used are pruned

#### Scenario: Release does not come up
- **WHEN** the API does not report healthy within 3 minutes
- **THEN** the previous release is started again
- **AND** the workflow run fails and shows where the backup taken at the start of the deploy is stored

### Requirement: One deploy at a time
At most one deploy SHALL run against the server at any moment; a second one SHALL wait for the first to finish instead
of interleaving with it.

#### Scenario: Two merges in quick succession
- **WHEN** a second commit is merged while the first one is still deploying
- **THEN** the second deploy starts only after the first one has finished

### Requirement: Deploy access is limited to deploying
The credential GitHub uses to reach the server SHALL only be able to deploy Amadya with a release tag. It SHALL NOT be
able to open a shell, run other commands, or reach the other applications on the server.

#### Scenario: Deploy command
- **WHEN** the deploy credential requests a deploy of a valid commit sha
- **THEN** the deploy runs

#### Scenario: Anything else
- **WHEN** the deploy credential requests a shell, another command, or a malformed version
- **THEN** the server refuses and nothing runs

### Requirement: Nightly backups with bounded retention
The database SHALL also be backed up every night. The server SHALL keep the 14 most recent backups (nightly and
pre-deploy together) and delete older ones.

#### Scenario: Retention
- **WHEN** a new backup is written and 15 backups exist
- **THEN** the oldest backup is deleted
