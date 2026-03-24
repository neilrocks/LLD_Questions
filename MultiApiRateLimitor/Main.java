import java.util.*;
import java.util.concurrent.*;

// Represents a single rate limit rule (e.g., 10 req per 60 sec)
class RateLimitRule {

    int maxRequests;
    long windowSizeInMillis;

    public RateLimitRule(int maxRequests, long windowSizeInMillis) {
        this.maxRequests = maxRequests;
        this.windowSizeInMillis = windowSizeInMillis;
    }
}


// Key for storing counters
// Combines user + API/group + window start time
class CompositeKey {

    String userId;
    String key; // API or group
    long windowStart;

    public CompositeKey(String userId, String key, long windowStart) {
        this.userId = userId;
        this.key = key;
        this.windowStart = windowStart;
    }

    // Required for HashMap
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompositeKey)) return false;
        CompositeKey that = (CompositeKey) o;
        return windowStart == that.windowStart &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, key, windowStart);
    }
}


// Thread-safe counter store
class CounterStore {

    // ConcurrentHashMap for thread-safe access
    private ConcurrentHashMap<CompositeKey, AtomicInteger> counterMap = new ConcurrentHashMap<>();

    // Increment counter atomically
    // TC: O(1)
    public int increment(CompositeKey key) {

        counterMap.putIfAbsent(key, new AtomicInteger(0));

        return counterMap.get(key).incrementAndGet();
    }
}


// Configuration class holding all rules
class RateLimitConfig {

    // API -> List of rules (multi-window support)
    Map<String, List<RateLimitRule>> apiRules = new HashMap<>();

    // Group -> APIs mapping
    Map<String, List<String>> groups = new HashMap<>();

    // Group -> rules
    Map<String, List<RateLimitRule>> groupRules = new HashMap<>();

    public void addApiRule(String api, RateLimitRule rule) {
        apiRules.computeIfAbsent(api, k -> new ArrayList<>()).add(rule);
    }

    public void addGroup(String group, List<String> apis) {
        groups.put(group, apis);
    }

    public void addGroupRule(String group, RateLimitRule rule) {
        groupRules.computeIfAbsent(group, k -> new ArrayList<>()).add(rule);
    }
}


// Main Rate Limiter
class RateLimiter {

    private RateLimitConfig config;
    private CounterStore store;

    public RateLimiter(RateLimitConfig config) {
        this.config = config;
        this.store = new CounterStore();
    }

    // Check if request is allowed
    // TC: O(number of rules) -> typically small constant
    public boolean allowRequest(String userId, String api) {

        long currentTime = System.currentTimeMillis();

        // 1. Check API specific rules
        List<RateLimitRule> rules = config.apiRules.getOrDefault(api, new ArrayList<>());

        for (RateLimitRule rule : rules) {

            long windowStart = currentTime / rule.windowSizeInMillis;

            CompositeKey key = new CompositeKey(userId, api, windowStart);

            int count = store.increment(key);

            if (count > rule.maxRequests) {
                return false;
            }
        }

        // 2. Check group rules (aggregate limits)
        for (String group : config.groups.keySet()) {

            if (config.groups.get(group).contains(api)) {

                List<RateLimitRule> groupRuleList = config.groupRules.getOrDefault(group, new ArrayList<>());

                for (RateLimitRule rule : groupRuleList) {

                    long windowStart = currentTime / rule.windowSizeInMillis;

                    CompositeKey key = new CompositeKey(userId, group, windowStart);

                    int count = store.increment(key);

                    if (count > rule.maxRequests) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}


// Client code to simulate usage
public class Client {

    public static void main(String[] args) {

        RateLimitConfig config = new RateLimitConfig();

        // API rules
        config.addApiRule("/login", new RateLimitRule(5, 60_000)); // 5 req/min
        config.addApiRule("/search", new RateLimitRule(100, 60_000)); // 100 req/min

        // Multi-window rule
        config.addApiRule("/search", new RateLimitRule(500, 24 * 60 * 60 * 1000)); // 500/day

        // Group config
        config.addGroup("file_ops", Arrays.asList("/upload", "/download"));
        config.addGroupRule("file_ops", new RateLimitRule(50, 60 * 60 * 1000)); // 50/hour

        RateLimiter limiter = new RateLimiter(config);

        String user = "user1";

        // Simulate requests
        for (int i = 1; i <= 10; i++) {

            boolean allowed = limiter.allowRequest(user, "/login");

            System.out.println("Request " + i + " allowed: " + allowed);
        }
    }
}