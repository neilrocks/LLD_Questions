package PostOffice;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class Customer{
    String id;
    String name;
    String address;
    Customer(String id,String name,String address){
        this.id=id;
        this.name=name;
        this.address=address;
    }
    public String getName(){
        return name;
    }
    public String getAddress(){
        return address;
    }
    public String getId(){
        return id;
    }
}
class PostOffice{
    String id;
    String name;
    String address;
    PostOffice(String id,String name,String address){
        this.id=id;
        this.name=name;
        this.address=address;
    }
    public String getName(){
        return name;
    }
    public String getAddress(){
        return address;
    }
    public String getId(){
        return id;
    }
}
class PackageItem {
    private final String id;
    private final String description;
    private final double weight;

    public PackageItem(String id, String description, double weight) {
        this.id = id;
        this.description = description;
        this.weight = weight;
    }

    public String getDescription() { return description; }
    public double getWeight() { return weight; }
    public String getId(){return id;}
    public String toString(){
        return "Id "+id+" Description "+description+" weight "+weight;
    }
}
interface ShipmentState{
    void move(Shipment s,PostOffice nextOffice);
    void deliver(Shipment s);
    ShipmentStatus getStatus();
}
class CreatedState implements ShipmentState{
    public void move(Shipment s,PostOffice nextOffice){
        s.setCurrentOffice(nextOffice);
        s.setState(new InTransitState());
        System.out.println("Shipment moved to: " + nextOffice.getName());
    }
    public void deliver(Shipment s){
        throw new IllegalStateException("Shipment cannot be delivered directly from CREATED state!");
    }
    public ShipmentStatus getStatus() { return ShipmentStatus.CREATED; }
}
class InTransitState implements ShipmentState {
    @Override
    public void move(Shipment shipment, PostOffice nextOffice) {
        shipment.setCurrentOffice(nextOffice);
        System.out.println("Shipment moved to: " + nextOffice.getName());
    }

    @Override
    public void deliver(Shipment shipment) {
        shipment.setState(new DeliveredState());
        System.out.println("Shipment delivered!");
    }

    public ShipmentStatus getStatus() { return ShipmentStatus.IN_TRANSIT; }
}
class DeliveredState implements ShipmentState {
    @Override
    public void move(Shipment shipment, PostOffice nextOffice) {
        throw new IllegalStateException("Shipment already delivered. Cannot move further!");
    }

    @Override
    public void deliver(Shipment shipment) {
        System.out.println("Shipment already delivered.");
    }

    @Override
    public ShipmentStatus getStatus() { return ShipmentStatus.DELIVERED; }
}
enum ShipmentStatus{
    CREATED,
    IN_TRANSIT,
    DELIVERED
}
class Shipment {
    private String id;
    private String trackingNumber;
    private Customer sender;
    private Customer receiver;
    private List<PackageItem> items;
    private PostOffice currentOffice;
    private ShipmentState state;
    private final Lock lock = new ReentrantLock();
    public Shipment() {
        this.state = new CreatedState(); // default
    }

    // State transitions
    public void move(PostOffice nextOffice) {
        lock.lock();
        try {
            state.move(this, nextOffice);
        } finally {
            lock.unlock();
        }
    }

    public void deliver() {
        lock.lock();
        try {
            state.deliver(this);
        } finally {
            lock.unlock();
        }
    }

    // Accessors
    public ShipmentStatus getStatus() { return state.getStatus(); }
    public String getTrackingNumber() { return trackingNumber; }
    public Customer getReceiver() { return receiver; }
    public PostOffice getCurrentOffice() { return currentOffice; }

    // Internal setters (used by State & Factory)
    public void setState(ShipmentState state) { this.state = state; }
    public void setCurrentOffice(PostOffice office) { this.currentOffice = office; }
    public void setId(String id) { this.id = id; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public void setSender(Customer sender) { this.sender = sender; }
    public void setReceiver(Customer receiver) { this.receiver = receiver; }
    public void setItems(List<PackageItem> items) { this.items = items; }
    public List<PackageItem> getItems(){
        return items;
    }
}
class ShipmentFactory{
    public static Shipment createShipment(PostOffice origin,Customer sender,Customer receiver,List<PackageItem>items){
        Shipment shipment = new Shipment();
        shipment.setId(UUID.randomUUID().toString());
        shipment.setTrackingNumber("TRK-" + System.currentTimeMillis());
        shipment.setSender(sender);
        shipment.setReceiver(receiver);
        shipment.setItems(items);
        shipment.setCurrentOffice(origin);
        return shipment;
    }
}
class PostOfficeApplication{
    private static PostOfficeApplication instance;
    private Map<String,Shipment>db=new ConcurrentHashMap<>();
    private PostOfficeApplication(){

    }
    public static PostOfficeApplication getInstance(){
        if(instance==null){
            instance=new PostOfficeApplication();
        }
        return instance;
    }
    public Shipment createShipment(PostOffice origin,Customer c_sender,Customer c_receiver,List<PackageItem>items){
        Shipment shipment=ShipmentFactory.createShipment(origin,c_sender,c_receiver,items);
        db.put(shipment.getTrackingNumber(),shipment);
        return shipment;
    }
    public Shipment track(String trackingNumber) {
        return db.get(trackingNumber);
    }
}
public class Main {
    public static void main(String[] args) {
        // Customers
        Customer alice = new Customer("C1", "Alice", "New York");
        Customer bob = new Customer("C2", "Bob", "Los Angeles");

        // Post Offices
        PostOffice ny = new PostOffice("P1", "NY Office", "NY");
        PostOffice la = new PostOffice("P2", "LA Office", "LA");
        PostOfficeApplication app=PostOfficeApplication.getInstance();
        ArrayList<PackageItem>items=new ArrayList<>();
        items.add(new PackageItem("PKG1", "Book", 1.2));
        items.add(new PackageItem("PKG2", "Tabel", 20));
        Shipment s = app.createShipment(ny, alice, bob, items);
        System.out.println("Shipment Created: " + s.getTrackingNumber() +
                " | Status: " + s.getStatus());
        System.out.println("Items in Shipment "+s.getItems());
        ExecutorService executor=Executors.newFixedThreadPool(3);
        executor.submit(()->{
            s.move(la);
            System.out.println("Status after move: " + s.getStatus());
        });

        executor.submit(()->{
            // Deliver shipment
            s.deliver();
        });
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Now safe to print final status
        System.out.println("Final Status: " + s.getStatus());
    }
}
