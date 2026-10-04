# ADR 0008 — Media storage: filesystem volume by default, any S3 service optionally

**Status:** Accepted — 2026-10-04 (supersedes the MinIO container from Phase 0)

## Context
Product photos, promotion banners and the logo must be stored and served. Phase 0 planned a MinIO container,
but MinIO no longer publishes free community images (Docker Hub `minio/minio` is gone; quay.io needs a login).

## Decision
- A `FileStorage` port in the `media` module with two adapters, chosen by `MEDIA_PROVIDER`:
  - `filesystem` (default): a Docker volume (`mediadata`). This suits one install per restaurant, and the volume is backed up with the database.
  - `s3`: any S3-compatible service (AWS S3, Cloudflare R2, Garage, RustFS, ...) through the MinIO Java client, which speaks plain S3.
- Uploads (`POST /admin/files`): ADMIN/MANAGER only, max 5 MB. The type is detected from the file's magic bytes (JPEG, PNG, WebP);
  the client's content type and file name are ignored. Keys are random UUIDs.
- Files are served by the API (`GET /files/{key}`) with `Cache-Control: immutable` and `nosniff`, so the bucket or volume is never public.
- The S3 adapter is tested against RustFS (Apache-2.0) in Testcontainers.

## Consequences
- One fewer container in the default stack.
- Moving an install to S3 means copying the volume's files to the bucket; keys and URLs stay the same.
