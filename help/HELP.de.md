# MoveAPiece Hilfe

MoveAPiece startet direkt in eine Partie. Alles, was sich einstellen
lässt, steht im Dialog Neues Spiel, und die App merkt es sich für das
nächste Mal.

## Spielen

- **Neues Spiel** (der erste Knopf) wählt den Gegner: Stockfish, Maia,
  eine zweite Person am selben Bildschirm oder das Eröffnungstraining.
  Dort wählst du auch Farbe und Spielstärke.
- **Stockfish** spielt mit einer Elo-Stärke von 1320 bis 3190. **Maia**
  spielt wie ein menschlicher Spieler mit der gewählten Wertung, von 800
  bis 2400, Fehler inklusive. Auf dem Desktop lässt sich die Stärke
  während der Partie auch mit dem Schieberegler neben dem Brett ändern.
- Zum Ziehen tippst oder klickst du eine Figur an und dann ihr Zielfeld.
  Erreicht ein Bauer die letzte Reihe, fragt die App, welche Figur er
  wird.
- **Zug zurücknehmen** und **Zug wiederholen** gehen durch die Züge;
  gegen eine Engine nimmt Zurücknehmen deinen Zug und die Antwort
  zusammen zurück. **Brett drehen** dreht das Brett um.
- Ein Tipp oder Klick auf einen Zug im Zugverlauf springt zu dieser
  Stellung; von dort spielst du weiter oder kehrst mit Zug wiederholen
  zurück.

## Hilfe während der Partie

- **Tipp** markiert den besten Zug auf dem Brett und nennt unter den
  Knöpfen weitere gute Züge.
- Nach jedem deiner Züge gegen eine Engine wird ein schwacher Zug als
  Ungenauigkeit, Fehler oder Patzer benannt, mit dem Verlust in Bauern.
- **Bewertung anzeigen** zeigt die Einschätzung der Stellung durch die
  Engine in Bauern, aus Sicht von Weiß: Plus ist gut für Weiß.
  Standardmäßig ist sie aus.
- **Partie analysieren** im Menü (die drei Punkte) prüft eine Partie Zug
  für Zug und listet die Fehler beider Seiten auf.

## Eröffnungen

- Das **Eröffnungstraining** unter Neues Spiel übt eine von 20 bekannten
  Eröffnungen. Die App spielt die andere Seite und nimmt nur die richtige
  Antwort an; mit „Zugvorschlag anzeigen“ markiert sie diese Antwort. Am
  Ende kannst du die Linie wiederholen, eine andere wählen oder frei
  weiterspielen.
- Die **Eröffnungsbibliothek** im Menü zeigt jede Eröffnung Zug für Zug,
  ohne Partie.

## Partien speichern und laden

- **PGN exportieren** im Menü gibt die aktuelle Partie im PGN-Format
  weiter, das andere Schachprogramme lesen: unter Android an eine App
  deiner Wahl, auf dem Desktop als Datei.
- **PGN importieren** öffnet eine PGN-Datei. Enthält sie mehrere
  Partien, wählst du eine aus. Die Partie wird mit ihrem letzten Zug
  geladen; Zug zurücknehmen geht durch sie zurück, und du kannst an jeder
  Stelle weiterspielen.

## Elektronische Bretter

MoveAPiece funktioniert mit dem DGT Pegasus und dem Chessnut Air, Air+,
Pro und Go über Bluetooth.

- Schalte das Brett ein und drücke den Brett-Knopf (neben Tipp). Wähle
  dein Brett aus der Liste; den Typ erkennt die App selbst. Unter Android
  erlaube den Bluetooth-Zugriff, wenn danach gefragt wird.
- Züge auf dem Brett erscheinen auf dem Bildschirm, und die Antwort des
  Gegners leuchtet auf dem Brett auf: Führe sie dort aus. Auf dem
  Bildschirm kannst du gleichzeitig weiter ziehen.
- Passen die Figuren nicht zur Partie, nennt ein Hinweis die Felder, und
  die LEDs zeigen sie an. Der Zug der App wartet, bis das Brett stimmt.
- **Pegasus**: Das Brett erkennt nur, dass ein Feld besetzt ist, nicht
  mit welcher Figur. Umwandlungen wählst du auf dem Bildschirm. Ist ein
  Schlagzug unklar, hebe die Figur auf dem Zielfeld kurz an.
- **Chessnut**: Das Brett erkennt jede Figur. Eine auf dem Brett
  aufgebaute Stellung lässt sich übernehmen: Tippe oder klicke auf den
  Hinweis, der dann erscheint. Der NEW-GAME-Knopf am Brett startet ein
  neues Spiel; während einer Partie drückst du ihn zweimal.
- Ein erneuter Druck auf den Brett-Knopf trennt die Verbindung.

## Gut zu wissen

- MoveAPiece arbeitet komplett offline und sammelt keine Daten; die
  Datenschutzerklärung steht im Menü.
- Die App ist freie Software unter der GPL. Quellcode, Releases und der
  Issue-Tracker für Fragen und Fehlermeldungen sind unter
  github.com/egdels/moveapiece.
