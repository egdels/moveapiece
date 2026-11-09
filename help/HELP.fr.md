# Aide de MoveAPiece

MoveAPiece démarre directement une partie. Tout ce qui peut se régler se
trouve dans la boîte de dialogue Nouvelle partie, et l'application s'en
souvient pour la prochaine fois.

## Jouer

- **Nouvelle partie** (le premier bouton) choisit l'adversaire :
  Stockfish, Maia, une deuxième personne devant le même écran,
  l'Entraînement d'ouvertures ou les Problèmes. Vous y choisissez aussi
  la couleur et la force.
- **Stockfish** joue avec une force Elo de 1320 à 3190. **Maia** joue
  comme un joueur humain du classement choisi, de 800 à 2400, erreurs
  comprises. Sur ordinateur, la force peut aussi être modifiée en cours
  de partie avec le curseur à côté de l'échiquier.
- Pour jouer, touchez ou cliquez une pièce, puis sa case d'arrivée. Un
  pion qui atteint la dernière rangée demande en quelle pièce il se
  transforme.
- **Annuler le coup** et **Rétablir le coup** parcourent les coups ;
  contre un moteur, Annuler reprend votre coup et sa réponse ensemble.
  **Retourner l'échiquier** inverse l'échiquier.
- Touchez ou cliquez un coup dans l'historique des coups pour revenir à
  cette position ; jouez à partir de là ou revenez avec Rétablir le coup.

## Aide pendant la partie

- **Indice** marque le meilleur coup sur l'échiquier et cite d'autres
  bons coups sous les boutons.
- Après chacun de vos coups contre un moteur, un coup faible est signalé
  comme imprécision, erreur ou gaffe, avec la perte en pions.
- **Afficher l'évaluation** montre l'appréciation de la position par le
  moteur, en pions, du point de vue des Blancs : un plus est bon pour les
  Blancs. Elle est désactivée par défaut.
- **Analyser la partie** dans le menu (les trois points) examine une
  partie coup par coup et liste les erreurs des deux camps.

## Ouvertures

- L'**Entraînement d'ouvertures** dans Nouvelle partie fait travailler
  l'une de 20 ouvertures connues. L'application joue l'autre camp et
  n'accepte que la bonne réponse ; avec « Afficher une suggestion de
  coup », elle marque cette réponse. À la fin, vous pouvez recommencer la
  ligne, en choisir une autre ou continuer librement.
- La **Bibliothèque d'ouvertures** dans le menu montre chaque ouverture
  coup par coup, sans partie.

## Problèmes

- **Problèmes** dans Nouvelle partie propose des problèmes tactiques :
  des positions de vraies parties dans lesquelles un coup ou une courte
  suite gagne du matériel ou fait mat. Le problème décide de la couleur
  que vous jouez.
- L'adversaire joue en premier ; son coup est marqué sur l'échiquier.
  Trouvez ensuite le meilleur coup pour votre camp. Si la solution
  compte plusieurs coups, l'application répond à chaque bon coup jusqu'à
  ce que le problème soit résolu. Tout coup qui fait mat le résout, même
  si la solution enregistrée mate autrement.
- Un mauvais coup est montré brièvement puis repris ; réessayez.
- **Indice** marque la pièce qui doit jouer. **Voir la solution** joue
  le reste de la solution coup par coup.
- Votre **classement de problèmes** commence à 1200 et s'affiche à côté
  du classement du problème. Résoudre un problème sans mauvais coup,
  indice ni solution le fait monter ; sinon il baisse, et les problèmes
  plus difficiles comptent davantage. Les premiers problèmes comptent le
  plus, si bien que le classement trouve en général votre niveau en
  quelques problèmes. Le problème suivant est choisi près de votre
  classement ; les problèmes joués récemment ne reviennent pas de sitôt.
  Quitter un problème non résolu, pour une nouvelle partie ou un autre
  problème, compte comme non résolu.
- Si les problèmes restent trop faciles ou trop difficiles, rendez-les
  **Plus facile** ou **Plus difficile** que votre classement dans
  Nouvelle partie, d'environ 300 points ; **Normal** est la valeur par
  défaut. Votre classement compte de la même façon : un problème plus
  difficile résolu rapporte simplement davantage.
- Après une résolution sans faute, le problème suivant arrive tout seul.
  Sinon, la position reste affichée jusqu'à ce que vous appuyiez sur
  **Problème suivant**.
- Annuler le coup, l'évaluation et les indices du moteur sont désactivés
  pendant les problèmes. Les échiquiers électroniques ne sont pas encore
  utilisés pour les problèmes ; jouez-les à l'écran. Le bouton NEW GAME
  d'un Chessnut lance le problème suivant.
- Quand l'application est fermée, un problème en cours est conservé ;
  après un problème terminé, le prochain démarrage continue avec un
  nouveau problème. Les quelque 4 600 problèmes proviennent de la base
  de problèmes libre de Lichess.

## Chess960

- Avec **Chess960** dans Nouvelle partie, chaque partie commence à
  partir de l'une des 960 positions de départ, tirée au hasard : les
  pièces de la première rangée sont mélangées, de la même façon pour les
  deux camps, et les pions sont à leur place habituelle. Cela fonctionne
  contre Stockfish, Maia ou une deuxième personne ; l'application
  affiche le numéro de la position.
- Maia n'a appris que sur des parties d'échecs classiques. En Chess960,
  Maia joue nettement plus faiblement que le classement choisi ; un
  classement plus élevé reste toutefois plus fort qu'un classement plus
  bas.
- Pour roquer, placez le roi sur sa propre tour. Roi et tour se
  retrouvent alors comme aux échecs classiques : en g1 et f1 ou en c1 et
  d1. Si aucun coup ordinaire du roi ne mène en g1 ou c1, vous pouvez
  aussi y placer le roi directement.
  Quand vous sélectionnez le roi, la tour porte elle
  aussi un point vert : il indique le roque, pas la prise de votre
  propre pièce.
- Sur un échiquier électronique, installez les pièces comme à l'écran ;
  tant qu'elles ne correspondent pas, les cases différentes s'allument.
  Roquez comme le recommandent les règles de la FIDE : soulevez le roi,
  déplacez la tour, posez le roi. Si roi et tour ne font qu'échanger
  leurs cases, le Pegasus ne voit pas la différence ; jouez ce roque à
  l'écran.

## Enregistrer et charger des parties

- **Exporter en PGN** dans le menu transmet la partie en cours au format
  PGN, que lisent les autres programmes d'échecs : sous Android à
  l'application de votre choix, sur ordinateur sous forme de fichier.
- **Importer un PGN** ouvre un fichier PGN. S'il contient plusieurs
  parties, choisissez-en une. La partie est chargée à son dernier coup ;
  Annuler le coup la parcourt à rebours, et vous pouvez reprendre le jeu
  à tout moment.

## Échiquiers électroniques

MoveAPiece fonctionne avec le DGT Pegasus et les Chessnut Air, Air+, Pro
et Go via Bluetooth.

- Allumez l'échiquier et appuyez sur le bouton de l'échiquier (à côté
  d'Indice). Choisissez votre échiquier dans la liste ; l'application en
  reconnaît le type toute seule. Sous Android, autorisez l'accès
  Bluetooth lorsqu'il est demandé.
- Les coups joués sur l'échiquier apparaissent à l'écran, et la réponse
  de l'adversaire s'allume sur l'échiquier : jouez-la là. Vous pouvez
  toujours jouer à l'écran en même temps.
- Si les pièces ne correspondent pas à la partie, une note indique les
  cases et les LED les montrent. Le coup de l'application attend que
  l'échiquier soit correct.
- **Pegasus** : l'échiquier détecte seulement qu'une case est occupée,
  pas par quelle pièce. Les promotions se choisissent à l'écran. Si une
  prise n'est pas claire, soulevez brièvement la pièce sur la case
  d'arrivée.
- **Chessnut** : l'échiquier reconnaît chaque pièce. Une position mise en
  place sur l'échiquier peut être reprise : touchez ou cliquez la note
  qui apparaît. Le bouton NEW GAME de l'échiquier lance une nouvelle
  partie ; pendant une partie, appuyez deux fois.
- Appuyez à nouveau sur le bouton de l'échiquier pour le déconnecter.

## Bon à savoir

- MoveAPiece fonctionne entièrement hors ligne et ne collecte aucune
  donnée ; la politique de confidentialité se trouve dans le menu.
- L'application est un logiciel libre sous licence GPL. Le code source,
  les versions et le suivi des tickets pour les questions et les
  rapports de bogues se trouvent sur github.com/egdels/moveapiece.
