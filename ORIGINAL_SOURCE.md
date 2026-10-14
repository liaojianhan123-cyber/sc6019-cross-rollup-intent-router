# Source provenance

This independent repository starts from a complete source snapshot of:

- Source repository: https://github.com/liaojianhan123-cyber/defi-cli
- Source branch: `main`
- Source commit: `9e111ead1cfdc718018105937a81e913ef3bc6a4`
- Source Git tree: `ab2d61f6f45e1d058a7945bd12dbaeb9abdf778a`
- Snapshot verified on: 2026-10-13

All 257 source files, their contents and Git file modes were checked against the
source Git tree before adding the independent-repository documentation.

## Contributions carried forward

The Go CLI is based on Gustavo Gonzalez's original `defi-cli` project:
https://github.com/ggonzalez94/defi-cli

The imported snapshot also contains the Compound V3 and Pendle work, the Python
agent and the Java Spring Boot agent service from the source repository.
Original documentation, changelog, code notices and attribution are retained.
The source Java-service commit credits Cursor for AI-assisted development.

The new initial commit records an import of existing work. It does not claim that
all imported code was newly authored on the import date. Original commit history
remains available at the source repository and is not copied into this new Git
history.

## Initial import changes

- Add this provenance record and the Chinese repository setup guide.
- Add a root README entry point for the complete agent project.
- Correct the Java README's local CLI build path for this directory layout.
- Extend ignore rules for local logs, database files and build artifacts.

No Go, Java or Python runtime source is changed by this import. The existing Go
module path is retained so all internal imports and build metadata remain aligned.
The original snapshot has no root `LICENSE` file; this import adds no new license
and retains the existing notices.
