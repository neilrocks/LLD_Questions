import java.util.*;

// ─────────────────────────────────────────────
// ObstacleType Enum
// ─────────────────────────────────────────────
enum ObstacleType {
    SNAKE, LADDER
}

// ─────────────────────────────────────────────
// Obstacle (Abstract Base)
// ─────────────────────────────────────────────
abstract class Obstacle {
    protected int src;
    protected int dst;

    public Obstacle(int src, int dst) {
        this.src = src;
        this.dst = dst;
    }

    public abstract ObstacleType getObstacleType();

    // Moves player from src to dst
    public int movePlayer() {
        return dst;
    }

    public int getSrc() { return src; }
    public int getDst() { return dst; }
}

// ─────────────────────────────────────────────
// Snake — src is head (higher), dst is tail (lower)
// ─────────────────────────────────────────────
class Snake extends Obstacle {
    public Snake(int head, int tail) {
        super(head, tail);
        if (head <= tail) throw new IllegalArgumentException("Snake head must be above tail");
    }

    @Override
    public ObstacleType getObstacleType() {
        return ObstacleType.SNAKE;
    }

    @Override
    public String toString() {
        return "Snake[head=" + src + " -> tail=" + dst + "]";
    }
}

// ─────────────────────────────────────────────
// Ladder — src is bottom, dst is top (higher)
// ─────────────────────────────────────────────
class Ladder extends Obstacle {
    public Ladder(int bottom, int top) {
        super(bottom, top);
        if (bottom >= top) throw new IllegalArgumentException("Ladder bottom must be below top");
    }

    @Override
    public ObstacleType getObstacleType() {
        return ObstacleType.LADDER;
    }

    @Override
    public String toString() {
        return "Ladder[bottom=" + src + " -> top=" + dst + "]";
    }
}

// ─────────────────────────────────────────────
// ObstacleFactory
// ─────────────────────────────────────────────
class ObstacleFactory {
    public static Obstacle createObstacle(ObstacleType type, int src, int dst) {
        return switch (type) {
            case SNAKE  -> new Snake(src, dst);
            case LADDER -> new Ladder(src, dst);
        };
    }
}

// ─────────────────────────────────────────────
// Cell
// ─────────────────────────────────────────────
class Cell {
    private final int position;   // cell number (1-based)
    private Obstacle obstacle;    // null if no obstacle

    public Cell(int position) {
        this.position = position;
    }

    public boolean hasObstacle() {
        return obstacle != null;
    }

    // Returns final position after applying obstacle (or same cell if none)
    public int getFinalPos() {
        return hasObstacle() ? obstacle.movePlayer() : position;
    }

    public void setObstacle(Obstacle obstacle) {
        this.obstacle = obstacle;
    }

    public Obstacle getObstacle() { return obstacle; }
    public int getPosition()      { return position; }
}

// ─────────────────────────────────────────────
// Board
// ─────────────────────────────────────────────
class Board {
    private final int size;         // e.g. 10 → 10x10 board
    private final int sizeLength;   // total cells = size * size
    private final Cell[][] grid;    // 2D grid (internal); we use 1-D indexing via helpers

    public Board(int size) {
        this.size       = size;
        this.sizeLength = size * size;
        this.grid       = new Cell[size][size];
        initCells();
    }

    private void initCells() {
        int cellNo = 1;
        // Fill from bottom row (r=0) to top row (r=size-1)
        // Even rows (0,2,4...) → left to right
        // Odd  rows (1,3,5...) → right to left
        for (int r = 0; r < size; r++) {
            if (r % 2 == 0) {
                // left → right
                for (int c = 0; c < size; c++)
                    grid[r][c] = new Cell(cellNo++);
            } else {
                // right → left
                for (int c = size - 1; c >= 0; c--)
                    grid[r][c] = new Cell(cellNo++);
            }
        }
    }

    // Convert 1-based cell number → Cell (reverse boustrophedon mapping)
    private Cell getCell(int pos) {
        int idx = pos - 1;          // 0-based index
        int r   = idx / size;       // which row (0 = bottom)
        int col = idx % size;       // offset within the row
        // Even rows: left to right  (c = col as-is)
        // Odd  rows: right to left  (c = size-1-col)
        int c = (r % 2 == 0) ? col : (size - 1 - col);
        return grid[r][c];
    }

    public boolean addObstacle(Obstacle obstacle) {
        int src = obstacle.getSrc();
        int dst = obstacle.getDst();
        if (src < 1 || src > sizeLength || dst < 1 || dst > sizeLength) return false;
        Cell cell = getCell(src);
        if (cell.hasObstacle()) return false;  // cell already occupied
        cell.setObstacle(obstacle);
        return true;
    }

    // Given player's current position + dice roll, return new final position
    public int getNewPosition(Player player, int diceValue) {
        int next = player.getPos() + diceValue;
        if (next > sizeLength) return player.getPos(); // can't overshoot last cell
        return getCell(next).getFinalPos();
    }

    public int getSizeLength() { return sizeLength; }

    public void printBoard() {
        System.out.println("\n=== BOARD (" + size + "x" + size + ") ===");
        for (int r = size - 1; r >= 0; r--) {
            for (int c = 0; c < size; c++) {
                Cell cell = grid[r][c];
                String mark = "  " + String.format("%3d", cell.getPosition());
                if (cell.hasObstacle()) {
                    mark += (cell.getObstacle().getObstacleType() == ObstacleType.SNAKE ? "(S)" : "(L)");
                } else {
                    mark += "   ";
                }
                System.out.print(mark);
            }
            System.out.println();
        }
        System.out.println();
    }
}

// ─────────────────────────────────────────────
// Player
// ─────────────────────────────────────────────
class Player {
    private final String name;
    private int pos;

    public Player(String name) {
        this.name = name;
        this.pos  = 0; // start before cell 1
    }

    public String getName() { return name; }
    public int    getPos()  { return pos;  }
    public void   setPos(int pos) { this.pos = pos; }
}

// ─────────────────────────────────────────────
// Dice
// ─────────────────────────────────────────────
class Dice {
    private final int noOfDice;
    private final Random random;

    public Dice(int noOfDice) {
        this.noOfDice = noOfDice;
        this.random   = new Random();
    }

    // Sum of all dice
    public int roll() {
        int total = 0;
        for (int i = 0; i < noOfDice; i++)
            total += random.nextInt(6) + 1;
        return total;
    }
}

// ─────────────────────────────────────────────
// Game
// ─────────────────────────────────────────────
class Game {
    private final Deque<Player> players;
    private final Board         board;
    private final int           noOfSnakes;
    private final int           noOfLadders;
    private final Dice          dice;

    // Constructor: boardSize, noOfSnakes, noOfLadders, noOfDice
    public Game(int boardSize, int noOfSnakes, int noOfLadders, int noOfDice) {
        this.noOfSnakes  = noOfSnakes;
        this.noOfLadders = noOfLadders;
        this.board       = new Board(boardSize);
        this.dice        = new Dice(noOfDice);
        this.players     = new ArrayDeque<>();

        setupObstacles();
    }

    public void addPlayer(String name) {
        players.add(new Player(name));
    }

    // ── Randomly place snakes and ladders ──────
    private void setupObstacles() {
        int totalCells = board.getSizeLength();
        Set<Integer> used = new HashSet<>();

        // Place Snakes
        int placed = 0;
        while (placed < noOfSnakes) {
            int head = randomBetween(2, totalCells, used);   // snake head can't be cell 1
            int tail = randomBetween(1, head - 1, new HashSet<>()); // tail below head
            Obstacle snake = ObstacleFactory.createObstacle(ObstacleType.SNAKE, head, tail);
            if (board.addObstacle(snake)) {
                used.add(head);
                System.out.println("Placed " + snake);
                placed++;
            }
        }

        // Place Ladders
        placed = 0;
        while (placed < noOfLadders) {
            int bottom = randomBetween(1, totalCells - 1, used);
            int top    = randomBetween(bottom + 1, totalCells, new HashSet<>());
            Obstacle ladder = ObstacleFactory.createObstacle(ObstacleType.LADDER, bottom, top);
            if (board.addObstacle(ladder)) {
                used.add(bottom);
                System.out.println("Placed " + ladder);
                placed++;
            }
        }
    }

    private int randomBetween(int min, int max, Set<Integer> exclude) {
        if (min > max) throw new IllegalStateException("Board too small for requested obstacles");
        Random rnd = new Random();
        int val;
        do { val = min + rnd.nextInt(max - min + 1); }
        while (exclude.contains(val));
        return val;
    }

    // ── Main game loop ─────────────────────────
    public void startGame() {
        if (players.size() < 2) {
            System.out.println("Need at least 2 players to start!");
            return;
        }

        board.printBoard();
        System.out.println("=== GAME STARTED ===\n");

        Player winner = null;

        while (winner == null) {
            Player current = players.poll();  // dequeue from front
            int roll = dice.roll();
            int oldPos = current.getPos();
            int newPos = board.getNewPosition(current, roll);

            current.setPos(newPos);

            System.out.printf("%-10s | Roll: %2d | %3d → %3d", current.getName(), roll, oldPos, newPos);

            // Print obstacle effect if triggered
            if (oldPos + roll != newPos && oldPos + roll <= board.getSizeLength()) {
                Cell arrivedAt = null; // we check via difference
                if (newPos < oldPos + roll) {
                    System.out.print("  🐍 Bitten by a snake!");
                } else if (newPos > oldPos + roll) {
                    System.out.print("  🪜 Climbed a ladder!");
                }
            }
            System.out.println();

            if (newPos == board.getSizeLength()) {
                winner = current;
            } else {
                players.add(current);  // re-enqueue at back
            }
        }

        System.out.println("\n🏆 " + winner.getName() + " wins the game!");
    }
}

// ─────────────────────────────────────────────
// Main / Driver
// ─────────────────────────────────────────────
public class Main {
    public static void main(String[] args) {
        // Game(boardSize=10, snakes=5, ladders=5, dice=1)
        Game game = new Game(10, 5, 5, 1);

        game.addPlayer("Alice");
        game.addPlayer("Bob");
        game.addPlayer("Charlie");

        game.startGame();
    }
}