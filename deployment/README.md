# Production deployment assets

The production target is `https://structify.cn`. The default topology keeps an
existing host Caddy in control of public TLS and routes only Structify traffic
to dedicated loopback ports:

```text
Internet :443
    -> existing host Caddy
       /api/v1/*       -> Spring loopback :18792 (MySQL, Flyway)
       /api/*          -> Node loopback :18791 (compatibility service)
       /presentation/* -> Node loopback :18791 (JWT/HMAC check)
       / and static    -> Node loopback :18791
```

`CADDY_MODE=host` is the production default. It starts only MySQL, Node, and
Spring, then requires the operator to import and validate
[`Caddyfile.host.production`](Caddyfile.host.production) in the existing host
Caddy configuration. Structify never binds public `80/443` in that mode. The
`container` mode is available only for a dedicated host and explicitly starts
the profiled Caddy service. Node and Spring bind the configurable loopback
ports `18791` and `18792`; MySQL has no host port and is reachable only on the
internal Compose network.

Host mode is an execute-time handoff, not merely a port choice. Set
`HOST_CADDY_CONFIG` to the complete existing Caddyfile that imports
`Caddyfile.host.production`; `preflight.sh --execute` runs `caddy validate`
against that file before it invokes Docker. The default `low-memory` profile
reads Linux `MemAvailable` and requires the larger of its configured budget and
the 1,088 MiB service hard-cap total plus its 256 MiB host reserve (1,344 MiB).
A host with another TLS owner must use an explicitly reviewed proxy integration
or a dedicated Structify host; do not start the container-Caddy profile alongside it.

The default build bases are the official Node 22 Bookworm and Eclipse Temurin
21 images. When Docker Hub is unavailable, an operator may set
`NODE_BASE_IMAGE`, `JAVA_BUILD_IMAGE`, and `JAVA_RUNTIME_IMAGE` in the private
production environment file to a verified compatible mirror. Record the
resolved digests with the release; do not add mirror credentials to source.

PDF courseware stays outside the image build context. Set
`PDF_SOURCE_DIR_HOST` to the absolute host directory that contains the
reviewed PDFs for a release; Compose mounts it read-only at
`/app/default-pdfs`. On the first Node boot for a new `node-pdfs` volume, the
entrypoint copies non-conflicting source files into the writable volume and
creates `.course-pdfs-seeded`. This marker is independent of the historical
`.seeded` marker, so an existing volume receives the course-PDF baseline once
when this source is introduced without overwriting user uploads. Later
restarts never overwrite uploads or operator-managed files in that volume, so
updating the source directory alone does not refresh an existing deployment.
Ensure the source directory and its files are readable by the Node container
user.

Use [`../docs/production-deployment.md`](../docs/production-deployment.md) as
the operator runbook. All operational scripts are under `scripts/` and are
dry-run by default. Mutating actions require both `--execute` and an exact
domain-specific `--confirm` value.

Files:

- `docker-compose.production.yml` - Node, Spring, MySQL, and optional profiled Caddy topology.
- `Dockerfile.node` / `Dockerfile.node.dockerignore` / `node-entrypoint.sh` - non-root Node compatibility image; the build context allowlist excludes private media and databases, while the read-only PDF source is seeded once into a dedicated writable upload volume.
- `Caddyfile.production` - dedicated-host container Caddy routes and SSE flush behavior.
- `Caddyfile.host.production` - append-only shared-host site block for `structify.cn`.
- `.env.spring.example` - placeholder-only production environment template.
- `scripts/init-production-env.sh` - Linux-only secret-file generator; it
  writes fresh database/JWT values outside the checkout, refuses overwrite,
  and leaves optional model/SMTP/sandbox integrations disabled.
- `scripts/preflight.sh` - local configuration and path checks.
- `scripts/deploy.sh` - build, backup, Flyway-on-start, and service rollout.
- `scripts/backup.sh` / `restore.sh` - MySQL, SQLite, and optional private-media snapshots.
- `scripts/migrate-sqlite.sh` - read-only legacy audit and staging-only SQL generation.
- `scripts/health-check.sh` / `smoke.sh` - loopback and public read-only checks.
- `scripts/dns-check.sh` - DNS visibility check; it never changes DNS records.
- `scripts/rollback.sh` - image-only rollback with explicit confirmation.

This workspace has a verified `origin/main` source reference, but the current
integration changes are intentionally uncommitted. Do not promote an image
until the release operator creates and records an immutable release commit/tag,
source revision, and artifact digest, and confirms that public-history and
private-courseware checks have passed.

The Spring rollout reuses Flyway V11 for DSVP evidence. Release verification
must exercise both modes: a context-free request returns a non-persisted
preview, while an authenticated source-verified request commits one linked
animation/snapshot/event evidence unit with a non-null chapter. Do this only
after a restore-tested backup and before DNS promotion.

The legacy Node login bridge uses a dedicated `NODE_COMPAT_JWT_SECRET`; it is
not the Spring `JWT_SECRET`. In host or container mode, Node receives only the
compatibility key. Spring accepts it only when `NODE_COMPAT_ENABLED=true` and
only on the restricted learning/animation evidence endpoints documented in the
API difference matrix.
