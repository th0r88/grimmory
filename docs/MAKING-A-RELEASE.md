# Making a Release

This repository uses `semantic-release` for stable releases.

Stable releases are not created by manually dispatching a "release" workflow, and there is no promotion step to another branch. `develop` is the trunk and the release branch: a stable release is triggered by pushing release-worthy conventional commits to `develop` and letting CI go green.

## Overview

The stable release flow has three stages:

1. Push or merge release-worthy commits to `develop`.
2. `CI - Validate` runs the migration check, the test suite, and the packaging smoke test.
3. On a successful validation run, GitHub Actions computes the version, creates the tag, and publishes the images.

The relevant workflows are:

- [`.github/workflows/ci-validate.yml`](../.github/workflows/ci-validate.yml)
- [`.github/workflows/release-develop.yml`](../.github/workflows/release-develop.yml)
- [`.github/workflows/publish-release.yml`](../.github/workflows/publish-release.yml)
- [`.github/workflows/release-preview.yml`](../.github/workflows/release-preview.yml)

`release-develop.yml` is chained to `CI - Validate` with a `workflow_run` trigger rather than running on push directly. That keeps the migration check and test suite running exactly once per push while still gating the release on them.

## Prerequisites

Before cutting a stable release, make sure:

- The commits that should be released are already on `develop`.
- Those commits follow conventional commit semantics.
- `RELEASE_BOT_TOKEN` is configured if branch protection requires more than the default `github.token`.
- `DOCKER_USERNAME` and `DOCKER_PASSWORD` are configured for Docker Hub publishing.

## What Triggers a Stable Release

`semantic-release` runs after every successful `CI - Validate` run on `develop` and decides whether a new release is needed.

Release behavior is based on commit history since the last stable tag:

- `feat:` triggers a minor release.
- `fix:`, `perf:`, and `refactor:` trigger a patch release.
- `BREAKING CHANGE:` triggers a major release.
- `docs:`, `ci:`, `build:`, `chore:`, `test:`, and `style:` appear in notes but do not trigger a release on their own.

The next version is always computed from the latest stable tag already reachable from `develop`.

Because `develop` is the release branch, there is no staging buffer. Anything merged to `develop` with a releasable commit type ships on the next green CI run. Use `docs:`/`chore:`/`ci:` types for work that should not cut a version.

## Recommended Maintainer Flow

### 1. Preview the next release

Run the `Release - Dry Run Preview` workflow from the Actions tab.

This workflow is defined in [`.github/workflows/release-preview.yml`](../.github/workflows/release-preview.yml) and accepts:

- `ref`
  Default: `develop`

It runs `semantic-release --dry-run` against the selected ref and summarises the commits since the last stable tag.

Use it to confirm:

- whether a release will be created,
- what the next version will be,
- how the release notes will be grouped,
- and which commits are included in the release range.

### 2. Push or merge the release-worthy change set to `develop`

Once the dry run looks correct, land the commits on `develop`.

That triggers `CI - Validate`, and a successful run triggers [`.github/workflows/release-develop.yml`](../.github/workflows/release-develop.yml).

### 3. Let semantic-release do the versioning work

If a release is warranted, `release-develop.yml` will:

- compute the next semantic version,
- update `CHANGELOG.md`,
- create a release commit with `[skip ci]`,
- create the Git tag `vX.Y.Z`,
- and create a draft GitHub release.

If no release is warranted, the workflow exits without tagging or publishing.

The release commit is pushed back to `develop` with `[skip ci]`, which both `ci-validate.yml` and `release-develop.yml` skip, so it does not loop.

### 4. Let the stable publish job release the artifacts

When `semantic-release` creates a release, `release-develop.yml` invokes [`.github/workflows/publish-release.yml`](../.github/workflows/publish-release.yml) with the resolved release tag and commit SHA.

That workflow will:

- build the multi-architecture container image (`linux/amd64`, `linux/arm64`),
- publish `jferme/grimmory:vX.Y.Z`,
- publish `jferme/grimmory:latest`,
- publish `ghcr.io/th0r88/grimmory:vX.Y.Z`,
- publish `ghcr.io/th0r88/grimmory:latest`,
- and flip the GitHub release from draft to published.

## Republishing an Existing Tag

`publish-release.yml` can also be dispatched manually with an existing `release_tag`. In that case no draft exists, so the workflow creates the GitHub release with generated notes instead of publishing a draft. Use this only to recover a failed publish — normal releases should go through `develop`.

## Preview Builds

Manual preview builds for PRs or arbitrary refs are separate from stable releases.

Use [`.github/workflows/preview-image.yml`](../.github/workflows/preview-image.yml) if you want a one-off test image without creating a stable release. It builds `linux/amd64` only and pushes to `ghcr.io/th0r88/grimmory:preview-<sha>` (or `pr-<number>-<sha>`).

## Notes

- Stable releases are driven by commit history on `develop`, not by labels or manual version bump inputs.
- If you need to understand why a release did or did not happen, start with the `Release - Dry Run Preview` workflow and then inspect the `semantic-release` output.
