/*
================================================================================
RATE LIMITER - LOW LEVEL DESIGN
================================================================================

PROBLEM STATEMENT:
Design a Rate Limiter system that controls the rate of requests from users to prevent 
abuse and ensure fair usage of resources. The system should support multiple rate 
limiting algorithms and different limits for different user types.

REQUIREMENTS:

Functional Requirements:
1. Support multiple rate limiting algorithms:
   - Token Bucket: Allows burst traffic while maintaining average rate
   - Fixed Window: Simple counter reset at fixed intervals
   - Sliding Window: Smooth rate limiting without boundary issues
2. Different rate limits for different users (e.g., Free vs Premium)
3. Per-user rate limiting (each user has independent limits)
4. Allow/reject requests based on current rate limit state
5. Configurable window duration and max requests per window

Non-Functional Requirements:
1. Thread-safe: Handle concurrent requests from multiple users
2. Low latency: Quick decision on allow/reject (<1ms)
3. Memory efficient: Store only necessary data per user
4. Scalable: Support millions of users
5. Flexible: Easy to add new rate limiting algorithms

CORE ENTITIES:

1. User
   - Represents a client making requests
   - Has unique ID and type (FREE/PREMIUM)
   - Different types can have different rate limits

2. RateLimiterConfig
   - Configuration for rate limiting behavior
   - Contains: window duration (seconds) and max requests allowed
   - Can be customized per user

3. RateLimiter (Abstract)
   - Base class for all rate limiting algorithms
   - Defines contract: allowRequest(User) -> boolean
   - Each algorithm implements its own logic

4. RateLimiterService (Singleton)
   - Central service to manage all user rate limiters
   - Maps each user to their specific rate limiter instance
   - Factory pattern to create appropriate rate limiter type

DESIGN PATTERNS USED:
1. Strategy Pattern: Different rate limiting algorithms (Token/Fixed/Sliding)
2. Factory Pattern: RateLimiterFactory creates appropriate limiter
3. Singleton Pattern: RateLimiterService ensures single instance
4. Template Method: Abstract RateLimiter with common config

RATE LIMITING ALGORITHMS COMPARISON:

┌─────────────────┬────────────────────┬──────────────────┬──────────────────┐
│   Algorithm     │   Pros             │   Cons           │   Use Case       │
├─────────────────┼────────────────────┼──────────────────┼──────────────────┤
│ Token Bucket    │ Allows bursts      │ More complex     │ API rate limits  │
│                 │ Smooth long-term   │ Memory overhead  │ with burst needs │
├─────────────────┼────────────────────┼──────────────────┼──────────────────┤
│ Fixed Window    │ Simple & fast      │ Boundary bursts  │ Simple quotas    │
│                 │ Low memory         │ 2x spike risk    │ Less strict      │
├─────────────────┼────────────────────┼──────────────────┼──────────────────┤
│ Sliding Window  │ No boundary issue  │ More memory      │ Strict limits    │
│                 │ Accurate           │ Slower lookups   │ Premium APIs     │
└─────────────────┴────────────────────┴──────────────────┴──────────────────┘

TIME COMPLEXITY:
- Token Bucket: O(1) per request
- Fixed Window: O(1) per request
- Sliding Window: O(n) where n = requests in window, typically O(1) amortized

SPACE COMPLEXITY:
- Token Bucket: O(u) where u = number of users (2 maps)
- Fixed Window: O(u) where u = number of users (2 maps)
- Sliding Window: O(u * r) where u = users, r = requests per window

INTERVIEW TALKING POINTS:
1. "I'm using ConcurrentHashMap for thread-safety in multi-threaded environments"
2. "Token bucket allows controlled bursts - good for real-world API usage patterns"
3. "Fixed window has 2x spike vulnerability at boundaries - explain with example"
4. "Sliding window is most accurate but has higher memory footprint"
5. "Using compute() ensures atomic read-modify-write operations"
6. "Factory pattern makes it easy to add new algorithms without changing client code"
7. "In production, we'd use distributed cache like Redis for multi-server deployment"

EXTENSIONS/IMPROVEMENTS:
1. Distributed Rate Limiting: Use Redis with atomic operations
2. Rate Limit Headers: Return X-RateLimit-Remaining, X-RateLimit-Reset
3. Soft vs Hard Limits: Warning before actual blocking
4. Dynamic Rate Limits: Adjust based on system load
5. User Quotas: Daily/monthly limits alongside per-second limits
6. Whitelist/Blacklist: Bypass rate limiting for certain users
7. Leaky Bucket: Alternative algorithm with constant outflow rate

================================================================================
*/

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// ENUMS: Define types for extensibility

// UserType: Different user tiers with potentially different rate limits
enum UserType{
    FREE,
    PREMIUM
}

// RateLimiterType: Supported rate limiting algorithms
enum RateLimiterType{
    TOKEN,           // Token Bucket - allows bursts
    FIXED,           // Fixed Window - simple counter
    SLIDING_WINDOW   // Sliding Window - most accurate
}

// CONFIGURATION: Encapsulates rate limit settings
// Example: window=10, maxRequest=5 means "5 requests per 10 seconds"
class RateLimiterConfig{
    private int window;        // Time window in seconds
    private int maxRequest;    // Maximum requests allowed in the window
    
    RateLimiterConfig(int window,int maxRequest){
        this.window=window;
        this.maxRequest=maxRequest;
    }
    public int getWindow(){
        return this.window;
    }
    public int maxRequest(){
        return this.maxRequest;
    }
}

// USER: Represents a client making requests
class User{
    private int id;
    private UserType type;
    
    User(int id,UserType type){
        this.id=id;
        this.type=type;
    }
    public int getId(){
        return this.id;
    }
    public UserType getType(){
        return type;
    }
}

// ABSTRACT BASE: Template for all rate limiting strategies
abstract class RateLimiter{
    public RateLimiterConfig config;
    
    RateLimiter(RateLimiterConfig config){
        this.config=config;
    }
    
    // Core method: Returns true if request allowed, false if rate limited
    abstract boolean allowRequest(User id);
}

// ALGORITHM 1: TOKEN BUCKET
// Interview Note: "This is like a bucket that holds tokens. Each request needs 1 token.
// Tokens are added continuously at a fixed rate. Allows bursts if bucket is full,
// but maintains average rate over time. Used by AWS, Stripe, Shopify."
//
// Concept:
// Each user has a bucket with a maximum number of tokens = maxRequest.
// Each request consumes 1 token.
// Tokens are refilled gradually over time at a fixed rate (maxRequest / window seconds).
// If tokens > 0 → request allowed, otherwise rejected.
// This algorithm allows short bursts while controlling long-term average rate.
class TokenBucketRateLimiter extends RateLimiter{
    // ConcurrentHashMap for thread-safety - multiple threads can access simultaneously
    Map<Integer,Integer>tokens=new ConcurrentHashMap<>();        // userId -> current token count
    Map<Integer,Long> lastRefillTime=new HashMap<>();            // userId -> last refill timestamp
    
    public TokenBucketRateLimiter(RateLimiterConfig config){
        super(config);
    }
    
    @Override
    boolean allowRequest(User user){
        final boolean[] allowed = {false}; // Must be final/effectively final for lambda
        long now=System.currentTimeMillis();
        
        // compute() provides atomic read-modify-write operation
        tokens.compute(user.getId(),(k,v)->{
            int currentTokens=refillToke(user.getId(),now);
            if(currentTokens>0){
                allowed[0]=true;
                return currentTokens-1;  // Consume 1 token
            }
            return currentTokens;        // No tokens, reject request
        });
        return allowed[0];
    }
    
    // Refills tokens based on elapsed time since last refill
    // Interview Note: "Calculate how many tokens to add based on time passed.
    // refill_rate = seconds per token. If 3 requests per 10 seconds, then 10/3 = 3.33 seconds per token."
    private int refillToke(int userId,long now){
        // Calculate refill rate: seconds per token
        double refill_rate=(double)config.getWindow()/(double)config.maxRequest();
        
        long lastRefilTime=lastRefillTime.getOrDefault(userId, now);
        long elapsedSecond=(now-lastRefilTime)/1000;
        
        // Calculate tokens to add: elapsed time / seconds per token
        int refillTokens=(int)(elapsedSecond/refill_rate);
        
        // Get current tokens (start with full bucket for new users)
        int currentTokens=tokens.getOrDefault(userId,config.maxRequest());
        
        // Add refilled tokens but cap at max capacity
        currentTokens=Math.min(config.maxRequest(),currentTokens+refillTokens);
        
        // Update last refill time only if we actually refilled
        if(refillTokens>0) lastRefillTime.put(userId,now);
        
        return currentTokens;
    }
}

// ALGORITHM 2: FIXED WINDOW
// Interview Note: "Simplest approach. Time divided into fixed windows. Count requests per window.
// Problem: 2x spike at boundaries - if user makes maxRequest at end of window1 and 
// maxRequest at start of window2, they get 2x limit in short time span."
//
// Concept:
// Divide time into fixed-size windows (e.g., every 10 seconds).
// Count how many requests a user made in the current window.
// If count < maxRequest → allow; else reject.
// When time moves to next window, reset count to 1.
// Simple but allows bursts at window boundaries.
class FixedWindowRateLimiter extends RateLimiter{
    Map<Integer,Integer>userIdVsRequestCount=new ConcurrentHashMap<>();    // userId -> request count
    Map<Integer,Long>userIdVsWindowStartTime=new HashMap<>();              // userId -> window start time
    
    public FixedWindowRateLimiter(RateLimiterConfig config){
        super(config);
    }
    
    @Override
    boolean allowRequest(User user){
        final boolean[] allowed = {false};
        long now=System.currentTimeMillis()/1000;  // Convert to seconds
        
        userIdVsRequestCount.compute(user.getId(),(userId,reqCount)->{
            long windowStartTime=userIdVsWindowStartTime.getOrDefault(userId,now);
            
            // Check if we've moved to a new window
            if(now-windowStartTime>=config.getWindow()){
                // New window - reset counter
                userIdVsWindowStartTime.put(userId,now);
                allowed[0]=true;
                return 1;  // First request in new window
            }else{
                // Same window - check if under limit
                if(reqCount<config.maxRequest()){
                    allowed[0]=true;
                    return reqCount+1;  // Increment counter
                }else{
                    return reqCount;     // At limit, reject
                }
            }
        });
        return allowed[0];
    }
}

// ALGORITHM 3: SLIDING WINDOW
// Interview Note: "Most accurate but memory intensive. Stores timestamp of each request.
// Continuously slides the window forward. No boundary burst issue. Used when accuracy matters more than memory."
//
// Concept:
// Maintain a sliding window of recent request timestamps for each user.
// For each new request:
//   1. Remove timestamps older than (now - window).
//   2. If remaining count < maxRequest → allow and record current time.
//   3. Else reject.
// Provides smoother rate limiting and prevents boundary bursts.
class SlidingWindowRateLimiter extends RateLimiter{
    Map<Integer,Queue<Long>> userIdVsTimestamps=new ConcurrentHashMap<>();  // userId -> queue of timestamps
    
    public SlidingWindowRateLimiter(RateLimiterConfig config){
        super(config);
    }
    
    @Override
    boolean allowRequest(User user){
        final boolean[] allowed = {false};
        long now=System.currentTimeMillis()/1000;  // Convert to seconds
        
        userIdVsTimestamps.compute(user.getId(),(id,log)->{
            if(log==null) log=new ArrayDeque<>();
            
            // Clean up old timestamps outside the sliding window
            while(!log.isEmpty() && now-log.peek()>=config.getWindow()){
                log.poll();
            }
            
            // Check if under limit in current window
            if(log.size()<config.maxRequest()){
                allowed[0]=true;
                log.add(now);  // Record this request timestamp
            }
            return log;
        });
        return allowed[0];
    }
}

// FACTORY: Creates appropriate rate limiter based on type
// Interview Note: "Factory pattern makes it easy to add new algorithms.
// Client code doesn't need to know about concrete implementations."
class RateLimiterFactory {
    public static RateLimiter createLimiter(RateLimiterType type, RateLimiterConfig config) {
        switch (type) {
            case TOKEN:
                return new TokenBucketRateLimiter(config);
            case FIXED:
                return new FixedWindowRateLimiter(config);
            case SLIDING_WINDOW:
                return new SlidingWindowRateLimiter(config);
            default:
                throw new IllegalArgumentException("Unknown rate limiter type");
        }
    }
}

// SERVICE: Central manager for all rate limiters (Singleton)
// Interview Note: "In production, this would be distributed using Redis.
// Each user gets their own rate limiter instance with custom config."
class RateLimiterService{
    private static RateLimiterService instance;
    private RateLimiterType type;
    private Map<Integer,RateLimiter>idVsLimiter=new HashMap<>();  // userId -> RateLimiter instance
    
    private RateLimiterService(){
        // Private constructor for Singleton
    }
    
    public static RateLimiterService getInstance(){
        if(instance==null){
            instance=new RateLimiterService();
        }
        return instance;
    }
    
    // Register a user with their specific rate limit configuration
    public void addClient(User user,RateLimiterConfig config){
        RateLimiter limiter=RateLimiterFactory.createLimiter(this.type, config);
        idVsLimiter.put(user.getId(),limiter);
    }
    
    // Check if user's request is allowed
    public boolean allowRequest(User user){
        RateLimiter limiter=idVsLimiter.get(user.getId());
        return limiter.allowRequest(user);
    }
    
    // Set which algorithm to use for new clients
    public void setAlgo(RateLimiterType type){
        this.type=type;
    }
}

// DEMO: Shows rate limiter in action
public class Main{
    public static void main(String[] args) throws InterruptedException {
        // Initialize service
        RateLimiterService service=RateLimiterService.getInstance();
        
        // Configure different limits for different users
        RateLimiterConfig userAConfig=new RateLimiterConfig(10, 3);    // 3 requests per 10 seconds
        RateLimiterConfig userBConfig=new RateLimiterConfig(10, 100);  // 100 requests per 10 seconds
        
        service.setAlgo(RateLimiterType.TOKEN);

        User userA=new User(1,UserType.FREE);
        User userB=new User(2,UserType.PREMIUM);

        service.addClient(userA, userAConfig);
        service.addClient(userB, userBConfig);

        // Test rate limiting
        for (int i = 1; i <= 5; i++) {
            boolean res = service.allowRequest(userA);
            System.out.println("Request " + i + " from userA allowed? " + res);
            Thread.sleep(1000); // simulate 1s between requests
        }
    }
}