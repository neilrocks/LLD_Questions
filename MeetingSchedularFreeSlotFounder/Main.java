import java.util.*;

/**
 * PROBLEM:
 * Given multiple users' BUSY schedules (intervals),
 * find time intervals where ALL users are FREE.
 *
 * ------------------------------------------------------------
 * APPROACH 1: Pairwise Intersection (DSA Classic)
 * ------------------------------------------------------------
 *
 * Idea:
 * 1. First convert BUSY → FREE for each user
 * 2. Then intersect free slots of all users
 *
 * DSA Concepts Used:
 * - Interval subtraction
 * - Two-pointer intersection
 *
 * Complexity: O(N * K)
 *
 * ------------------------------------------------------------
 * APPROACH 2: Line Sweep (Optimized)
 * ------------------------------------------------------------
 *
 * Idea:
 * Instead of finding free time per user,
 * combine ALL busy intervals and find where no one is busy.
 *
 * Use events:
 * start → +1
 * end   → -1
 *
 * When active == 0 → everyone is free
 *
 * Complexity: O(N log N)
 */

class Interval {
    int start, end;

    Interval(int s, int e) {
        this.start = s;
        this.end = e;
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

public class MeetingSchedulerDSA {

    // ============================================================
    // APPROACH 1: Pairwise Intersection
    // ============================================================

    /**
     * Step 1: Convert BUSY → FREE
     * Given busy intervals and working range, find free slots
     */
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

    /**
     * Step 2: Intersect two users' free slots
     * Two-pointer approach
     */
    public List<Interval> intersectTwo(List<Interval> A, List<Interval> B) {
        List<Interval> result = new ArrayList<>();

        int i = 0, j = 0;

        while (i < A.size() && j < B.size()) {
            int start = Math.max(A.get(i).start, B.get(j).start);
            int end   = Math.min(A.get(i).end,   B.get(j).end);

            if (start < end) { // valid overlap
                result.add(new Interval(start, end));
            }

            // Move pointer of smaller ending interval
            if (A.get(i).end < B.get(j).end) i++;
            else j++;
        }

        return result;
    }

    /**
     * Step 3: Intersect across multiple users
     */
    public List<Interval> findCommonFreeTime_Intersection(List<List<Interval>> schedules, int start, int end) {

        List<List<Interval>> freeLists = new ArrayList<>();

        // Convert each user's busy → free
        for (List<Interval> busy : schedules) {
            freeLists.add(getFreeSlots(busy, start, end));
        }

        // Start with first user's free slots
        List<Interval> common = freeLists.get(0);

        // Intersect with each user
        for (int i = 1; i < freeLists.size(); i++) {
            common = intersectTwo(common, freeLists.get(i));
            if (common.isEmpty()) return common;
        }

        return common;
    }

    // ============================================================
    // APPROACH 2: Line Sweep
    // ============================================================

    public List<Interval> findCommonFreeTime_LineSweep(List<List<Interval>> schedules) {
        List<Event> events = new ArrayList<>();

        // Convert all busy intervals into events
        for (List<Interval> user : schedules) {
            for (Interval in : user) {
                events.add(new Event(in.start, +1));
                events.add(new Event(in.end, -1));
            }
        }

        // Sort events
        Collections.sort(events, (a, b) -> {
            if (a.time == b.time) {
                return a.delta - b.delta; // end(-1) before start(+1)
            }
            return a.time - b.time;
        });

        List<Interval> result = new ArrayList<>();
        int active = 0;

        Integer freeStart = null;

        for (Event e : events) {
            int prevActive = active;
            active += e.delta;

            // Transition: busy → free
            if (prevActive > 0 && active == 0) {
                freeStart = e.time;
            }

            // Transition: free → busy
            else if (prevActive == 0 && active > 0 && freeStart != null) {
                result.add(new Interval(freeStart, e.time));
                freeStart = null;
            }
        }

        return result;
    }

    // ============================================================
    // DRIVER CODE
    // ============================================================

    public static void main(String[] args) {
        MeetingSchedulerDSA obj = new MeetingSchedulerDSA();

        List<List<Interval>> schedules = new ArrayList<>();

        // User 1
        schedules.add(Arrays.asList(
                new Interval(1, 3),
                new Interval(6, 7)
        ));

        // User 2
        schedules.add(Arrays.asList(
                new Interval(2, 4)
        ));

        // User 3
        schedules.add(Arrays.asList(
                new Interval(2, 5),
                new Interval(9, 12)
        ));

        int workingStart = 0;
        int workingEnd = 15;

        System.out.println("=== Pairwise Intersection Approach ===");
        List<Interval> res1 = obj.findCommonFreeTime_Intersection(schedules, workingStart, workingEnd);
        for (Interval in : res1) {
            System.out.println("[" + in.start + ", " + in.end + "]");
        }

        System.out.println("\n=== Line Sweep Approach ===");
        List<Interval> res2 = obj.findCommonFreeTime_LineSweep(schedules);
        for (Interval in : res2) {
            System.out.println("[" + in.start + ", " + in.end + "]");
        }
    }
}