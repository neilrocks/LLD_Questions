import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
enum UserType{
    FREE,
    PREMIUM
}
enum RateLimiterType{
    TOKEN,
    FIXED,
    SLIDING_WINDOW
}
class RateLimiterConfig{
    private int window;
    private int maxRequest;
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
abstract class RateLimiter{
    public RateLimiterConfig config;
    RateLimiter(RateLimiterConfig config){
        this.config=config;
    }
    abstract boolean allowRequest(User id);
}
// Concept:
// Each user has a bucket with a maximum number of tokens = maxRequest.
// Each request consumes 1 token.
// Tokens are refilled gradually over time at a fixed rate (maxRequest / window seconds).
// If tokens > 0 → request allowed, otherwise rejected.
// This algorithm allows short bursts while controlling long-term average rate.
class TokenBucketRateLimiter extends RateLimiter{
    Map<Integer,Integer>tokens=new ConcurrentHashMap<>();//userId VS number of tokens
    Map<Integer,Long> lastRefillTime=new HashMap<>();;//userId vs last time tokens were refilled for this user
    public TokenBucketRateLimiter(RateLimiterConfig config){
        super(config);
    }
    @Override
    boolean allowRequest(User user){
        final boolean[] allowed = {false};//in lambda expression variable should be final or effectively final
        long now=System.currentTimeMillis();
        tokens.compute(user.getId(),(k,v)->{
            int currentTokens=refillToke(user.getId(),now);
            if(currentTokens>0){
                allowed[0]=true;
                return currentTokens-1;
            }
            return currentTokens;
        });
        return allowed[0];
    }
    //timestamp 0,1,2,3,4,5,6,7,9,10,11,12,13,14, lets 14 is now and lastrefiltime was 2. Elapsed time is 14-2
    //refill rate = maxRequestAllowed/windowDurationInSec
    private int refillToke(int userId,long now){
        double refill_rate=(double)config.maxRequest()/(double)config.getWindow(); // e.g., 3/10 = 0.3 tokens per second
        long lastRefilTime=lastRefillTime.getOrDefault(userId, now);
        long elapsedSecond=(now-lastRefilTime)/1000;
        int refillTokens=(int)(elapsedSecond*refill_rate); // e.g., 10 seconds * 0.3 = 3 tokens
        int currentTokens=tokens.getOrDefault(userId,config.maxRequest());
        currentTokens=Math.min(config.maxRequest(),currentTokens+refillTokens);
        if(refillTokens>0) lastRefillTime.put(userId,now);
        return currentTokens;
    }
}
// Concept:
// Divide time into fixed-size windows (e.g., every 10 seconds).
// Count how many requests a user made in the current window.
// If count < maxRequest → allow; else reject.
// When time moves to next window, reset count to 1.
// Simple but allows bursts at window boundaries.
class FixedWindowRateLimiter extends RateLimiter{
    Map<Integer,Integer>userIdVsRequestCount=new ConcurrentHashMap<>();//userId vs number of requests in current window
    Map<Integer,Long>userIdVsWindowStartTime=new HashMap<>();//userId vs last window start time
    public FixedWindowRateLimiter(RateLimiterConfig config){
        super(config);
    }
    @Override
    boolean allowRequest(User user){
        final boolean[] allowed = {false};
        long now=System.currentTimeMillis()/1000;
        userIdVsRequestCount.compute(user.getId(),(userId,reqCount)->{
            long windowStartTime=userIdVsWindowStartTime.getOrDefault(userId,now);
            if(now-windowStartTime>=config.getWindow()){
                //new window
                userIdVsWindowStartTime.put(userId,now);
                allowed[0]=true;
                return 1;//first request in new window
            }else{
                //same window
                if(reqCount<config.maxRequest()){
                    allowed[0]=true;
                    return reqCount+1;
                }else{
                    return reqCount;
                }
            }
        });
        return allowed[0];
    }
}
// Concept:
// Maintain a sliding window of recent request timestamps for each user.
// For each new request:
//   1. Remove timestamps older than (now - window).
//   2. If remaining count < maxRequest → allow and record current time.
//   3. Else reject.
// Provides smoother rate limiting and prevents boundary bursts.
class SlidingWindowRateLimiter extends RateLimiter{
    Map<Integer,Queue<Long>> userIdVsTimestamps=new ConcurrentHashMap<>();//userId vs timestamps of requests
    public SlidingWindowRateLimiter(RateLimiterConfig config){
        super(config);
    }
    @Override
    boolean allowRequest(User user){
        final boolean[] allowed = {false};
        long now=System.currentTimeMillis()/1000;
        userIdVsTimestamps.compute(user.getId(),(id,log)->{
            if(log==null) log=new ArrayDeque<>();
            //remove timestamps which are out of the window
            while(!log.isEmpty() && now-log.peek()>=config.getWindow()){
                log.poll();
            }
            if(log.size()<config.maxRequest()){
                allowed[0]=true;
                log.add(now);
            }
            return log;
        });
        return allowed[0];
    }
}
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
class RateLimiterService{
    private static RateLimiterService instance;
    private RateLimiterType type;
    private Map<Integer,RateLimiter>idVsLimiter;
    private RateLimiterService(){

    }
    public static RateLimiterService getInstance(){
        if(instance==null){
            instance=new RateLimiterService();
        }
        return instance;
    }
    public void addClient(User user,RateLimiterConfig config){
        RateLimiter limiter=RateLimiterFactory.createLimiter(this.type, config);
        idVsLimiter.put(user.getId(),limiter);
    }
    public boolean allowRequest(User user){
        RateLimiter limiter=idVsLimiter.get(user.getId());
        return limiter.allowRequest(user);

    }
    public void setAlgo(RateLimiterType type){
        this.type=type;
    }
}
public class Main{
    public static void main(String[] args) throws InterruptedException {
        RateLimiterService service=RateLimiterService.getInstance();
        RateLimiterConfig userAConfig=new RateLimiterConfig(10, 3);
        RateLimiterConfig userBConfig=new RateLimiterConfig(10, 100);
        service.setAlgo(RateLimiterType.TOKEN);

        User userA=new User(1,UserType.FREE);
        User userB=new User(2,UserType.PREMIUM);

        service.addClient(userA, userAConfig);
        service.addClient(userB, userBConfig);

        for (int i = 1; i <= 5; i++) {
            boolean res = service.allowRequest(userA);
            System.out.println("Request " + i + " from userA allowed? " + res);
            Thread.sleep(1000); // simulate 1s between requests
        }
    }
}