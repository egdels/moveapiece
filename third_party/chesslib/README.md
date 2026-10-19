# chesslib (bundled source)

This is the source of [chesslib](https://github.com/bhlangonijr/chesslib)
by Ben-Hur Carlos Vieira Langoni Junior, Apache License 2.0 (see `LICENSE`),
built as the Gradle module `:chesslib` for `:core`, `:app` and `:desktop`.

It is upstream master at commit
[`12dac82`](https://github.com/bhlangonijr/chesslib/commit/12dac82e072696c209143f3b10a440044da9531b)
(after release 1.3.7) with two changes, offered upstream as pull requests:

- [#143](https://github.com/bhlangonijr/chesslib/pull/143): Chess960 castling
  is stored as the king moving onto its own rook, so it is no longer confused
  with a plain king move to the same square.
- [#144](https://github.com/bhlangonijr/chesslib/pull/144): restores
  commons-lang3 3.18.0.

`moveapiece-changes.diff` is the complete difference to upstream `12dac82`.
Only `src/main` is copied; chesslib's own tests live upstream.

The source is bundled instead of fetched as a binary so that every build,
F-Droid's included, compiles it from source. Once an upstream release contains
both changes, this directory goes and `:core`, `:app` and `:desktop` depend on
`com.github.bhlangonijr:chesslib:<release>` again.

Leave the code here as it is: changes belong upstream. Spotless and Error
Prone skip this module.
