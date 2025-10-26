package ATMCashWithdrawal;
import java.util.*;

abstract class NoteHandler {
    protected NoteHandler nextHandler;
    protected int denomination;
    protected int count;

    public NoteHandler(int denomination, int count) {
        this.denomination = denomination;
        this.count = count;
    }

    public NoteHandler setNextHandler(NoteHandler handler) {
        this.nextHandler = handler;
        return handler;
    }

    public boolean withdraw(int amount, Map<Integer, Integer> dispensed) {
        int numNotes = 0;
        if (amount >= denomination && count > 0) {
            numNotes = Math.min(amount / denomination, count);
            amount -= numNotes * denomination;
            count -= numNotes;
            dispensed.put(denomination, numNotes);
        }

        if (amount == 0) return true;

        if (nextHandler != null) {
            boolean success = nextHandler.withdraw(amount, dispensed);
            if (!success) { // rollback
                count += numNotes;
                dispensed.remove(denomination);
            }
            return success;
        } else {
            count += numNotes; // rollback self
            return false;
        }
    }
}

class ATMMachine {
    private NoteHandler handler;

    public ATMMachine(List<Pair> notes) {
        NoteHandler thousand = new ConcreteNoteHandler(1000, notes.stream().filter(n -> n.denom == 1000).mapToInt(n -> n.count).sum());
        NoteHandler fiveHundred = new ConcreteNoteHandler(500, notes.stream().filter(n -> n.denom == 500).mapToInt(n -> n.count).sum());
        NoteHandler hundred = new ConcreteNoteHandler(100, notes.stream().filter(n -> n.denom == 100).mapToInt(n -> n.count).sum());

        handler = thousand;
        handler.setNextHandler(fiveHundred).setNextHandler(hundred);
    }

    public void withdraw(int amount) {
        if (amount % 100 != 0) {
            System.out.println("Amount must be multiple of 100.");
            return;
        }

        Map<Integer, Integer> dispensed = new TreeMap<>(Comparator.reverseOrder());
        boolean success = handler.withdraw(amount, dispensed);

        if (success) {
            dispensed.forEach((denom, count) -> System.out.println("Dispensed " + count + " notes of ₹" + denom));
        } else {
            System.out.println("Insufficient cash for requested amount.");
        }
    }
}

class ConcreteNoteHandler extends NoteHandler {
    public ConcreteNoteHandler(int denomination, int count) {
        super(denomination, count);
    }
}

class Pair {
    int denom, count;
    Pair(int denom, int count) {
        this.denom = denom;
        this.count = count;
    }
}

class ATMCashWithdrawal {
    public static void main(String[] args) {
        System.out.println("Welcome to Swapnil's ATM!");
        List<Pair> notes = Arrays.asList(new Pair(100, 10), new Pair(500, 10), new Pair(1000, 10));
        ATMMachine atm = new ATMMachine(notes);
        atm.withdraw(15000000);
    }
}
