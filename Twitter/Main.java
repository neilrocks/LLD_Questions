import java.util.*;

// Design Twitter: Support posting tweets, following/unfollowing users, and viewing news feed
// Key Design: Linked list for tweets per user + Priority Queue for merging feeds

class Tweet {
    int tweetId;
    int time;           // timestamp for ordering
    Tweet next;         // linked list of user's tweets (newest first)

    public Tweet(int tweetId, int time) {
        this.tweetId = tweetId;
        this.time = time;
    }
}

class User {
    int userId;
    Set<Integer> followees;    // users this user follows (including self)
    Tweet tweetHead;           // head of user's tweet linked list

    public User(int userId) {
        this.userId = userId;
        followees = new HashSet<>();
        follow(userId); // Important: user follows themselves to see own tweets in feed
    }

    public void follow(int id) {
        followees.add(id);
    }

    public void unfollow(int id) {
        if (id != userId)  // Cannot unfollow yourself
            followees.remove(id);
    }

    public void post(int tweetId, int time) {
        Tweet t = new Tweet(tweetId, time);
        t.next = tweetHead;    // Insert at head (most recent)
        tweetHead = t;
    }
}

class Twitter {

    private static int timeStamp = 0;       // Global timestamp to order tweets
    Map<Integer, User> users;               // userId -> User object

    public Twitter() {
        users = new HashMap<>();
    }

    // Lazy initialization: create user if doesn't exist
    private User getUser(int userId) {
        users.putIfAbsent(userId, new User(userId));
        return users.get(userId);
    }

    public void postTweet(int userId, int tweetId) {
        User user = getUser(userId);
        user.post(tweetId, timeStamp++);    // Auto-increment timestamp
    }

    // Return 10 most recent tweets from user's feed (self + followees)
    public List<Integer> getNewsFeed(int userId) {
        List<Integer> res = new ArrayList<>();

        if (!users.containsKey(userId))
            return res;

        Set<Integer> followees = users.get(userId).followees;

        // Max heap by time: merge K sorted linked lists (each user's tweets)
        PriorityQueue<Tweet> pq =
            new PriorityQueue<>((a, b) -> b.time - a.time);

        // Add most recent tweet from each followee
        for (int id : followees) {
            Tweet tweet = users.get(id).tweetHead;
            if (tweet != null)
                pq.offer(tweet);
        }

        int count = 0;

        // Extract top 10 most recent tweets
        while (!pq.isEmpty() && count < 10) {
            Tweet t = pq.poll();
            res.add(t.tweetId);
            count++;

            if (t.next != null)     // Add next tweet from same user
                pq.offer(t.next);
        }

        return res;
    }

    public void follow(int followerId, int followeeId) {
        User follower = getUser(followerId);
        getUser(followeeId);                    // Ensure followee exists
        follower.follow(followeeId);
    }

    public void unfollow(int followerId, int followeeId) {
        if (!users.containsKey(followerId))
            return;
        users.get(followerId).unfollow(followeeId);
    }
}
//Client Code
public static void main(String[] args) {
    Twitter twitter = new Twitter();

    twitter.postTweet(1, 5);
    System.out.println(twitter.getNewsFeed(1)); // [5]

    twitter.follow(1, 2);
    twitter.postTweet(2, 6);
    System.out.println(twitter.getNewsFeed(1)); // [6, 5]

    twitter.unfollow(1, 2);
    System.out.println(twitter.getNewsFeed(1)); // [5]
}