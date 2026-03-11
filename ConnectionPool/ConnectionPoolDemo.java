package ConnectionPool;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/*
============================ REQUIREMENTS ============================

Functional Requirements
1. Maintain a pool of reusable database connections.
2. A client should be able to request a connection from the pool.
3. If a connection is available → return immediately.
4. If no connection is available → wait for a configurable timeout.
5. When the client finishes work → connection must be returned to the pool.
6. If a connection is closed/broken → pool should recreate it.
7. Multiple threads should be able to safely access the pool concurrently.

Non Functional Requirements
1. Thread-safe access to the pool.
2. Efficient concurrent access (avoid locking the entire pool).
3. Maintain a fixed maximum number of connections.
4. Prevent connection leaks.

============================ CORE ENTITIES ============================

1. Connection
   Represents a single database connection.

2. ConnectionState
   Enum representing the lifecycle of a connection.

3. ConnectionPool
   Manages all connections, handles allocation and release.

4. Client (Developer threads in demo)
   Requests and uses connections from the pool.

=======================================================================
*/

enum ConnectionState {
    FREE,      // connection available in pool
    BLOCKED,   // connection currently used by a client
    CLOSED     // connection is broken/closed
}

class Connection {

    // Unique identifier for the connection
    private final int id;

    // Current state of the connection
    private ConnectionState state;

    public Connection(int id) {
        this.id = id;
        this.state = ConnectionState.FREE;
    }

    public int getId() {
        return id;
    }

    // synchronized ensures multiple threads do not read/write state concurrently
    public synchronized ConnectionState getState() {
        return state;
    }

    // synchronized ensures state change is thread safe
    public synchronized void setState(ConnectionState state) {
        this.state = state;
    }

    // Simulate using the connection
    public void use() {
        System.out.println("Using connection: " + id);
    }

    // Simulate closing/breaking the connection
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

    // Maximum number of connections allowed in the pool
    private final int maxSize;

    // Timeout while waiting for a free connection
    private final long DEFAULT_TIMEOUT_MS = 3000;

    /*
    BlockingQueue provides thread-safe queue operations.

    freeConnections → holds only FREE connections.
    Threads calling getConnection() will poll from this queue.
    */
    private final BlockingQueue<Connection> freeConnections;

    /*
    Track all connections created by the pool.
    Even if a connection is currently in use, it still exists here.

    This helps in monitoring, recreation, and future extensions.
    */
    private final Set<Connection> allConnections = ConcurrentHashMap.newKeySet();

    // Keeps track of total number of connections ever created
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    public ConnectionPool(int size) {
        this.maxSize = size;

        // queue capacity equals pool size
        this.freeConnections = new LinkedBlockingQueue<>(size);

        initializePool(size);
    }

    /*
    Pre-create connections during pool initialization.

    This avoids the cost of creating connections at runtime.
    */
    private void initializePool(int size) {
        for (int i = 0; i < size; i++) {

            Connection conn = new Connection(i + 1);

            // track connection globally
            allConnections.add(conn);

            // add to free queue
            freeConnections.offer(conn);

            totalConnections.incrementAndGet();
        }
    }

    /*
    Called by clients to acquire a connection.

    Steps:
    1. Try to poll from free queue.
    2. Wait up to DEFAULT_TIMEOUT_MS if none available.
    3. If still null → throw timeout exception.
    */
    public Connection getConnection() throws InterruptedException {

        Connection conn = freeConnections.poll(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        if (conn == null) {
            throw new RuntimeException("Timeout! No available connections in pool.");
        }

        /*
        Lock only this specific connection object.

        This avoids locking the entire pool and allows
        other threads to acquire other connections simultaneously.
        */
        synchronized (conn) {

            /*
            If the connection was closed by a client,
            we recreate a new connection with the same id.
            */
            if (conn.getState() == ConnectionState.CLOSED) {

                System.out.println("Recreating closed connection: " + conn.getId());

                Connection newConn = new Connection(conn.getId());

                // update tracking set
                allConnections.remove(conn);
                allConnections.add(newConn);

                conn = newConn;
            }

            // mark connection as in use
            conn.setState(ConnectionState.BLOCKED);
        }

        return conn;
    }

    /*
    Client calls this method after finishing work.

    Steps:
    1. If connection is closed → recreate it.
    2. Otherwise mark it FREE.
    3. Put it back in the free queue.
    */
    public void releaseConnection(Connection conn) {

        synchronized (conn) {

            /*
            If the connection is closed/broken,
            replace it with a fresh connection.
            */
            if (conn.getState() == ConnectionState.CLOSED) {

                Connection newConn = new Connection(conn.getId());

                allConnections.remove(conn);
                allConnections.add(newConn);

                freeConnections.offer(newConn);

            } else {

                // mark connection as free
                conn.setState(ConnectionState.FREE);

                // return to pool
                freeConnections.offer(conn);
            }
        }
    }

    // Number of currently available connections
    public int getFreeCount() {
        return freeConnections.size();
    }

    // Total connections created by pool
    public int getTotalConnections() {
        return totalConnections.get();
    }
}

public class ConnectionPoolDemo {

    public static void main(String[] args) {

        // Create pool with 5 connections
        ConnectionPool pool = new ConnectionPool(5);

        // Simulate 10 developers requesting connections
        ExecutorService executor = Executors.newFixedThreadPool(10);

        for (int i = 0; i < 10; i++) {

            final int devId = i + 1;

            executor.submit(() -> {
                try {

                    System.out.println("Dev " + devId + " requesting connection...");

                    // request connection from pool
                    Connection conn = pool.getConnection();

                    // simulate using it
                    conn.use();

                    Thread.sleep(1000);

                    /*
                    Randomly close some connections
                    to simulate real world failures.
                    */
                    if (devId % 4 == 0) {
                        conn.close();
                    }

                    // release back to pool
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

/*
======================= DESIGN EXPLANATION =======================

Why synchronized(conn)?

Lock is applied per connection instead of the entire pool.

Example:

Thread A → using connection C1
Thread B → using connection C2

Both threads can operate in parallel safely.

Only if two threads try modifying the SAME connection,
the synchronization prevents race conditions.

So we achieve:
Thread Safety + High Concurrency.

-------------------------------------------------------------------

Why both freeConnections and allConnections?

Action                         freeConnections        allConnections

Connection handed to client    Removed                Still present
Connection released            Added back             Already exists
Connection closed              Replaced               Old removed, new added

So:

freeConnections → availability queue
allConnections  → full registry of pool connections

This design helps maintain a fixed pool size and allows
future features like:

• health check thread
• connection validation
• monitoring / metrics
*/