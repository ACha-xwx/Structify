# Structify Course Assets

This directory contains the reviewed source courseware bundled with the Structify release:

- `source-ppt/`: the 22 source teaching decks, tracked with Git LFS.
- `reference-pdf/`: the 23 compact reference and exercise PDFs.
- `manifest.sha256`: checksums for the copied assets.

The application still reads production course resources from the configured private resource
mount (`KNOWLEDGE_DIR`, `RESOURCE_DIR`, `PRESENTATION_DIR`, and `PDF_SOURCE_DIR_HOST`). These
files are a source/archive bundle for the repository and are not exposed as a public static
directory by the frontend.

Runtime state, databases, credentials, raw OCR, rendered browser evidence, and generated slide
images are intentionally excluded from this bundle.
