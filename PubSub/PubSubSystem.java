/*
REQUIREMENTS
------------
1. Topics: The system supports multiple topics.
2. Publishers: Publishers can publish messages to any topic.
3. Subscribers: Subscribers can subscribe to one or more topics.
4. Multiple Subscribers: Each topic can have multiple subscribers.
5. Asynchronous Delivery: Messages must be delivered asynchronously.
6. Unsubscribe: Subscribers should be able to unsubscribe from topics.
7. Extensibility: Easy to add new subscriber types or processing logic.

CORE ENTITIES
-------------
Broker: Central component that manages topics, subscriptions and message routing.
Topic: Represents a logical channel where messages are published.
Publisher: Publishes messages to topics via the Broker.
Subscriber: Interface representing consumers of messages.
PrintSubscriber: Subscriber implementation that prints messages.
LoggingSubscriber: Subscriber implementation that logs messages.
Message: Represents the payload being transmitted.
Dispatcher: Responsible for asynchronous message delivery.
*/

import java.util.*;
import java.util.concurrent.*;

/* 
Message object representing a payload sent by publishers.
This can be extended later to include metadata like timestamp, headers, etc.
*/
class Message {

    private String payload;

    public Message(String payload) {
        this.payload = payload;
    }

    public String getPayload() {
        return payload;
    }
}

/*
Subscriber interface.
All subscriber types must implement the consume() method.
This allows extensibility since new subscriber types can be added easily.
*/
interface Subscriber {

    void consume(String topicName, Message message);

    String getId();
}

/*
A simple subscriber implementation that prints messages to console.
*/
class PrintSubscriber implements Subscriber {

    private String id;

    public PrintSubscriber(String id) {
        this.id = id;
    }

    public void consume(String topicName, Message message) {
        System.out.println("PrintSubscriber " + id + " received from topic [" + topicName + "] : " + message.getPayload());
    }

    public String getId() {
        return id;
    }
}

/*
Another subscriber implementation which simulates logging behavior.
*/
class LoggingSubscriber implements Subscriber {

    private String id;

    public LoggingSubscriber(String id) {
        this.id = id;
    }

    public void consume(String topicName, Message message) {
        System.out.println("LoggingSubscriber " + id + " logged message from [" + topicName + "] : " + message.getPayload());
    }

    public String getId() {
        return id;
    }
}

/*
Topic represents a message channel.

We use CopyOnWriteArrayList for storing subscribers because:
- Subscriptions/unsubscriptions are relatively rare compared to reads.
- Message dispatch involves iterating over subscribers.
- CopyOnWriteArrayList allows thread-safe iteration without locking.
*/
class Topic {

    private String name;

    private List<Subscriber> subscribers;

    public Topic(String name) {
        this.name = name;
        this.subscribers = new CopyOnWriteArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void addSubscriber(Subscriber subscriber) {
        subscribers.add(subscriber);
    }

    public void removeSubscriber(Subscriber subscriber) {
        subscribers.remove(subscriber);
    }

    public List<Subscriber> getSubscribers() {
        return subscribers;
    }
}

/*
Dispatcher is responsible for asynchronous message delivery.

We use ExecutorService with a thread pool so that:
- Message delivery happens asynchronously.
- Publishing thread does not block while subscribers process messages.
*/
class Dispatcher {

    private ExecutorService executor;

    public Dispatcher(int workerThreads) {
        executor = Executors.newFixedThreadPool(workerThreads);
    }

    public void dispatch(Subscriber subscriber, String topicName, Message message) {

        executor.submit(() -> {
            subscriber.consume(topicName, message);
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}

/*
Broker is the central orchestrator.

Responsibilities:
- Manage topics
- Handle subscriptions/unsubscriptions
- Route published messages to correct subscribers
*/
class Broker {

    /*
    We use ConcurrentHashMap to store topics because:
    - Multiple publishers/subscribers may access it concurrently.
    - ConcurrentHashMap allows thread-safe reads/writes without global locking.
    */
    private Map<String, Topic> topics;

    private Dispatcher dispatcher;

    public Broker() {
        topics = new ConcurrentHashMap<>();
        dispatcher = new Dispatcher(4);
    }

    /*
    Create topic if it doesn't exist.
    */
    public void createTopic(String topicName) {
        topics.putIfAbsent(topicName, new Topic(topicName));
    }

    /*
    Subscribe a subscriber to a topic.
    */
    public void subscribe(String topicName, Subscriber subscriber) {

        Topic topic = topics.get(topicName);

        if(topic == null) {
            System.out.println("Topic does not exist: " + topicName);
            return;
        }

        topic.addSubscriber(subscriber);
    }

    /*
    Unsubscribe a subscriber from a topic.
    */
    public void unsubscribe(String topicName, Subscriber subscriber) {

        Topic topic = topics.get(topicName);

        if(topic != null) {
            topic.removeSubscriber(subscriber);
        }
    }

    /*
    Publisher publishes message via broker.

    Broker finds the topic and dispatches message asynchronously to all subscribers.
    */
    public void publish(String topicName, Message message) {

        Topic topic = topics.get(topicName);

        if(topic == null) {
            System.out.println("Topic does not exist: " + topicName);
            return;
        }

        for(Subscriber subscriber : topic.getSubscribers()) {
            dispatcher.dispatch(subscriber, topicName, message);
        }
    }
}

/*
Publisher class.

Publisher is decoupled from the internal details of the broker.
It only interacts with the publish API.
*/
class Publisher {

    private String id;

    private Broker broker;

    public Publisher(String id, Broker broker) {
        this.id = id;
        this.broker = broker;
    }

    public void publish(String topicName, String payload) {

        Message message = new Message(payload);

        System.out.println("Publisher " + id + " published to [" + topicName + "] : " + payload);

        broker.publish(topicName, message);
    }
}

/*
Driver class to demonstrate the system.

Shows:
- topic creation
- multiple subscribers
- publishing messages
- unsubscribe flow
- asynchronous delivery
*/
public class PubSubSystem {

    public static void main(String[] args) throws InterruptedException {

        Broker broker = new Broker();

        broker.createTopic("sports");
        broker.createTopic("news");

        Subscriber s1 = new PrintSubscriber("S1");
        Subscriber s2 = new LoggingSubscriber("S2");
        Subscriber s3 = new PrintSubscriber("S3");

        broker.subscribe("sports", s1);
        broker.subscribe("sports", s2);
        broker.subscribe("news", s3);

        Publisher p1 = new Publisher("P1", broker);

        // Expected Output:
        // Publisher P1 published to [sports] : India won the match
        // PrintSubscriber S1 received from topic [sports] : India won the match
        // LoggingSubscriber S2 logged message from [sports] : India won the match
        p1.publish("sports", "India won the match");
        
        // Expected Output:
        // Publisher P1 published to [news] : Elections announced
        // PrintSubscriber S3 received from topic [news] : Elections announced
        p1.publish("news", "Elections announced");

        Thread.sleep(1000);

        System.out.println("\nSubscriber S2 unsubscribing from sports\n");

        broker.unsubscribe("sports", s2);

        // Expected Output:
        // Publisher P1 published to [sports] : New football season starts
        // PrintSubscriber S1 received from topic [sports] : New football season starts
        // Note: S2 will NOT receive this message since it unsubscribed
        p1.publish("sports", "New football season starts");

        Thread.sleep(1000);
    }
}