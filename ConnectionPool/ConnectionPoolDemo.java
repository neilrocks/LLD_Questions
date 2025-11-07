package ConnectionPool;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

enum ConnectionState {
    FREE,
    BLOCKED,
    CLOSED
}

class Connection {
    private final int id;
    private ConnectionState state;

    public Connection(int id) {
        this.id = id;
        this.state = ConnectionState.FREE;
    }

    public int getId() {
        return id;
    }

    public synchronized ConnectionState getState() {
        return state;
    }

    public synchronized void setState(ConnectionState state) {
        this.state = state;
    }

    public void use() {
        System.out.println("Using connection: " + id);
    }

    public void close() {
        System.out.println("Closing connection: " + id);
        setState(ConnectionState.CLOSED);
    }

    @Override
    public String toString() {
        return "Connection{" + "id=" + id + ", state=" + state + '}';
    }
}

class ConnectionPool {
    private final int maxSize;
    private final long DEFAULT_TIMEOUT_MS = 3000; // configurable
    private final BlockingQueue<Connection> freeConnections;
    private final Set<Connection> allConnections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    public ConnectionPool(int size) {
        this.maxSize = size;
        this.freeConnections = new LinkedBlockingQueue<>(size);
        initializePool(size);
    }

    private void initializePool(int size) {
        for (int i = 0; i < size; i++) {
            Connection conn = new Connection(i + 1);
            allConnections.add(conn);
            freeConnections.offer(conn);
            totalConnections.incrementAndGet();
        }
    }

    public Connection getConnection() throws InterruptedException {
        Connection conn = freeConnections.poll(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (conn == null) {
            throw new RuntimeException("Timeout! No available connections in pool.");
        }

        synchronized (conn) {
            if (conn.getState() == ConnectionState.CLOSED) {
                // recreate and replace
                System.out.println("Recreating closed connection: " + conn.getId());
                Connection newConn = new Connection(conn.getId());
                allConnections.remove(conn);
                allConnections.add(newConn);
                conn = newConn;
            }
            conn.setState(ConnectionState.BLOCKED);
        }
        return conn;
    }

    public void releaseConnection(Connection conn) {
        synchronized (conn) {
            if (conn.getState() == ConnectionState.CLOSED) {
                // recreate and put back
                Connection newConn = new Connection(conn.getId());
                allConnections.remove(conn);
                allConnections.add(newConn);
                freeConnections.offer(newConn);
            } else {
                conn.setState(ConnectionState.FREE);
                freeConnections.offer(conn);
            }
        }
    }

    public int getFreeCount() {
        return freeConnections.size();
    }

    public int getTotalConnections() {
        return totalConnections.get();
    }
}

public class ConnectionPoolDemo {
    public static void main(String[] args) {
        ConnectionPool pool = new ConnectionPool(5); // small for demo

        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 10; i++) {
            final int devId = i + 1;
            executor.submit(() -> {
                try {
                    System.out.println("Dev " + devId + " requesting connection...");
                    Connection conn = pool.getConnection();
                    conn.use();
                    Thread.sleep(1000);

                    if (devId % 4 == 0) { // simulate a few closing
                        conn.close();
                    }

                    pool.releaseConnection(conn);
                    System.out.println("Dev " + devId + " released connection.");
                } catch (Exception e) {
                    System.out.println("Dev " + devId + " failed: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
    }
}

// ✅ With synchronized(conn)

// Now the lock is per-connection, not on the whole pool.

// Thread A and Thread B can both call getConnection() simultaneously.

// If they get two different Connection objects (say C1, C2), both can safely update their states in parallel.

// But if both try to modify the same connection (rare, but possible), synchronization ensures only one does so at a time.

// So we get thread-safety + concurrency.

// Why both are needed
// Action	freeConnections	allConnections
// When a connection is given out	Removed from queue	Still present (state = BLOCKED)
// When a connection is released	Added back	Already exists
// When a connection is closed	Replaced (new object added)	Old one removed, new one added

// So:

// freeConnections = active availability queue

// allConnections = complete tracking registry

// This design helps the pool maintain a constant total number of connections and allows future features like monitoring or validation threads.