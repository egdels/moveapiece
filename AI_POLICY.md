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

Every commit the agent contributes to carries a `Co-Authored-By: Claude ...`
trailer (the exact model name after "Claude" varies with the model used in
that session), so the exact scope is always checkable directly in the
history:

```sh
git log --oneline | wc -l                                   # total commits
git log --format="%b" | grep -c "Co-Authored-By: Claude"    # AI-assisted commits
```

## What the AI does

- Implements features, refactors, and bug fixes the developer specifies —
  across the shared `core` module, the Android app, and the JavaFX desktop
  app, including the small native BLE bridges (Objective-C on macOS,
  C++/WinRT on Windows) and the `jpackage` packaging
- Writes and runs automated tests (JVM unit tests in every module, Android
  instrumented tests, and the desktop golden tests that check the Maia
  integration against lc0's own native output)
- Sets up and maintains build/CI tooling (Gradle, the three GitHub Actions
  workflows in `.github/workflows/`, formatting and lint checks)
- Drafts documentation, release notes, and store metadata
- For the physical board integrations (DGT Pegasus, Chessnut Air), drives real-hardware test
  sessions (over `adb` on Android; directly on the macOS and Windows desktop
  builds) — installing builds, capturing screenshots, reading logs — but
  cannot itself hold or move pieces on a physical board, so those sessions
  are run together with the developer

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
- Publication: pushing to GitHub, tagging versions (which triggers the
  release workflow), and any submission to F-Droid happen only on the
  developer's explicit instruction, never on the agent's own initiative

## What this does *not* mean

This document is about how the software was **built**, not what it
**does**. MoveAPiece makes no network calls at runtime — not to any AI
service, not to anything else (see the [README](README.md)).

The app does bundle two kinds of chess neural networks, both run entirely
on-device: the optional [Maia](https://github.com/CSSLab/maia-chess)
opponent, nine pre-trained networks (GPLv3, shipped as `.onnx` files) that
predict human-like moves via a single ONNX Runtime forward pass per move,
with ONNX Runtime's built-in telemetry disabled on both Android and desktop
(see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)); and Stockfish's own
NNUE evaluation networks, which are part of any modern Stockfish build.
Both are chess-position models, not language models, and have nothing to do
with the AI tooling used during development. Everything else the app does —
the opening trainer, PGN handling, the physical-board link — is conventional
code.

## Verification

AI-authored changes go through the same scrutiny any change would:
automated tests, [Spotless](https://github.com/diffplug/spotless) formatting
checks, and Android Lint, all enforced in CI. `.github/workflows/build.yml`
runs the Android unit and instrumented tests plus a two-runner
reproducible-build comparison; `desktop.yml` runs the shared `core` tests
and the desktop tests (including the Maia golden tests against lc0's
output) on every supported OS/architecture before packaging. The physical
board integrations (DGT Pegasus, Chessnut Air) are additionally verified in
real hardware sessions with the developer.
