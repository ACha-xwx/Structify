# Production deployment assets

The production target is `https://structify.cn`. The complete topology is in
[`docker-compose.production.yml`](docker-compose.production.yml) and uses
Caddy for TLS and routing:

```text
Internet :443
    -> caddy
       /api/v1/*      -> spring-api:8792 (Spring, MySQL, Flyway)
       /api/*         -> node:8791 (legacy compatibility service)
       /presentation/* -> node:8791 (Node JWT or short-lived HMAC check)
       / and static   -> node:8791
```

Only Caddy publishes ports 80/443. Node 8791 and Spring 8792 are published on
the host loopback interface for health checks, not on a public interface.
MySQL has no host port and is reachable only on the internal Compose network.

Use [`../docs/production-deployment.md`](../docs/production-deployment.md) as
the operator runbook. All operational scripts are under `scripts/` and are
dry-run by default. Mutating actions require both `--execute` and an exact
domain-specific `--confirm` value.

Files:

- `docker-compose.production.yml` - Node, Spring, MySQL, and Caddy topology.
- `Dockerfile.node` / `Dockerfile.node.dockerignore` / `node-entrypoint.sh` - non-root Node compatibility image; the build context allowlist excludes private media and databases, while reviewed public PDFs are seeded into a dedicated writable upload volume.
- `Caddyfile.production` - `structify.cn` routes and SSE flush behavior.
- `.env.spring.example` - placeholder-only production environment template.
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
