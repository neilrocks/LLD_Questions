import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

// ---------------- Vehicle Hierarchy ----------------
abstract class Vehicle {
    private final String id;
    private final String type;
    Vehicle(String id, String type) {
        this.id = id;
        this.type = type;
    }
    public String getId() { return id; }
    public String getType() { return type; }
}

class Car extends Vehicle {
    Car(String id) { super(id, "Car"); }
}
class Bike extends Vehicle {
    Bike(String id) { super(id, "Bike"); }
}

// ---------------- Spot ----------------
class Spot {
    private final int id;
    private final String type;
    private boolean isOccupied;
    private final ReentrantLock lock = new ReentrantLock();

    Spot(int id, String type) {
        this.id = id;
        this.type = type;
        this.isOccupied = false;
    }

    public int getId() { return id; }
    public String getType() { return type; }

    // Thread-safe occupancy
    public boolean tryOccupy() {
        lock.lock();
        try {
            if (!isOccupied) {
                isOccupied = true;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void free() {
        lock.lock();
        try {
            isOccupied = false;
        } finally {
            lock.unlock();
        }
    }
}

// ---------------- Ticket ----------------
class Ticket {
    private final Vehicle vehicle;
    private final Spot spot;
    private final int floorNumber;
    private final LocalDateTime entryTime;

    Ticket(Vehicle vehicle, Spot spot, int floorNumber) {
        this.vehicle = vehicle;
        this.spot = spot;
        this.floorNumber = floorNumber;
        this.entryTime = LocalDateTime.now();
    }

    public Vehicle getVehicle() { return vehicle; }
    public Spot getSpot() { return spot; }
    public int getFloorNumber() { return floorNumber; }
    public LocalDateTime getEntryTime() { return entryTime; }
}

// ---------------- Strategies ----------------
interface ParkingFeeStrategy {
    double calcFee(String vehicleType, LocalDateTime entryTime, LocalDateTime exitTime);
}

class HourlyFee implements ParkingFeeStrategy {
    public double calcFee(String vehicleType, LocalDateTime entry, LocalDateTime exit) {
        long hours = Duration.between(entry, exit).toHours();
        if (hours == 0) hours = 1; // minimum 1 hour
        return vehicleType.equals("Car") ? 100 * hours : 50 * hours;
    }
}

interface PaymentStrategy {
    void pay(double amount);
}

class UpiPay implements PaymentStrategy {
    public void pay(double amount) {
        System.out.println("Paid ₹" + amount + " via UPI");
    }
}

// ---------------- ParkingFloor ----------------
class ParkingFloor {
    private final int floorNumber;
    private final List<Spot> spots;

    ParkingFloor(int floorNumber, int carSpots, int bikeSpots) {
        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>();
        int id = 1;
        for (int i = 0; i < carSpots; i++) spots.add(new Spot(id++, "Car"));
        for (int i = 0; i < bikeSpots; i++) spots.add(new Spot(id++, "Bike"));
    }

    public int getFloorNumber() { return floorNumber; }

    public Spot getFreeSpot(String type) {
        for (Spot s : spots) {
            if (s.getType().equals(type) && s.tryOccupy()) {
                return s;
            }
        }
        return null;
    }
}

// ---------------- Multi-Floor Parking Lot ----------------
class MultiFloorParkingLot {
    private final List<ParkingFloor> floors = new ArrayList<>();
    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();
    private final ReentrantLock lotLock = new ReentrantLock(); // coarse-grain lot lock
    private ParkingFeeStrategy feeStrategy;
    private PaymentStrategy payStrategy;

    MultiFloorParkingLot(int numFloors, int carSpotsPerFloor, int bikeSpotsPerFloor) {
        for (int i = 1; i <= numFloors; i++) {
            floors.add(new ParkingFloor(i, carSpotsPerFloor, bikeSpotsPerFloor));
        }
    }

    public void setFeeStrategy(ParkingFeeStrategy strategy) { this.feeStrategy = strategy; }
    public void setPayStrategy(PaymentStrategy strategy) { this.payStrategy = strategy; }

    public Ticket park(Vehicle v) {
        lotLock.lock();
        try {
            for (ParkingFloor f : floors) {
                Spot s = f.getFreeSpot(v.getType());
                if (s != null) {
                    Ticket t = new Ticket(v, s, f.getFloorNumber());
                    activeTickets.put(v.getId(), t);
                    System.out.println(v.getType() + " " + v.getId() +
                                       " parked at Floor " + f.getFloorNumber() + ", Spot " + s.getId());
                    return t;
                }
            }
            System.out.println("No available spot for " + v.getType());
            return null;
        } finally {
            lotLock.unlock();
        }
    }

    public void exit(String vehicleId) {
        Ticket t = activeTickets.remove(vehicleId);
        if (t == null) {
            System.out.println("Vehicle " + vehicleId + " not found.");
            return;
        }
        LocalDateTime exitTime = LocalDateTime.now();
        double fee = feeStrategy.calcFee(t.getVehicle().getType(), t.getEntryTime(), exitTime);
        System.out.println("Parking fee for vehicle " + vehicleId + ": ₹" + fee);
        payStrategy.pay(fee);

        // Free the spot
        t.getSpot().free();
        System.out.println("Spot " + t.getSpot().getId() + " on Floor " + t.getFloorNumber() + " is now free.");
    }
}

// ---------------- Main ----------------
public class Main {
    public static void main(String[] args) {
        MultiFloorParkingLot lot = new MultiFloorParkingLot(2, 2, 2);
        lot.setFeeStrategy(new HourlyFee());
        lot.setPayStrategy(new UpiPay());

        Runnable carTask = () -> {
            Vehicle car = new Car(UUID.randomUUID().toString());
            Ticket t = lot.park(car);
            if (t != null) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                lot.exit(car.getId());
            }
        };

        // Simulate concurrent parking with multiple threads
        Thread t1 = new Thread(carTask);
        Thread t2 = new Thread(carTask);
        Thread t3 = new Thread(carTask);

        t1.start(); t2.start(); t3.start();
    }
}
