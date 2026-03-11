/*
===========================
CHESS GAME – LLD DESIGN
===========================

FUNCTIONAL REQUIREMENTS
-----------------------
1. Two players play a chess game (White vs Black).
2. Board size is fixed to 8x8.
3. Game should support all chess pieces:
   - King
   - Queen
   - Rook
   - Bishop
   - Knight
   - Pawn
4. Each piece should have its own movement logic.
5. Special moves should be extensible (example: castling, promotion).
6. Support undo and redo functionality.
7. Spectators should be able to watch the game and receive move notifications.
8. Initially support Player vs Player but should be extensible for Bot Player.

NON FUNCTIONAL REQUIREMENTS
----------------------------
1. Extensible move logic.
2. Decouple piece logic from move logic.
3. Use design patterns where appropriate.
4. Focus on single game session (not high traffic distributed system).

DESIGN PATTERNS USED
---------------------
1. Strategy Pattern → MoveStrategy (decouple move logic)
2. Observer Pattern → Spectators observing game moves
3. Command Pattern → Move objects used for undo/redo

CORE ENTITIES
--------------
Game
Board
Cell
Piece (Abstract)
  - King
  - Queen
  - Rook
  - Bishop
  - Knight
  - Pawn

MoveStrategy (Interface)

Player (Abstract)
  - HumanPlayer
  - BotPlayer

MoveService

Move
Action

Spectator (Observer)

GameBroadcaster
*/
import java.util.*;

/*
Position class representing coordinates on board
*/
class Position {
    int row;
    int col;

    Position(int r, int c) {
        row = r;
        col = c;
    }
}

/*
Cell represents each square on chess board
*/
class Cell {
    Position position;
    Piece piece;

    Cell(int r, int c) {
        position = new Position(r, c);
    }

    boolean isEmpty() {
        return piece == null;
    }
}

/*
Board maintains 8x8 grid of cells
*/
class Board {

    private Cell[][] grid;

    Board() {
        grid = new Cell[8][8];

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                grid[i][j] = new Cell(i, j);
            }
        }
    }

    Cell getCell(Position pos) {
        return grid[pos.row][pos.col];
    }

    void placePiece(Piece piece, Position pos) {
        grid[pos.row][pos.col].piece = piece;
    }
}

/*
Enum representing piece color
*/
enum Color {
    WHITE,
    BLACK
}

/*
MoveStrategy interface – Strategy Pattern

Each strategy defines how a piece moves.
*/
interface MoveStrategy {
    List<Position> getValidMoves(Position start, Board board);
}

/*
Abstract Piece class
Each piece uses MoveStrategy
*/
abstract class Piece {

    Color color;
    List<MoveStrategy> strategies = new ArrayList<>();

    Piece(Color color) {
        this.color = color;
    }

    List<Position> getValidMoves(Position start, Board board) {

        List<Position> moves = new ArrayList<>();

        for (MoveStrategy strategy : strategies) {
            moves.addAll(strategy.getValidMoves(start, board));
        }

        return moves;
    }
}

/*
Concrete Piece Implementations
*/
class Rook extends Piece {

    Rook(Color color) {
        super(color);
        strategies.add(new HorizontalMoveStrategy());
        strategies.add(new VerticalMoveStrategy());
    }
}

class Bishop extends Piece {

    Bishop(Color color) {
        super(color);
        strategies.add(new DiagonalMoveStrategy());
    }
}

class Queen extends Piece {

    Queen(Color color) {
        super(color);
        strategies.add(new HorizontalMoveStrategy());
        strategies.add(new VerticalMoveStrategy());
        strategies.add(new DiagonalMoveStrategy());
    }
}

class Knight extends Piece {

    Knight(Color color) {
        super(color);
        strategies.add(new KnightMoveStrategy());
    }
}

class King extends Piece {

    King(Color color) {
        super(color);
        strategies.add(new KingMoveStrategy());
    }
}

class Pawn extends Piece {

    Pawn(Color color) {
        super(color);
        strategies.add(new PawnMoveStrategy());
    }
}

/*
Example Move Strategies
*/

class HorizontalMoveStrategy implements MoveStrategy {

    public List<Position> getValidMoves(Position start, Board board) {
        return new ArrayList<>(); // simplified
    }
}

class VerticalMoveStrategy implements MoveStrategy {

    public List<Position> getValidMoves(Position start, Board board) {
        return new ArrayList<>();
    }
}

class DiagonalMoveStrategy implements MoveStrategy {

    public List<Position> getValidMoves(Position start, Board board) {
        return new ArrayList<>();
    }
}

class KnightMoveStrategy implements MoveStrategy {

    public List<Position> getValidMoves(Position start, Board board) {
        return new ArrayList<>();
    }
}

class KingMoveStrategy implements MoveStrategy {

    public List<Position> getValidMoves(Position start, Board board) {
        return new ArrayList<>();
    }
}

class PawnMoveStrategy implements MoveStrategy {

    public List<Position> getValidMoves(Position start, Board board) {
        return new ArrayList<>();
    }
}

/*
Move class represents a single move
Used for undo/redo functionality
*/
class Move {

    Position from;
    Position to;
    Piece movedPiece;
    Piece capturedPiece;

    Move(Position f, Position t, Piece p, Piece captured) {
        from = f;
        to = t;
        movedPiece = p;
        capturedPiece = captured;
    }
}

/*
MoveService

Responsible for:
1. Validating moves
2. Executing moves
3. Undo / Redo functionality
*/
class MoveService {

    Board board;

    Stack<Move> undoStack = new Stack<>();
    Stack<Move> redoStack = new Stack<>();

    MoveService(Board board) {
        this.board = board;
    }

    void executeMove(Move move) {

        Cell from = board.getCell(move.from);
        Cell to = board.getCell(move.to);

        move.capturedPiece = to.piece;

        to.piece = from.piece;
        from.piece = null;

        undoStack.push(move);
        redoStack.clear();
    }

    void undo() {

        if (undoStack.isEmpty())
            return;

        Move move = undoStack.pop();

        Cell from = board.getCell(move.from);
        Cell to = board.getCell(move.to);

        from.piece = move.movedPiece;
        to.piece = move.capturedPiece;

        redoStack.push(move);
    }

    void redo() {

        if (redoStack.isEmpty())
            return;

        Move move = redoStack.pop();
        executeMove(move);
    }
}

/*
Spectator interface – Observer Pattern
*/
interface Spectator {

    void onMove(Move move);
}

/*
GameBroadcaster responsible for notifying spectators
*/
interface GameBroadcaster {

    void registerSpectator(Spectator spectator);

    void notifySpectators(Move move);
}

/*
Abstract Player
*/
abstract class Player {

    Color color;

    Player(Color color) {
        this.color = color;
    }

    abstract Move makeMove();
}

/*
Human Player implementation
*/
class HumanPlayer extends Player {

    HumanPlayer(Color color) {
        super(color);
    }

    Move makeMove() {
        return null; // input from console/UI
    }
}

/*
Bot player for future extensibility
*/
class BotPlayer extends Player {

    BotPlayer(Color color) {
        super(color);
    }

    Move makeMove() {
        return null; // random / AI
    }
}

/*
Game class – Main engine

Responsibilities:
1. Maintain board
2. Manage players
3. Execute moves
4. Notify spectators
*/
class Game implements GameBroadcaster {

    Board board;
    MoveService moveService;

    Player white;
    Player black;

    List<Spectator> spectators = new ArrayList<>();

    Game(Player white, Player black) {

        this.white = white;
        this.black = black;

        board = new Board();
        moveService = new MoveService(board);
    }

    public void registerSpectator(Spectator spectator) {
        spectators.add(spectator);
    }

    public void notifySpectators(Move move) {
        for (Spectator s : spectators) {
            s.onMove(move);
        }
    }

    void playMove(Move move) {

        moveService.executeMove(move);

        notifySpectators(move);
    }
}

/*
Main class (client code)

Starts the chess game
*/
public class ChessGame {

    public static void main(String[] args) {

        Player white = new HumanPlayer(Color.WHITE);
        Player black = new HumanPlayer(Color.BLACK);

        Game game = new Game(white, black);

        // Example gameplay
        Position from = new Position(1,0);
        Position to = new Position(3,0);

        Move move = new Move(from,to,null,null);

        game.playMove(move);
    }
}