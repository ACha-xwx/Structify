# Structify Release Source

This release repository contains the reviewed application source, contracts, tests, deployment templates, and operator documentation for Structify.

The release source intentionally excludes private courseware and local state. The following paths stay outside Git and are mounted or provisioned separately in production:

- `teach_ppt/`, `pdfs/`, `lesson-materials/`, and `presentation-materials/`
- `knowledge/private/`
- `data.db*`, `.jwt-secret`, `.env` files, uploads, and backups
- `node_modules/`, `apps/server/target/`, `output/`, and `.playwright-cli/`
- local planning and audit logs (`task_plan.md`, `findings.md`, and `progress.md`)
- local production-input answers and generated design/planning notes
  (`docs/production-input-*.md` and `docs/superpowers/`)

The release repository is initialized with fresh history so it does not inherit private-material objects from the legacy public repository. Production secrets, private presentation resources, database state, and Caddy data must be supplied through the server secret and backup procedures described in `docs/production-deployment.md`.
