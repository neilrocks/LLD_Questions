import java.util.*;

class ExpiryCounter<T> {
    private final long expiryTimeMillis;
    private final Map<T, List<Long>> elementTimestamps;

    public ExpiryCounter(long expiryTimeSeconds) {
        this.expiryTimeMillis = expiryTimeSeconds * 1000;
        this.elementTimestamps = new HashMap<>();
    }

    public synchronized void add(T element) {
        long currentTime = System.currentTimeMillis();
        elementTimestamps.computeIfAbsent(element, k -> new ArrayList<>()).add(currentTime);
        removeExpiredElements(); // optional global cleanup
    }

    public synchronized int getCount(T element) {
        cleanupKey(element);
        return elementTimestamps.getOrDefault(element, Collections.emptyList()).size();
    }

    public synchronized int getTotalElements() {
        removeExpiredElements();
    
        int total = 0;
        for (List<Long> timestamps : elementTimestamps.values()) {
            total += timestamps.size();
        }
        return total;
    }
    

    /**
     * Removes expired timestamps from the entire map using binary search.
     */
    private void removeExpiredElements() {
        long currentTime = System.currentTimeMillis();
        long threshold = currentTime - expiryTimeMillis;

        Iterator<Map.Entry<T, List<Long>>> iterator = elementTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<T, List<Long>> entry = iterator.next();
            List<Long> timestamps = entry.getValue();

            int firstValidIndex = findFirstValidTimestamp(timestamps, threshold);
            if (firstValidIndex == timestamps.size()) {
                iterator.remove(); // all expired
            } else if (firstValidIndex > 0) {
                timestamps.subList(0, firstValidIndex).clear(); // remove expired prefix
            }
        }
    }

    /**
     * Removes expired timestamps only for a specific key.
     */
    private void cleanupKey(T element) {
        long currentTime = System.currentTimeMillis();
        long threshold = currentTime - expiryTimeMillis;

        List<Long> timestamps = elementTimestamps.get(element);
        if (timestamps == null) return;

        int firstValidIndex = findFirstValidTimestamp(timestamps, threshold);
        if (firstValidIndex == timestamps.size()) {
            elementTimestamps.remove(element);
        } else if (firstValidIndex > 0) {
            timestamps.subList(0, firstValidIndex).clear();
        }
    }

    /**
     * Binary search: finds the first timestamp >= threshold.
     */
    private int findFirstValidTimestamp(List<Long> timestamps, long threshold) {
        int low = 0, high = timestamps.size() - 1, ans = timestamps.size();
        while (low <= high) {
            int mid = (low + high) / 2;
            if (timestamps.get(mid) >= threshold) {
                ans = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return ans; // index of first unexpired timestamp
    }

    public static void main(String[] args) throws InterruptedException {
        ExpiryCounter<String> counter = new ExpiryCounter<>(3); // expiry = 3 sec
        counter.add("apple");
        counter.add("apple");
        counter.add("banana");

        System.out.println(counter.getCount("apple")); // 2
        System.out.println(counter.getTotalElements()); // 3

        Thread.sleep(4000); // wait for 4s

        System.out.println(counter.getCount("apple")); // 0
        System.out.println(counter.getTotalElements()); // 0
    }
}
