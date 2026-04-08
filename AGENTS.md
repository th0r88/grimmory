# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Grimmory is a self-hosted book library manager (Angular 21 + Spring Boot 4 / Java 25 + MariaDB). Community fork of Booklore.

## Agent Workflow

- Start from the root `Justfile` unless you have a clear reason to drop into a subproject.
- Prefer `just api ...` and `just ui ...` over ad hoc commands.
- Keep changes scoped to the relevant project instead of mixing backend, frontend, deployment, and release edits in one pass.
- Target `develop`, not `main`.
- First prove the change with targeted tests around the edited surface, then run the wider suite for that surface before handing off.
- If you cannot run the wider suite, say exactly what you ran, what you skipped, and why.

## Tech Stack

- **Backend:** Java 25 (virtual threads enabled), Spring Boot 4.0, Gradle, MariaDB, Flyway, Hibernate 7, Lombok, MapStruct, JJWT
- **Frontend:** Angular 21, TypeScript 5.9, PrimeNG 21, TanStack Query, Transloco (i18n), Vitest (unit), Playwright (e2e), Yarn 4 via corepack
- **Infra:** Docker Compose for dev stack, port 6060

## Project Structure

- **Backend (`booklore-api/`)**
  - Application code: `src/main/java/org/booklore/`
  - Key packages: `app/{controller,service,dto,mapper,specification}`, `config/security/`, `repository/`, `util/`
  - Resources and Flyway migrations: `src/main/resources` (migrations in `db/migration/`)
  - Tests: `src/test/java`
- **Frontend (`frontend/`)**
  - Application code: `src/app/{core,features,shared}`
  - Feature modules: `features/{author-browser,book,bookdrop,dashboard,library-creator,magic-shelf,metadata,notebook,readers,series-browser,settings,stats}`
  - Translations: `src/i18n/`
  - Environment config: `src/environments/` (API base URL `http://localhost:6060`, WebSocket `ws://localhost:6060/ws`)

## Architecture

The backend is a monolithic Spring Boot app serving both the REST API and the Angular SPA (bundled into the jar). In development, the Angular dev server runs separately and talks to the backend at `localhost:6060`.

- **Controllers** expose REST endpoints under `/api/v1/`. WebSocket broker at `/ws`.
- **Services** contain business logic; **repositories** are Spring Data JPA interfaces.
- **DTOs** are mapped to/from JPA entities via MapStruct mappers (in `app/mapper/`).
- **Security** supports local auth (JWT via JJWT) and OIDC (`config/security/oidc/`).
- **File handling** supports LOCAL and NETWORK disk types. Book files live under `/books`, imports via `/bookdrop`.
- **Frontend** uses standalone Angular components, `inject()` for DI, TanStack Query for server state, PrimeNG for UI, and Transloco for i18n.

## Ownership Boundaries

- `deploy/`, `packaging/`, `tools/`, `docs/`, and `assets/` are support surfaces. Do not change them unless the task actually touches deployment, packaging, release automation, or shared docs/assets.
- Keep backend, frontend, deployment, and release work separated unless the task genuinely crosses those boundaries.
- When a change spans multiple surfaces, validate each one explicitly.

## Command Surface

```bash
# Full repo
just check                          # backend + frontend verification
just test                           # backend + frontend tests
just dev-up                         # start full Docker dev stack (foreground)
just db-up                          # start only the dev database
just db-down                        # stop the dev database

# Backend
just api run                        # start Spring Boot with dev profile
just api test                       # run all backend tests
just api test-class MyTestClass     # run a single test class by name
just api check                      # full Gradle check
just api coverage                   # JaCoCo coverage report
just api build                      # build jar

# Frontend
just ui dev                         # start Angular dev server
just ui test                        # run Vitest tests
just ui lint                        # ESLint
just ui typecheck                   # TypeScript type checking
just ui check                       # full verification (deps, typecheck, lint, stylelint, build, test)
just ui e2e                         # Playwright e2e suite
just ui e2e-file <spec>             # run single Playwright spec file
just ui build                       # production build
just ui install                     # install dependencies
```

## Backend Rules

- Use 4-space indentation and match surrounding Java style.
- Prefer constructor injection via Lombok patterns already used in the codebase. Do not introduce `@Autowired` field injection.
- Use MapStruct for entity/DTO mapping.
- Keep JPA entities on the `*Entity` suffix.
- Add Flyway migrations as new files named `V<number>__<Description>.sql`.
- Do not edit released migrations in place.
- Prefer focused unit tests; use `@SpringBootTest` only when the Spring context is required.

## Frontend Rules

- Use 2-space indentation in TypeScript, HTML, and SCSS.
- Keep Angular code on standalone components. Do not add NgModules.
- Prefer `inject()` over constructor injection.
- Follow `frontend/eslint.config.js`: component selectors use `app-*`, directive selectors use `app*`, and `any` is disallowed.
- Put user-facing strings in Transloco files under `frontend/src/i18n/`.
- Keep responsive behavior intact.
- Use Vitest for tests.

## Validation

- Use staged verification: prove the behavior locally with targeted tests first, then run the wider suite for that surface.
- Typical backend path: `just api test` and then `just api check`.
- Typical frontend path: targeted Vitest coverage for the changed area and then `just ui check`.
- If the change crosses frontend and backend boundaries, finish with a repo-level pass such as `just test` or `just check`.
- Do not claim completion from a narrow test when a broader runnable suite exists.

## PR Expectations

- If UI behavior changes, capture screenshots or a short recording for the PR.
- PRs in this repo are expected to link an approved issue and include local test output.
- Follow Conventional Commits with scopes, for example `feat(devex): ...` or `fix(entrypoint): ...`.
