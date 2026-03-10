/*
===========================================================
TIC TAC TOE - LOW LEVEL DESIGN (SDE2 INTERVIEW STYLE)
Single File Java Implementation
===========================================================

REQUIREMENTS
------------
1. The system should support an N x N board.
2. The game should support multiple players (here we use 2).
3. Each player has a unique piece (X or O).
4. Players take turns sequentially.
5. The system must validate moves (cannot place on occupied cell).
6. After every move the system should check if the player has won.
7. Winning conditions:
      - All cells in a row are same piece
      - All cells in a column are same piece
      - All cells in diagonal are same piece
8. If the board fills and no one wins -> DRAW.
9. Design should be extensible for:
      - Different board sizes
      - Additional winning strategies
      - AI/Bot players

DESIGN PRINCIPLES USED
----------------------
1. Single Responsibility Principle
2. Strategy Pattern for Winning Logic
3. Extensible board size
4. Clean separation between Game / Board / Player

CORE CLASSES
------------
Game -> orchestrates gameplay
Board -> maintains board state
Cell -> individual grid cell
Player -> player information
Piece -> X or O
WinningStrategy -> interface for win detection
RowWinningStrategy / ColumnWinningStrategy / DiagonalWinningStrategy
*/

import java.util.*;

/* ------------------------------------------------------
ENUMS
------------------------------------------------------*/

enum GameStatus {
    IN_PROGRESS,
    SUCCESS,
    DRAW
}

enum PieceType {
    X,
    O
}

/* ------------------------------------------------------
PIECE
Represents the symbol used by a player
------------------------------------------------------*/
class Piece {

    private PieceType type;

    public Piece(PieceType type) {
        this.type = type;
    }

    public PieceType getType() {
        return type;
    }
}

/* ------------------------------------------------------
PLAYER
Represents a player in the game
------------------------------------------------------*/
class Player {

    private String name;
    private Piece piece;

    public Player(String name, Piece piece) {
        this.name = name;
        this.piece = piece;
    }

    public String getName() {
        return name;
    }

    public Piece getPiece() {
        return piece;
    }
}

/* ------------------------------------------------------
CELL
Represents a single board cell
------------------------------------------------------*/
class Cell {

    int row;
    int col;
    Piece piece;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }

    // Check if the cell is empty
    public boolean isEmpty() {
        return piece == null;
    }

    // Place a piece in the cell
    public void setPiece(Piece piece) {
        this.piece = piece;
    }
}

/* ------------------------------------------------------
BOARD
Maintains the state of the board
------------------------------------------------------*/
class Board {

    int size;
    Cell[][] grid;

    public Board(int size) {
        this.size = size;
        grid = new Cell[size][size];

        // Initialize board cells
        for(int i=0;i<size;i++) {
            for(int j=0;j<size;j++) {
                grid[i][j] = new Cell(i,j);
            }
        }
    }

    // Try placing a piece on the board
    public boolean placePiece(int row,int col,Piece piece){

        if(!grid[row][col].isEmpty())
            return false;

        grid[row][col].setPiece(piece);
        return true;
    }

    public Cell[][] getGrid(){
        return grid;
    }

    // Utility method to print board state
    public void printBoard(){

        for(int i=0;i<size;i++){

            for(int j=0;j<size;j++){

                if(grid[i][j].piece == null)
                    System.out.print("- ");
                else
                    System.out.print(grid[i][j].piece.getType()+" ");
            }

            System.out.println();
        }

        System.out.println();
    }
}

/* ------------------------------------------------------
WINNING STRATEGY INTERFACE
Strategy Pattern
Allows adding new win rules without modifying Game
------------------------------------------------------*/
interface WinningStrategy {

    boolean checkWinner(Board board,int row,int col,Piece piece);
}

/* ------------------------------------------------------
ROW WIN STRATEGY
------------------------------------------------------*/
class RowWinningStrategy implements WinningStrategy {

    public boolean checkWinner(Board board,int row,int col,Piece piece){

        Cell[][] grid = board.getGrid();

        for(int j=0;j<board.size;j++){

            if(grid[row][j].piece == null ||
               grid[row][j].piece.getType()!=piece.getType())
                return false;
        }

        return true;
    }
}

/* ------------------------------------------------------
COLUMN WIN STRATEGY
------------------------------------------------------*/
class ColumnWinningStrategy implements WinningStrategy {

    public boolean checkWinner(Board board,int row,int col,Piece piece){

        Cell[][] grid = board.getGrid();

        for(int i=0;i<board.size;i++){

            if(grid[i][col].piece == null ||
               grid[i][col].piece.getType()!=piece.getType())
                return false;
        }

        return true;
    }
}

/* ------------------------------------------------------
DIAGONAL WIN STRATEGY
------------------------------------------------------*/
class DiagonalWinningStrategy implements WinningStrategy {

    public boolean checkWinner(Board board,int row,int col,Piece piece){

        Cell[][] grid = board.getGrid();
        int size = board.size;

        boolean win = true;

        for(int i=0;i<size;i++){

            if(grid[i][i].piece == null ||
               grid[i][i].piece.getType()!=piece.getType()){
                win=false;
                break;
            }
        }

        if(win) return true;

        win=true;

        for(int i=0;i<size;i++){

            if(grid[i][size-i-1].piece == null ||
               grid[i][size-i-1].piece.getType()!=piece.getType()){
                win=false;
                break;
            }
        }

        return win;
    }
}

/* ------------------------------------------------------
GAME CLASS
Main orchestrator of the system
------------------------------------------------------*/
class Game {

    Board board;
    Queue<Player> turnQueue;
    List<WinningStrategy> strategies;
    GameStatus status;
    int movesPlayed;

    public Game(int size,List<Player> players){

        board = new Board(size);
        turnQueue = new LinkedList<>(players);
        strategies = new ArrayList<>();

        strategies.add(new RowWinningStrategy());
        strategies.add(new ColumnWinningStrategy());
        strategies.add(new DiagonalWinningStrategy());

        status = GameStatus.IN_PROGRESS;
        movesPlayed = 0;
    }

    // Main move method called by driver
    public void makeMove(int row,int col){

        if(status!=GameStatus.IN_PROGRESS)
            return;

        Player player = turnQueue.poll();

        boolean placed = board.placePiece(row,col,player.getPiece());

        if(!placed){

            System.out.println("Invalid Move");
            turnQueue.offer(player);
            return;
        }

        movesPlayed++;

        System.out.println(player.getName()+" placed "+player.getPiece().getType()+" at "+row+","+col);

        board.printBoard();

        if(checkWinner(row,col,player)){

            status = GameStatus.SUCCESS;
            System.out.println("WINNER : "+player.getName());
            return;
        }

        if(movesPlayed == board.size * board.size){

            status = GameStatus.DRAW;
            System.out.println("GAME DRAW");
            return;
        }

        turnQueue.offer(player);
    }

    // Check all winning strategies
    private boolean checkWinner(int row,int col,Player player){

        for(WinningStrategy strategy : strategies){

            if(strategy.checkWinner(board,row,col,player.getPiece()))
                return true;
        }

        return false;
    }
}

/* ------------------------------------------------------
MAIN DRIVER CLASS
Shows 3 scenarios:
1. Player A wins
2. Player B wins
3. Draw
------------------------------------------------------*/
public class TicTacToeLLD {

    public static void main(String[] args) {

        Player A = new Player("PlayerA", new Piece(PieceType.X));
        Player B = new Player("PlayerB", new Piece(PieceType.O));

        List<Player> players = Arrays.asList(A,B);

        /* ------------------------------------------------------
        SCENARIO 1 : PLAYER A WINS
        ------------------------------------------------------*/

        System.out.println("===== SCENARIO 1 : PLAYER A WINS =====");

        Game game1 = new Game(3,players);

        game1.makeMove(0,0);
        game1.makeMove(1,0);
        game1.makeMove(0,1);
        game1.makeMove(1,1);
        game1.makeMove(0,2);


        /* ------------------------------------------------------
        SCENARIO 2 : PLAYER B WINS
        ------------------------------------------------------*/

        System.out.println("===== SCENARIO 2 : PLAYER B WINS =====");

        Game game2 = new Game(3,players);

        game2.makeMove(0,0);
        game2.makeMove(0,1);
        game2.makeMove(1,0);
        game2.makeMove(1,1);
        game2.makeMove(2,2);
        game2.makeMove(2,1);


        /* ------------------------------------------------------
        SCENARIO 3 : DRAW
        ------------------------------------------------------*/

        System.out.println("===== SCENARIO 3 : DRAW =====");

        Game game3 = new Game(3,players);

        game3.makeMove(0,0);
        game3.makeMove(0,1);
        game3.makeMove(0,2);
        game3.makeMove(1,1);
        game3.makeMove(1,0);
        game3.makeMove(1,2);
        game3.makeMove(2,1);
        game3.makeMove(2,0);
        game3.makeMove(2,2);
    }
}