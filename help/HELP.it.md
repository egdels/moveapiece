# Guida di MoveAPiece

MoveAPiece parte subito con una partita. Tutto ciò che si può scegliere
si trova nella finestra Nuova partita, e l'app lo ricorda per la volta
successiva.

## Giocare

- **Nuova partita** (il primo pulsante) sceglie l'avversario: Stockfish,
  Maia, una seconda persona allo stesso schermo o l'Allenamento aperture.
  Lì scegli anche il colore e la forza.
- **Stockfish** gioca con una forza Elo da 1320 a 3190. **Maia** gioca
  come un giocatore umano del rating scelto, da 800 a 2400, errori
  compresi. Sul desktop la forza si può cambiare anche durante la partita
  con il cursore accanto alla scacchiera.
- Per muovere, tocca o fai clic su un pezzo e poi sulla casa di arrivo.
  Un pedone che raggiunge l'ultima traversa chiede in quale pezzo
  promuovere.
- **Annulla mossa** e **Ripeti mossa** scorrono le mosse; contro un
  motore, Annulla ritira la tua mossa e la sua risposta insieme. **Ruota
  scacchiera** capovolge la scacchiera.
- Tocca o fai clic su una mossa nella cronologia delle mosse per tornare
  a quella posizione; gioca da lì o torna avanti con Ripeti mossa.

## Aiuto durante la partita

- **Suggerimento** evidenzia la mossa migliore sulla scacchiera e
  elenca altre buone mosse sotto i pulsanti.
- Dopo ogni tua mossa contro un motore, una mossa debole viene indicata
  come imprecisione, errore o svista, con la perdita in pedoni.
- **Mostra valutazione** mostra il giudizio del motore sulla posizione,
  in pedoni, dal punto di vista del Bianco: un più è buono per il Bianco.
  È disattivata per impostazione predefinita.
- **Analizza partita** nel menu (i tre puntini) esamina una partita mossa
  per mossa ed elenca gli errori di entrambe le parti.

## Aperture

- L'**Allenamento aperture** in Nuova partita esercita una di 20
  aperture note. L'app gioca l'altra parte e accetta solo la risposta
  giusta; con «Mostra suggerimento mossa» evidenzia quella risposta. Alla
  fine puoi ripetere la linea, sceglierne un'altra o continuare
  liberamente.
- La **Libreria delle aperture** nel menu mostra ogni apertura mossa per
  mossa, senza partita.

## Chess960

- Con **Chess960** in Nuova partita, ogni partita inizia da una delle
  960 posizioni iniziali, scelta a caso: i pezzi della prima traversa
  sono mescolati, uguali per entrambi i colori, e i pedoni stanno come
  al solito. Funziona contro Stockfish, Maia o un'altra persona; l'app
  mostra il numero della posizione.
- Per arroccare, muovi il re sulla propria torre. Re e torre si trovano
  poi come negli scacchi normali: su g1 e f1 oppure su c1 e d1. Se nessuna
  mossa normale del re porta su g1 o c1, puoi anche muovere il re
  direttamente lì.
- Su una scacchiera elettronica, disponi i pezzi come sullo schermo;
  finché non corrispondono, le case diverse si illuminano. Arrocca come
  raccomandano le regole FIDE: solleva il re, muovi la torre, posa il
  re. Se re e torre si scambiano soltanto le case, la Pegasus non nota la
  differenza; esegui quell'arrocco sullo schermo.

## Salvare e caricare partite

- **Esporta PGN** nel menu passa la partita in corso in formato PGN,
  quello letto dagli altri programmi di scacchi: su Android a un'app a tua
  scelta, sul desktop come file.
- **Importa PGN** apre un file PGN. Se contiene più partite, scegline
  una. La partita viene caricata all'ultima mossa; Annulla mossa la
  ripercorre all'indietro, e puoi continuare a giocare da qualsiasi
  punto.

## Scacchiere elettroniche

MoveAPiece funziona con la DGT Pegasus e le Chessnut Air, Air+, Pro e Go
tramite Bluetooth.

- Accendi la scacchiera e premi il pulsante della scacchiera (accanto a
  Suggerimento). Scegli la tua scacchiera dall'elenco; l'app ne riconosce
  il tipo da sola. Su Android, consenti l'accesso Bluetooth quando
  richiesto.
- Le mosse fatte sulla scacchiera compaiono sullo schermo, e la risposta
  dell'avversario si illumina sulla scacchiera: eseguila lì. Puoi
  continuare a muovere anche sullo schermo.
- Se i pezzi non corrispondono alla partita, una nota indica le case e i
  LED le mostrano. La mossa dell'app aspetta finché la scacchiera è a
  posto.
- **Pegasus**: la scacchiera rileva solo che una casa è occupata, non da
  quale pezzo. Le promozioni si scelgono sullo schermo. Se una cattura non
  è chiara, solleva brevemente il pezzo sulla casa di arrivo.
- **Chessnut**: la scacchiera riconosce ogni pezzo. Una posizione
  disposta sulla scacchiera può essere ripresa: tocca o fai clic sulla
  nota che compare. Il pulsante NEW GAME della scacchiera avvia una nuova
  partita; durante una partita, premilo due volte.
- Premi di nuovo il pulsante della scacchiera per scollegarla.

## Buono a sapersi

- MoveAPiece funziona completamente offline e non raccoglie dati;
  l'informativa sulla privacy è nel menu.
- L'app è software libero sotto licenza GPL. Codice sorgente, versioni e
  il tracker delle segnalazioni per domande e bug sono su
  github.com/egdels/moveapiece.
