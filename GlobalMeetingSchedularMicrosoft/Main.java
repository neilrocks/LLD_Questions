import java.util.*;

/**
 * ============================================================
 * MICROSOFT SDE2 INTERVIEW - GLOBAL MEETING SCHEDULER
 * ============================================================
 *
 * WHAT TO SAY (FLOW):
 *
 * 1. Convert everything to UTC (ignored here for simplicity)
 * 2. For each user:
 *      working hours - busy slots = free slots
 * 3. Find common free slot:
 *      a) Pairwise intersection (safe)
 *      b) Line sweep (optimized)
 * 4. Conflict resolution:
 *      Use optimistic locking (retry if conflict)
 *
 * ============================================================
 */

class Interval {
    int start, end;

    Interval(int s, int e) {
        this.start = s;
        this.end = e;
    }
}

class User {
    String id;
    List<Interval> busy; // sorted busy intervals

    User(String id, List<Interval> busy) {
        this.id = id;
        this.busy = busy;
    }
}

class Event {
    int time;
    int delta; // +1 start, -1 end

    Event(int t, int d) {
        this.time = t;
        this.delta = d;
    }
}

public class MeetingSchedulerInterview {

    // ============================================================
    // STEP 1: BUSY -> FREE (Interval Subtraction)
    // ============================================================
    public List<Interval> getFreeSlots(List<Interval> busy, int start, int end) {
        List<Interval> free = new ArrayList<>();
        Collections.sort(busy, (a, b) -> a.start - b.start);

        int cursor = start;

        for (Interval b : busy) {
            if (cursor < b.start) {
                free.add(new Interval(cursor, b.start));
            }
            cursor = Math.max(cursor, b.end);
        }

        if (cursor < end) {
            free.add(new Interval(cursor, end));
        }

        return free;
    }

    // ============================================================
    // STEP 2A: INTERSECTION APPROACH (SAFE)
    // ============================================================
    public List<Interval> intersectTwo(List<Interval> A, List<Interval> B) {
        List<Interval> res = new ArrayList<>();
        int i = 0, j = 0;

        while (i < A.size() && j < B.size()) {
            int start = Math.max(A.get(i).start, B.get(j).start);
            int end   = Math.min(A.get(i).end,   B.get(j).end);

            if (start < end) {
                res.add(new Interval(start, end));
            }

            if (A.get(i).end < B.get(j).end) i++;
            else j++;
        }

        return res;
    }

    public List<Interval> findCommonFree_Intersection(List<User> users, int start, int end) {

        List<List<Interval>> freeLists = new ArrayList<>();

        for (User u : users) {
            freeLists.add(getFreeSlots(u.busy, start, end));
        }

        List<Interval> common = freeLists.get(0);

        for (int i = 1; i < freeLists.size(); i++) {
            common = intersectTwo(common, freeLists.get(i));
            if (common.isEmpty()) return common;
        }

        return common;
    }

    // ============================================================
    // STEP 2B: LINE SWEEP APPROACH (OPTIMIZED)
    // ============================================================
    public List<Interval> findCommonFree_LineSweep(List<User> users) {
        List<Event> events = new ArrayList<>();

        for (User u : users) {
            for (Interval in : u.busy) {
                events.add(new Event(in.start, +1));
                events.add(new Event(in.end, -1));
            }
        }

        Collections.sort(events, (a, b) -> {
            if (a.time == b.time) {
                return a.delta - b.delta; // END before START
            }
            return a.time - b.time;
        });

        List<Interval> res = new ArrayList<>();
        int active = 0;
        Integer freeStart = null;

        for (Event e : events) {
            int prev = active;
            active += e.delta;

            // busy -> free
            if (prev > 0 && active == 0) {
                freeStart = e.time;
            }

            // free -> busy
            else if (prev == 0 && active > 0 && freeStart != null) {
                res.add(new Interval(freeStart, e.time));
                freeStart = null;
            }
        }

        return res;
    }

    // ============================================================
    // STEP 3: PICK SLOT WITH REQUIRED DURATION
    // ============================================================
    public Interval pickSlot(List<Interval> slots, int duration) {
        for (Interval in : slots) {
            if (in.end - in.start >= duration) {
                return new Interval(in.start, in.start + duration);
            }
        }
        return null;
    }

    // ============================================================
    // STEP 4: CONFLICT RESOLUTION (OPTIMISTIC LOCK SIMULATION)
    // ============================================================
    // In real system: DB transaction / version check
    public boolean tryBookSlot(List<User> users, Interval slot) {

        // Check again before booking (IMPORTANT)
        for (User u : users) {
            for (Interval busy : u.busy) {
                if (!(slot.end <= busy.start || slot.start >= busy.end)) {
                    return false; // conflict detected
                }
            }
        }

        // If no conflict → book (add to all users)
        for (User u : users) {
            u.busy.add(new Interval(slot.start, slot.end));
        }

        return true;
    }

    // Retry mechanism
    public Interval scheduleMeeting(List<User> users, int start, int end, int duration) {

        for (int attempt = 0; attempt < 3; attempt++) {

            // 1. Find free slots
            List<Interval> free = findCommonFree_Intersection(users, start, end);

            // 2. Pick slot
            Interval chosen = pickSlot(free, duration);
            if (chosen == null) return null;

            // 3. Try booking
            if (tryBookSlot(users, chosen)) {
                return chosen;
            }

            // else retry
        }

        return null;
    }

    // ============================================================
    // DRIVER (USE THIS TO EXPLAIN IN INTERVIEW)
    // ============================================================
    public static void main(String[] args) {

        MeetingSchedulerInterview obj = new MeetingSchedulerInterview();

        User u1 = new User("A", new ArrayList<>(Arrays.asList(
                new Interval(1, 3),
                new Interval(6, 7)
        )));

        User u2 = new User("B", new ArrayList<>(Arrays.asList(
                new Interval(2, 4)
        )));

        User u3 = new User("C", new ArrayList<>(Arrays.asList(
                new Interval(2, 5),
                new Interval(9, 12)
        )));

        List<User> users = Arrays.asList(u1, u2, u3);

        int start = 0, end = 15, duration = 1;

        // Approach 1
        System.out.println("=== Intersection Approach ===");
        List<Interval> res1 = obj.findCommonFree_Intersection(users, start, end);
        for (Interval in : res1) {
            System.out.println("[" + in.start + ", " + in.end + "]");
        }

        // Approach 2
        System.out.println("\n=== Line Sweep Approach ===");
        List<Interval> res2 = obj.findCommonFree_LineSweep(users);
        for (Interval in : res2) {
            System.out.println("[" + in.start + ", " + in.end + "]");
        }

        // Scheduling with conflict handling
        System.out.println("\n=== Schedule Meeting ===");
        Interval meeting = obj.scheduleMeeting(users, start, end, duration);

        if (meeting != null) {
            System.out.println("Scheduled at: [" + meeting.start + ", " + meeting.end + "]");
        } else {
            System.out.println("No slot available");
        }
    }
}