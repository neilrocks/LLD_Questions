// ============================================================
//  LOGGER - Low Level Design (Java)
//  Patterns Used:
//    1. Singleton       → Logger (one global instance)
//    2. Chain of Resp.  → LogHandler chain (Debug→Info→Warn)
//    3. Observer        → LogHandler notifies LogAppenders
//    4. Strategy        → LogFormatter (Plain vs JSON)
//
//  Concurrency handled via:
//    - volatile + double-checked locking  (Singleton)
//    - CopyOnWriteArrayList               (Observer list)
//    - synchronized append()              (Console & File writes)
// ============================================================


// ─────────────────────────────────────────────
// 1. ENUM: LogLevel
//    Defines severity levels for log messages.
// ─────────────────────────────────────────────
enum LogLevel {
    DEBUG, INFO, WARN
}


// ─────────────────────────────────────────────
// 2. MODEL: LogMessage
//    Immutable value object passed through the system.
//    Captures level, message text, and auto-timestamp.
// ─────────────────────────────────────────────
class LogMessage {
    private final LogLevel level;
    private final String msg;
    private final long timestamp;

    public LogMessage(LogLevel level, String msg) {
        this.level     = level;
        this.msg       = msg;
        this.timestamp = System.currentTimeMillis();
    }

    public LogLevel getLevel()     { return level; }
    public String   getMsg()       { return msg; }
    public long     getTimestamp() { return timestamp; }
}


// ─────────────────────────────────────────────
// 3. STRATEGY INTERFACE: LogFormatter
//    Defines how a LogMessage is converted to a String.
//    Concrete strategies: PlainTxt, JSON.
// ─────────────────────────────────────────────
interface LogFormatter {
    String formatMsg(LogMessage msg);
}


// ─────────────────────────────────────────────
// 3a. STRATEGY: PlainTxtFormatter
//     Human-readable output. Good for console.
// ─────────────────────────────────────────────
class PlainTxtFormatter implements LogFormatter {
    @Override
    public String formatMsg(LogMessage msg) {
        return "[" + msg.getLevel() + "] "
             + msg.getTimestamp() + " - "
             + msg.getMsg();
    }
}


// ─────────────────────────────────────────────
// 3b. STRATEGY: JsonFormatter
//     Structured output. Good for log files / parsing.
// ─────────────────────────────────────────────
class JsonFormatter implements LogFormatter {
    @Override
    public String formatMsg(LogMessage msg) {
        return String.format(
            "{\"level\":\"%s\", \"timestamp\":%d, \"msg\":\"%s\"}",
            msg.getLevel(), msg.getTimestamp(), msg.getMsg()
        );
    }
}


// ─────────────────────────────────────────────
// 4. OBSERVER INTERFACE: LogAppender
//    Any class that wants to receive log messages
//    must implement this. Acts as the Observer.
// ─────────────────────────────────────────────
interface LogAppender {
    void append(LogMessage msg);
}


// ─────────────────────────────────────────────
// 4a. CONCRETE OBSERVER: ConsoleAppender
//     Prints formatted log to stdout.
//     'synchronized' ensures thread-safe console writes.
// ─────────────────────────────────────────────
class ConsoleAppender implements LogAppender {
    private final LogFormatter formatter;

    public ConsoleAppender(LogFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public synchronized void append(LogMessage msg) {
        // synchronized → only one thread prints at a time (no interleaved output)
        System.out.println(formatter.formatMsg(msg));
    }
}


// ─────────────────────────────────────────────
// 4b. CONCRETE OBSERVER: FileAppender
//     Writes formatted log to a file.
//     'synchronized' prevents concurrent write corruption.
//     Opens in append mode (true) so logs accumulate.
// ─────────────────────────────────────────────
class FileAppender implements LogAppender {
    private final LogFormatter formatter;
    private final String filePath;

    public FileAppender(LogFormatter formatter, String filePath) {
        this.formatter = formatter;
        this.filePath  = filePath;
    }

    @Override
    public synchronized void append(LogMessage msg) {
        // synchronized → no two threads write to the file simultaneously
        try (java.io.FileWriter fw     = new java.io.FileWriter(filePath, true);
             java.io.BufferedWriter bw = new java.io.BufferedWriter(fw)) {
            bw.write(formatter.formatMsg(msg));
            bw.newLine();
        } catch (java.io.IOException e) {
            System.err.println("FileAppender error: " + e.getMessage());
        }
    }
}


// ─────────────────────────────────────────────
// 5. ABSTRACT: LogHandler   (Observable + Chain node)
//
//    TWO responsibilities:
//      a) Chain of Responsibility → each handler decides
//         if it should handle the message (canHandle),
//         then passes it to 'next' regardless.
//      b) Observer pattern → if canHandle=true,
//         notifies all subscribed LogAppenders.
//
//    CopyOnWriteArrayList → thread-safe observer list.
//    Reads are fast (no locking); writes copy the array.
//    Perfect when subscribe() is rare but notify is frequent.
// ─────────────────────────────────────────────
abstract class LogHandler {
    protected LogHandler next;

    // CopyOnWriteArrayList: thread-safe without explicit locking on reads
    protected java.util.List<LogAppender> observers =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    // Link to next handler in the chain
    public void setNext(LogHandler next) {
        this.next = next;
    }

    // Subscribe an appender to this handler (Observer subscribe)
    public void subscribe(LogAppender appender) {
        observers.add(appender);
    }

    // Core chain logic:
    //   1. If this handler owns the level → notify appenders
    //   2. Always pass to next (so multiple handlers can act)
    public void handle(LogMessage msg) {
        if (canHandle(msg.getLevel())) {
            notifyAllObservers(msg);
        }
        if (next != null) {
            next.handle(msg);
        }
    }

    // Notify every subscribed appender (Observer notify)
    protected void notifyAllObservers(LogMessage msg) {
        for (LogAppender appender : observers) {
            appender.append(msg);  // each append() is internally synchronized
        }
    }

    // Each concrete handler decides which level it owns
    public abstract boolean canHandle(LogLevel level);
}


// ─────────────────────────────────────────────
// 5a. DebugHandler
//     Only handles DEBUG messages.
//     Per design: subscribed to ConsoleAppender only.
// ─────────────────────────────────────────────
class DebugHandler extends LogHandler {
    @Override
    public boolean canHandle(LogLevel level) {
        return level == LogLevel.DEBUG;
    }
}


// ─────────────────────────────────────────────
// 5b. InfoHandler
//     Only handles INFO messages.
//     Subscribed to ConsoleAppender only.
// ─────────────────────────────────────────────
class InfoHandler extends LogHandler {
    @Override
    public boolean canHandle(LogLevel level) {
        return level == LogLevel.INFO;
    }
}


// ─────────────────────────────────────────────
// 5c. WarnHandler
//     Only handles WARN messages.
//     Subscribed to BOTH Console + File appenders.
//     (As noted in the UML diagram)
// ─────────────────────────────────────────────
class WarnHandler extends LogHandler {
    @Override
    public boolean canHandle(LogLevel level) {
        return level == LogLevel.WARN;
    }
}


// ─────────────────────────────────────────────
// 6. SINGLETON: Logger
//
//    Thread Safety approach: Double-Checked Locking (DCL)
//      - 'volatile' prevents CPU instruction reordering.
//        Without it, another thread could see a partially
//        constructed Logger object.
//      - Outer null check avoids locking on every call.
//      - Inner null check inside synchronized block
//        prevents duplicate creation if two threads
//        both pass the outer check simultaneously.
//
//    This is the entry point for all logging calls.
//    It builds the handler chain and wires up appenders.
// ─────────────────────────────────────────────
class Logger {

    // volatile ensures visibility of the fully constructed object across threads
    private static volatile Logger INSTANCE;

    private final LogHandler handleChain;   // head of the chain
    private final java.util.Map<LogLevel, LogHandler> handlerMap = new java.util.HashMap<>();

    // Private constructor — builds the full chain + appender wiring
    private Logger() {
        // --- Build Handlers ---
        DebugHandler debugHandler = new DebugHandler();
        InfoHandler  infoHandler  = new InfoHandler();
        WarnHandler  warnHandler  = new WarnHandler();

        // Chain order: DEBUG → INFO → WARN
        // Each message walks the entire chain; each handler picks its level.
        debugHandler.setNext(infoHandler);
        infoHandler.setNext(warnHandler);
        this.handleChain = debugHandler;   // entry point of chain

        // --- Build Appenders (with their formatting strategies) ---
        LogFormatter    plainFmt       = new PlainTxtFormatter();
        LogFormatter    jsonFmt        = new JsonFormatter();
        ConsoleAppender consoleAppender = new ConsoleAppender(plainFmt);
        FileAppender    fileAppender    = new FileAppender(jsonFmt, "app.log");

        // --- Wire Appenders to Handlers (Observer subscribe) ---
        // DEBUG → console only
        debugHandler.subscribe(consoleAppender);
        // INFO  → console only
        infoHandler.subscribe(consoleAppender);
        // WARN  → console + file  (as per UML diagram note)
        warnHandler.subscribe(consoleAppender);
        warnHandler.subscribe(fileAppender);

        // Map for addAppenderForLevel() convenience
        handlerMap.put(LogLevel.DEBUG, debugHandler);
        handlerMap.put(LogLevel.INFO,  infoHandler);
        handlerMap.put(LogLevel.WARN,  warnHandler);
    }

    // Double-Checked Locking Singleton
    public static Logger getInstance() {
        if (INSTANCE == null) {                      // 1st check: avoid lock overhead after init
            synchronized (Logger.class) {
                if (INSTANCE == null) {              // 2nd check: prevent duplicate creation
                    INSTANCE = new Logger();
                }
            }
        }
        return INSTANCE;
    }

    // Allows runtime addition of appenders for any level
    public void addAppenderForLevel(LogLevel level, LogAppender appender) {
        LogHandler handler = handlerMap.get(level);
        if (handler != null) handler.subscribe(appender);
    }

    // Core log method: creates message and fires the chain
    public void log(LogLevel level, LogMessage msg) {
        handleChain.handle(msg);
    }

    // Convenience methods (mirror the UML diagram)
    public void info(String message) {
        log(LogLevel.INFO,  new LogMessage(LogLevel.INFO,  message));
    }

    public void warn(String message) {
        log(LogLevel.WARN,  new LogMessage(LogLevel.WARN,  message));
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, new LogMessage(LogLevel.DEBUG, message));
    }
}


// ─────────────────────────────────────────────
// 7. CLIENT
//    Shows usage. Logger is accessed via getInstance().
//    All calls are thread-safe; can be used from any thread.
//
//  Flow for logger.warn("msg"):
//    Logger.log(WARN, msg)
//      → DebugHandler.handle()  canHandle(WARN)=false → next
//      → InfoHandler.handle()   canHandle(WARN)=false → next
//      → WarnHandler.handle()   canHandle(WARN)=true
//          → ConsoleAppender.append()   [synchronized]
//          → FileAppender.append()      [synchronized]
// ─────────────────────────────────────────────
public class Main {
    public static void main(String[] args) {
        Logger logger = Logger.getInstance();

        logger.debug("This is debug");   // → Console only
        logger.info("This is info");     // → Console only
        logger.warn("This is warn");     // → Console + File (app.log)

        // Demonstrating concurrency: multiple threads logging simultaneously
        Runnable task = () -> {
            for (int i = 0; i < 5; i++) {
                logger.warn("Concurrent warn from " + Thread.currentThread().getName());
            }
        };

        Thread t1 = new Thread(task, "Thread-1");
        Thread t2 = new Thread(task, "Thread-2");
        t1.start();
        t2.start();
    }
}