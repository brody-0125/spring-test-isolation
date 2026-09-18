# Contributing

Thank you for contributing to Spring Test Isolation. This document defines how we name branches and write commits. It follows [Conventional Commits v1.0.0](https://www.conventionalcommits.org/ko/v1.0.0/) and overrides generic agent defaults for this repository.

Release versioning and tagging are described in [RELEASING.md](RELEASING.md). User-facing release notes belong in [CHANGELOG.md](CHANGELOG.md).

## Before you open a PR

1. Read [CONTRACT.md](CONTRACT.md) and [VERIFICATION.md](VERIFICATION.md) for runtime guarantees and evidence requirements.
2. Run the checks that match your change (at minimum `./gradlew :runtime:test :verification:test` when Docker is available).
3. For behavior or isolation changes, run the relevant scripts in [README.md](README.md) (`scripts/verify.ps1`, `scripts/verify-failures.ps1`, etc.) and keep claims within what those runs prove.

## Branch names

Use `{type}/{short-kebab-description}`:

| Part | Guidance |
|------|----------|
| `type` | Same set as commit types below (`feat`, `fix`, `docs`, `test`, `chore`, `ci`, `build`, `refactor`, `perf`, `revert`) |
| `short-kebab-description` | Lowercase, hyphen-separated, specific to the change |

Examples: `fix/worker-close-suppressed-errors`, `feat/nested-test-guard`, `chore/verify-config-cache`.

## Commit messages

```
<type>[optional scope][optional !]: <description>

[optional body]

[optional footer(s)]
```

- **description**: Imperative mood, lowercase start, no trailing period.
- **scope**: Use `runtime`, `plugin`, `verification`, or `docs` when it helps readers.
- **breaking changes**: Add `!` after type/scope and a `BREAKING CHANGE:` footer.

### Types

| Type | Use for |
|------|---------|
| `feat` | New behavior or capability |
| `fix` | Bug fixes and incorrect isolation/cleanup behavior |
| `docs` | Documentation only |
| `test` | Tests and fixtures only |
| `chore` | Maintenance, tooling, non-product code |
| `ci` | CI or verification scripts |
| `build` | Gradle, dependencies, publication |
| `refactor` | Code structure without behavior change |
| `perf` | Performance improvements |
| `revert` | Reverting a prior commit |

### Examples

```
fix(runtime): keep database drop primary when redis cleanup fails
```

```
feat(verification): add flyway smoke test on worker database

Uses baselineOnMigrate because fixtures already create user tables.
```

```
docs: document unsupported @Nested and @ContextHierarchy
```

## Pull requests

- Keep PRs focused; split unrelated changes.
- Summarize **why** in the PR description and point to verification evidence (commands run, log paths under `build/evidence/` when applicable).
- Do not claim compatibility beyond what [VERIFICATION.md](VERIFICATION.md) supports.

## Agent and editor conventions

Contributors using Cursor should follow this file when the agent creates branches or commits. The repository also includes [.cursor/rules/conventional-git.mdc](.cursor/rules/conventional-git.mdc) for project-scoped agent guidance.
