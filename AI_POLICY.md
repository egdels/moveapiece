# AI-Assisted Development

This document exists for transparency: it explains how AI tooling was used to
build MoveAPiece, so that reviewers (F-Droid, GitHub visitors, contributors)
don't have to piece it together from commit trailers.

## Summary

MoveAPiece is developed primarily with [Claude Code](https://claude.com/claude-code),
an AI coding agent by Anthropic, directed and reviewed throughout by a single
human developer (Christian Kierdorf, [@egdels](https://github.com/egdels)).
This is substantive involvement, not autocomplete-level assistance: the agent
designs and writes most of the implementation, tests, build/CI tooling, and
documentation (including this file), from requirements and direction the
developer provides throughout each session.

Every commit the agent contributes to carries a `Co-Authored-By: Claude`
trailer, so the exact scope is always checkable directly in the history:

```sh
git log --oneline | wc -l                                   # total commits
git log --format="%b" | grep -c "Co-Authored-By: Claude"    # AI-assisted commits
```

## What the AI does

- Implements features, refactors, and bug fixes the developer specifies
- Writes and runs automated tests (JVM unit tests, Android instrumented tests)
- Sets up and maintains build/CI tooling (Gradle, GitHub Actions, formatting
  and lint checks)
- Drafts documentation
- For the physical DGT Pegasus board integration, drives real-hardware test
  sessions over `adb` (installing builds, capturing screenshots, reading
  logs) — but cannot itself hold or move pieces on a physical board, so
  those sessions are run together with the developer

## What stays human

- All product and architecture decisions: what to build, what to publish,
  and when
- Final review of every change before it is committed — nothing lands
  without the developer's explicit go-ahead in the session
- Anything risky or hard to reverse (force-pushes, publishing, deleting
  work) requires explicit, per-action confirmation
- Copyright and licensing: the developer is the sole copyright holder (see
  the `SPDX-License-Identifier` headers in source files); AI assistance
  does not change authorship
- Publication timing — e.g. this repository has intentionally not been
  pushed to GitHub yet, pending a separate human-only review

## What this does *not* mean

MoveAPiece itself contains no AI code and makes no network calls to any AI
service at runtime, or at all — see the [README](README.md). This document
is about how the software was **built**, not what it **does**: the app
plays chess against a locally run Stockfish engine and nothing else.

## Verification

AI-authored changes go through the same scrutiny any change would:
automated tests, [Spotless](https://github.com/diffplug/spotless) formatting
checks, and Android Lint, all enforced in CI (`.github/workflows/build.yml`);
the physical Pegasus board integration is additionally verified in real
hardware sessions with the developer.
