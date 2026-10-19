# MoveAPiece Help

MoveAPiece starts straight into a game. Everything you can choose is in
the New Game dialog, and the app remembers it for next time.

## Playing

- **New Game** (the first button) picks the opponent: Stockfish, Maia, a
  second person at the same screen, or the Opening Trainer. Choose your
  colour and the strength there.
- **Stockfish** plays at an Elo strength from 1320 to 3190. **Maia**
  plays like a human player of the chosen rating, from 800 to 2400,
  mistakes included. On the desktop the strength can also be changed with
  the slider next to the board during a game.
- To move, tap or click a piece and then its target square. A pawn that
  reaches the last rank asks which piece it becomes.
- **Undo** and **Redo** step through the moves; against an engine, Undo
  takes back your move and its reply together. **Flip Board** turns the
  board around.
- Tap or click a move in the move history to go back to that position;
  play on from there or return with Redo.

## Help during a game

- **Hint** marks the best move on the board and lists other good moves
  under the buttons.
- After each of your moves against an engine, a weak move is named as
  inaccuracy, mistake or blunder, with the loss in pawns.
- **Show evaluation** shows the engine's assessment of the position in
  pawns, from White's point of view: plus is good for White. It is off by
  default.
- **Analyze Game** in the menu (the three dots) checks a game move by
  move and lists the mistakes of both sides.

## Openings

- The **Opening Trainer** in New Game drills one of 20 well-known
  openings. The app plays the other side and accepts only the right
  reply; with "Show move hint" it marks that reply. At the end you can
  repeat the line, pick another one or play on freely.
- The **Opening Library** in the menu shows each opening move by move,
  without a game.

## Chess960

- With **Chess960** in New Game, every game starts from one of the 960
  starting positions, picked at random: the back-rank pieces are
  shuffled, the same for both sides, and the pawns stand as usual. It
  works against Stockfish, Maia or a second person; the app shows the
  number of the position.
- To castle, move the king onto its own rook. King and rook then stand as
  in standard chess: on g1 and f1 or on c1 and d1. If no ordinary king
  move goes to g1 or c1, you can also move the king straight there.
- On an electronic board, set up the pieces as shown on the screen; until
  they match, the squares that differ light up. Castle the way the FIDE
  rules recommend: lift the king, move the rook, put the king down. If
  king and rook merely swap squares, the Pegasus cannot tell the
  difference; make that castling move on the screen.

## Saving and loading games

- **Export PGN** in the menu passes the current game on in PGN, the
  standard format other chess programs read: on Android to an app of your
  choice, on the desktop as a file.
- **Import PGN** opens a PGN file. If it holds several games, choose one.
  The game is loaded at its last move; Undo steps back through it, and
  you can play on from any point.

## Electronic boards

MoveAPiece works with the DGT Pegasus and the Chessnut Air, Air+, Pro and
Go over Bluetooth.

- Switch the board on and press the board button (next to Hint). Pick
  your board from the list; the app recognises the type by itself. On
  Android, allow Bluetooth access when asked.
- Moves made on the board appear on the screen, and the opponent's reply
  lights up on the board: make it there. You can still move on the screen
  at the same time.
- If the pieces do not match the game, a note names the squares and the
  LEDs show where. The app's move waits until the board is right.
- **Pegasus**: the board only senses that a square is occupied, not by
  which piece. Promotions are chosen on the screen. If a capture is
  unclear, briefly lift the piece on the target square.
- **Chessnut**: the board recognises every piece. A position set up on
  the board can be taken over: tap or click the note that appears. The
  NEW GAME button on the board starts a new game; during a game, press it
  twice.
- Press the board button again to disconnect.

## Good to know

- MoveAPiece works fully offline and collects no data; the privacy
  policy is in the menu.
- The app is free software under the GPL. Source code, releases and the
  issue tracker for questions and bug reports are at
  github.com/egdels/moveapiece.
