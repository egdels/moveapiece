# Ayuda de MoveAPiece

MoveAPiece empieza directamente con una partida. Todo lo que se puede
elegir está en el diálogo Partida nueva, y la aplicación lo recuerda para
la próxima vez.

## Jugar

- **Partida nueva** (el primer botón) elige el rival: Stockfish, Maia,
  una segunda persona en la misma pantalla, el Entrenamiento de
  aperturas o los Ejercicios. Ahí eliges también el color y la fuerza.
- **Stockfish** juega con una fuerza Elo de 1320 a 3190. **Maia** juega
  como un jugador humano del rating elegido, de 800 a 2400, errores
  incluidos. En el escritorio la fuerza también se puede cambiar durante
  la partida con el control deslizante junto al tablero.
- Para mover, toca o haz clic en una pieza y luego en su casilla de
  destino. Un peón que llega a la última fila pregunta en qué pieza se
  convierte.
- **Deshacer jugada** y **Rehacer jugada** recorren las jugadas; contra
  un motor, Deshacer retira tu jugada y su respuesta a la vez. **Girar
  tablero** da la vuelta al tablero.
- Toca o haz clic en una jugada del historial para volver a esa
  posición; sigue jugando desde ahí o vuelve con Rehacer jugada.

## Ayuda durante la partida

- **Pista** marca la mejor jugada en el tablero y muestra otras buenas
  jugadas debajo de los botones.
- Después de cada una de tus jugadas contra un motor, una jugada débil se
  señala como imprecisión, error o error garrafal, con la pérdida en
  peones.
- **Mostrar evaluación** muestra la valoración de la posición por el
  motor, en peones, desde el punto de vista de las blancas: un más es
  bueno para las blancas. Está desactivada por defecto.
- **Analizar partida** en el menú (los tres puntos) revisa una partida
  jugada a jugada y enumera los errores de ambos bandos.
- **Practicar errores** en ese informe plantea cada uno de tus errores y
  errores garrafales como un ejercicio: la posición antes de la última
  jugada del rival, esa jugada, y luego encuentras una jugada mejor que
  la tuya. Una de las tres mejores jugadas del motor también cuenta si
  está a menos de medio peón de la mejor, igual que cualquier jugada que
  dé mate. Contra Stockfish o Maia solo se practican tus propios errores;
  en una partida entre dos o una importada, los de ambos bandos.
  **Pista** y **Ver solución** funcionan como en los ejercicios, y nada
  cuenta para la puntuación de ejercicios. Tras una solución limpia, el
  siguiente error llega solo; **Siguiente error** avanza en cualquier
  momento, y en el último, **Volver a la partida** deja la partida como
  estaba. Partida nueva e Importar PGN terminan la práctica. Cerrar la
  aplicación también la termina; una partida aún en curso se conserva.

## Aperturas

- El **Entrenamiento de aperturas** en Partida nueva practica una de 20
  aperturas conocidas. La aplicación juega el otro bando y solo acepta la
  respuesta correcta; con «Mostrar sugerencia de jugada» marca esa
  respuesta. Al final puedes repetir la línea, elegir otra o continuar
  libremente.
- La **Biblioteca de aperturas** en el menú muestra cada apertura jugada
  a jugada, sin partida.

## Ejercicios

- **Ejercicios** en Partida nueva plantea ejercicios tácticos:
  posiciones de partidas reales en las que una jugada o una secuencia
  corta gana material o da mate. El ejercicio decide qué color juegas.
- Primero juega el rival; su jugada queda marcada en el tablero. Después
  encuentra la mejor jugada para tu bando. Si la solución tiene varias
  jugadas, la aplicación responde a cada jugada correcta hasta resolver
  el ejercicio. Cualquier jugada que dé mate lo resuelve, aunque la
  solución guardada dé mate de otra manera.
- Una jugada incorrecta se muestra un momento y se deshace; inténtalo de
  nuevo.
- **Pista** marca la pieza que tiene que moverse. **Ver solución** juega
  el resto de la solución jugada a jugada.
- Tu **puntuación de ejercicios** empieza en 1200 y aparece junto a la
  puntuación del ejercicio. Resolver un ejercicio sin jugada incorrecta,
  pista ni solución la sube; si no, baja, y los ejercicios más difíciles
  cuentan más. Los primeros ejercicios son los que más cuentan, así que
  la puntuación suele encontrar tu nivel en pocos ejercicios. El
  siguiente ejercicio se elige cerca de tu puntuación; los ejercicios
  jugados hace poco no vuelven pronto. Dejar un ejercicio sin resolver,
  por una partida nueva u otro ejercicio, cuenta como no resuelto.
- Si los ejercicios siguen siendo demasiado fáciles o difíciles,
  ponlos **Más fácil** o **Más difícil** que tu puntuación en Partida
  nueva, en unos 300 puntos; **Normal** es lo predeterminado. Tu
  puntuación cuenta igual: un ejercicio más difícil resuelto simplemente
  da más.
- Tras una solución limpia, el siguiente ejercicio llega solo. Si no, la
  posición se queda en el tablero hasta que pulses **Siguiente
  ejercicio**.
- Deshacer jugada, la evaluación y las pistas del motor están
  desactivadas en los ejercicios. Los tableros electrónicos todavía no se
  usan para los ejercicios; resuélvelos en la pantalla. El botón NEW
  GAME de un Chessnut inicia el siguiente ejercicio; mientras un
  ejercicio no esté resuelto, púlsalo dos veces.
- Al cerrar la aplicación, un ejercicio empezado se conserva; tras uno
  terminado, el próximo inicio sigue con un ejercicio nuevo. Los cerca
  de 4.600 ejercicios proceden de la base de datos libre de ejercicios
  de Lichess.

## Chess960

- Con **Chess960** en Partida nueva, cada partida empieza desde una de
  las 960 posiciones iniciales, elegida al azar: las piezas de la
  primera fila están mezcladas, igual para ambos bandos, y los peones
  están como siempre. Funciona contra Stockfish, Maia u otra persona; la
  aplicación muestra el número de la posición.
- Maia solo aprendió de partidas de ajedrez normal. En Chess960 juega
  bastante más débil que la puntuación elegida; aun así, una puntuación más
  alta sigue jugando mejor que una más baja.
- Para enrocar, mueve el rey sobre su propia torre. Después, rey y torre
  quedan como en el ajedrez normal: en g1 y f1 o en c1 y d1. Si ninguna
  jugada normal del rey lleva a g1 o c1, también puedes mover el rey
  directamente allí.
  Al seleccionar el rey, la torre también muestra un punto
  verde: significa enroque, no capturar tu propia pieza.
- En un tablero electrónico, coloca las piezas como se ven en la
  pantalla; hasta que coincidan, se iluminan las casillas distintas.
  Enroca como recomiendan las reglas de la FIDE: levanta el rey, mueve la
  torre y deja el rey. Si rey y torre solo intercambian sus casillas, el
  Pegasus no nota la diferencia; haz ese enroque en la pantalla.

## Guardar y cargar partidas

- **Exportar PGN** en el menú entrega la partida actual en formato PGN,
  el que leen otros programas de ajedrez: en Android a la aplicación que
  elijas, en el escritorio como archivo.
- **Importar PGN** abre un archivo PGN. Si contiene varias partidas,
  elige una. La partida se carga en su última jugada; Deshacer jugada la
  recorre hacia atrás, y puedes seguir jugando desde cualquier punto.

## Tableros electrónicos

MoveAPiece funciona con el DGT Pegasus y los Chessnut Air, Air+, Pro y Go
por Bluetooth.

- Enciende el tablero y pulsa el botón del tablero (junto a Pista). Elige
  tu tablero de la lista; la aplicación reconoce el tipo por sí sola. En
  Android, permite el acceso Bluetooth cuando se pida.
- Las jugadas hechas en el tablero aparecen en la pantalla, y la
  respuesta del rival se ilumina en el tablero: hazla allí. Puedes seguir
  moviendo en la pantalla al mismo tiempo.
- Si las piezas no coinciden con la partida, una nota indica las casillas
  y los LED las señalan. La jugada de la aplicación espera hasta que el
  tablero esté bien.
- **Pegasus**: el tablero solo detecta que una casilla está ocupada, no
  con qué pieza. Las promociones se eligen en la pantalla. Si una captura
  no está clara, levanta brevemente la pieza de la casilla de destino.
- **Chessnut**: el tablero reconoce cada pieza. Una posición colocada en
  el tablero se puede adoptar: toca o haz clic en la nota que aparece. El
  botón NEW GAME del tablero empieza una partida nueva; durante una
  partida, púlsalo dos veces.
- Pulsa de nuevo el botón del tablero para desconectarlo.

## Conviene saber

- MoveAPiece funciona totalmente sin conexión y no recoge datos; la
  política de privacidad está en el menú.
- La aplicación es software libre bajo la GPL. El código fuente, las
  versiones y el gestor de incidencias para preguntas y errores están en
  github.com/egdels/moveapiece.
