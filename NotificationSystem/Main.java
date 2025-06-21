import java.util.*;
interface Notification {
    public String getContent();
}
class SimpleNotification implements Notification{
    private String content;
    SimpleNotification(String content){
        this.content=content;
    }
    public String getContent(){
        return content;
    }
}
abstract class NotificationDecorator implements Notification{ //is a relationship
    protected Notification notification;//has a relationship
    NotificationDecorator(Notification notification){
        this.notification = notification;
    }
}
class SignatureDecorator extends NotificationDecorator{
    private String signature;
    SignatureDecorator(Notification notification, String signature){
        super(notification);
        this.signature = signature;
    }
    public String getContent(){
        return notification.getContent() + "\n\n" + "Signature: " + signature;
    }
}
class TimestampDecorator extends NotificationDecorator{
    private String timestamp;
    TimestampDecorator(Notification notification, String timestamp){
        super(notification);
        this.timestamp = timestamp;
    }
    public String getContent(){
        return notification.getContent() + "\n\n" + "Timestamp: " + timestamp;
    }
}
interface Observable{
    public void addObserver(Observer observer);
    public void removeObserver(Observer observer);
    public void notifyObservers();
}
class NotificationObservable implements Observable{
    Notification notification;
    private List<Observer> observers=new ArrayList<>();
    NotificationObservable(Notification notification) {
        this.notification = notification;
    }
    public void addObserver(Observer observer) {
        observers.add(observer);
    }
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }
    public void notifyObservers(){
        for(Observer obs:observers){
            obs.update();
        }
    }
    public String getNotificationContent() {
        return notification.getContent();
    }
    public Notification getNotification() {
        return notification;
    }
    public void setNotification(Notification notification) {
        this.notification = notification;
        notifyObservers();
    }
}
interface Observer{
    public void update();
}
class Logger implements Observer{
    private NotificationObservable observable;
    Logger(NotificationObservable observable) {
        this.observable = observable;
    }
    public void update() {
        System.out.println("Logger: New notification received: " + observable.getNotificationContent());
    }
}
class NotificationEngineContext implements Observer{
    private NotificationObservable observable;
    protected List<NotificationStartegies> strategies; 
    NotificationEngineContext(NotificationObservable observable,List<NotificationStartegies> strategies) {
        this.observable = observable;
        this.strategies = strategies;
    }
    public void update() {
        for(NotificationStartegies strategy : strategies) {
            strategy.sendNotification(observable.getNotification());
        }
    } 
}
interface NotificationStartegies{
    public void sendNotification(Notification notification);
}
class EmailNotificationStrategy implements NotificationStartegies{
    public void sendNotification(Notification notification) {
        System.out.println("Sending Email: " + notification.getContent());
    }
}
class SMSNotificationStrategy implements NotificationStartegies{
    public void sendNotification(Notification notification) {
        System.out.println("Sending SMS: " + notification.getContent());
    }
}
class PushNotificationStrategy implements NotificationStartegies{
    public void sendNotification(Notification notification) {
        System.out.println("Sending Push Notification: " + notification.getContent());
    }
}
class NotificationService{
    private NotificationObservable observable;
    private static NotificationService instance;
    private List<NotificationStartegies> strategies;
    private List<Notification> history;
    private NotificationService(Notification notification, List<NotificationStartegies> strategies) {
        this.observable = new NotificationObservable(notification);
        this.strategies = strategies;
        this.history = new ArrayList<>();
    }
    public static NotificationService getInstance(Notification notification, List<NotificationStartegies> strategies) {
        if (instance == null) {
            instance = new NotificationService(notification, strategies);
        }
        return instance;
    }
    public void sendNotification(Notification notification) {
        observable.setNotification(notification);
        history.add(notification);
    }
    public void updateNotification(Notification notification) {
        observable.setNotification(notification);
        history.add(notification);
    }
    public void addStrategy(NotificationStartegies strategy) {
        strategies.add(strategy);
        observable.addObserver(new NotificationEngineContext(observable, strategies));
    }
    public void addObserver(Observer observer) {
        observable.addObserver(observer);
    }  
    public NotificationObservable getObservable() {
        return observable;
    } 
}
public class Main {
    public static void main(String[] args) {
        Notification notification = new SimpleNotification("Hello, this is a test notification.");
        List<NotificationStartegies> strategies = new ArrayList<>();
        strategies.add(new EmailNotificationStrategy());
        strategies.add(new SMSNotificationStrategy());
        
        NotificationService service = NotificationService.getInstance(notification, strategies);

        Logger logger = new Logger(service.getObservable());
        service.addObserver(logger);
        
        // Adding a push notification strategy
        service.addStrategy(new PushNotificationStrategy());
        
        // Updating the notification
        Notification updatedNotification = new SignatureDecorator(
            new TimestampDecorator(notification, "2023-10-01 10:00:00"), "John Doe"
        );
        service.updateNotification(updatedNotification);
    }
}
