/*
===========================================================
PARKING LOT SYSTEM DESIGN
===========================================================

---------------------------
Functional Requirements
---------------------------
1. The system should support a parking lot with multiple floors.
2. Each floor should contain multiple parking spots.
3. Parking spots should support different vehicle types like Car and Bike.
4. When a vehicle enters:
   - The system should find the nearest available spot for that vehicle type.
   - A parking ticket should be generated containing entry time and spot details.
5. When a vehicle exits:
   - The system should calculate the parking fee based on parking duration.
   - The user should pay using a payment method.
   - The parking spot should become available again.
6. The system should support multiple vehicles trying to park concurrently.

---------------------------
Non Functional Requirements
---------------------------
1. The system should be thread-safe.
2. The design should be extensible to support new vehicle types.
3. The system should allow different pricing strategies.
4. The system should allow multiple payment methods.
5. The system should follow good OOP design principles.

---------------------------
Core Entities
---------------------------

Vehicle
Represents a vehicle entering the parking lot.
Stores vehicle id and vehicle type.

Spot
Represents a parking spot in a floor.
Tracks whether the spot is occupied.
Uses locking to ensure thread-safe allocation.

Ticket
Generated when a vehicle parks.
Stores vehicle, spot, floor number and entry time.

ParkingFloor
Represents one floor in the parking lot.
Contains multiple parking spots.

MultiFloorParkingLot
Main system managing all floors.
Responsible for parking vehicles, exiting vehicles,
tracking active tickets, calculating fees and processing payment.

ParkingFeeStrategy
Strategy pattern used to calculate parking fees.

PaymentStrategy
Strategy pattern used to support multiple payment methods.

===========================================================
*/

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


// ---------------------------------------------------------
// Vehicle Hierarchy
// ---------------------------------------------------------

/*
Vehicle is an abstract class representing any vehicle entering
the parking lot. It stores common attributes like vehicle id
and vehicle type.
*/
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

/*
Car class represents a specific type of vehicle.
It extends the Vehicle base class.
*/
class Car extends Vehicle {
    Car(String id) {
        super(id, "Car");
    }
}

/*
Bike class represents another vehicle type.
*/
class Bike extends Vehicle {
    Bike(String id) {
        super(id, "Bike");
    }
}


// ---------------------------------------------------------
// Spot
// ---------------------------------------------------------

/*
Spot represents a parking spot in a floor.

Responsibilities:
1. Store spot id.
2. Store spot type (Car or Bike).
3. Track whether the spot is occupied.
4. Ensure thread-safe allocation using locks.
*/
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

    /*
    Thread-safe method to occupy the spot.
    Only one thread can occupy it at a time.
    */
    public boolean tryOccupy() {

        lock.lock();
        try {
            if (!isOccupied) {
                isOccupied = true;
                return true;
            }
            return false;
        }
        finally {
            lock.unlock();
        }
    }

    /*
    Frees the parking spot when the vehicle exits.
    */
    public void free() {

        lock.lock();
        try {
            isOccupied = false;
        }
        finally {
            lock.unlock();
        }
    }
}


// ---------------------------------------------------------
// Ticket
// ---------------------------------------------------------

/*
Ticket represents a parking ticket generated when a
vehicle successfully parks.

It stores:
1. Vehicle details
2. Allocated parking spot
3. Floor number
4. Entry time for fee calculation
*/
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


// ---------------------------------------------------------
// Strategy Pattern for Parking Fee
// ---------------------------------------------------------

/*
ParkingFeeStrategy defines an interface for calculating
parking fees.

Different pricing algorithms can implement this interface.
*/
interface ParkingFeeStrategy {

    double calcFee(String vehicleType,
                   LocalDateTime entryTime,
                   LocalDateTime exitTime);
}


/*
HourlyFee is one implementation where parking fee is
calculated on an hourly basis.
*/
class HourlyFee implements ParkingFeeStrategy {

    public double calcFee(String vehicleType,
                          LocalDateTime entry,
                          LocalDateTime exit) {

        long hours = Duration.between(entry, exit).toHours();

        if (hours == 0)
            hours = 1; // minimum charge 1 hour

        if (vehicleType.equals("Car"))
            return 100 * hours;
        else
            return 50 * hours;
    }
}


// ---------------------------------------------------------
// Strategy Pattern for Payment
// ---------------------------------------------------------

/*
PaymentStrategy defines a payment interface.

Different payment types like UPI, Card or Cash can
implement this interface.
*/
interface PaymentStrategy {
    void pay(double amount);
}


/*
UPI payment implementation.
*/
class UpiPay implements PaymentStrategy {

    public void pay(double amount) {
        System.out.println("Paid ₹" + amount + " via UPI");
    }
}


// ---------------------------------------------------------
// Parking Floor
// ---------------------------------------------------------

/*
ParkingFloor represents a single floor of the parking lot.

Responsibilities:
1. Maintain list of parking spots.
2. Provide a free spot for a given vehicle type.
*/
class ParkingFloor {

    private final int floorNumber;
    private final List<Spot> spots;

    ParkingFloor(int floorNumber, int carSpots, int bikeSpots) {

        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>();

        int id = 1;

        for (int i = 0; i < carSpots; i++)
            spots.add(new Spot(id++, "Car"));

        for (int i = 0; i < bikeSpots; i++)
            spots.add(new Spot(id++, "Bike"));
    }

    public int getFloorNumber() { return floorNumber; }

    /*
    Find a free spot for the given vehicle type.
    */
    public Spot getFreeSpot(String type) {

        for (Spot s : spots) {

            if (s.getType().equals(type) && s.tryOccupy())
                return s;
        }

        return null;
    }
}


// ---------------------------------------------------------
// Multi Floor Parking Lot (Main System)
// ---------------------------------------------------------

/*
This class represents the entire parking lot.

Responsibilities:
1. Manage multiple parking floors.
2. Allocate spots when vehicles enter.
3. Maintain active parking tickets.
4. Handle vehicle exit.
5. Calculate parking fee.
6. Process payment.
*/
class MultiFloorParkingLot {

    private final List<ParkingFloor> floors = new ArrayList<>();

    // active tickets stored using vehicle id
    private final Map<String, Ticket> activeTickets =
            new ConcurrentHashMap<>();

    // coarse-grain lock for parking operations
    private final ReentrantLock lotLock = new ReentrantLock();

    private ParkingFeeStrategy feeStrategy;
    private PaymentStrategy payStrategy;

    MultiFloorParkingLot(int numFloors,
                         int carSpotsPerFloor,
                         int bikeSpotsPerFloor) {

        for (int i = 1; i <= numFloors; i++)
            floors.add(new ParkingFloor(i,
                    carSpotsPerFloor,
                    bikeSpotsPerFloor));
    }

    public void setFeeStrategy(ParkingFeeStrategy strategy) {
        this.feeStrategy = strategy;
    }

    public void setPayStrategy(PaymentStrategy strategy) {
        this.payStrategy = strategy;
    }

    /*
    Handles vehicle parking.
    Finds the first available spot across floors.
    */
    public Ticket park(Vehicle v) {

        lotLock.lock();

        try {

            for (ParkingFloor f : floors) {

                Spot s = f.getFreeSpot(v.getType());

                if (s != null) {

                    Ticket t = new Ticket(v, s, f.getFloorNumber());

                    activeTickets.put(v.getId(), t);

                    System.out.println(v.getType() + " "
                            + v.getId()
                            + " parked at Floor "
                            + f.getFloorNumber()
                            + ", Spot "
                            + s.getId());

                    return t;
                }
            }

            System.out.println("No available spot for "
                    + v.getType());

            return null;
        }

        finally {
            lotLock.unlock();
        }
    }

    /*
    Handles vehicle exit.

    Steps:
    1. Fetch ticket
    2. Calculate fee
    3. Process payment
    4. Free parking spot
    */
    public void exit(String vehicleId) {

        Ticket t = activeTickets.remove(vehicleId);

        if (t == null) {

            System.out.println("Vehicle "
                    + vehicleId
                    + " not found.");

            return;
        }

        LocalDateTime exitTime = LocalDateTime.now();

        double fee =
                feeStrategy.calcFee(
                        t.getVehicle().getType(),
                        t.getEntryTime(),
                        exitTime);

        System.out.println("Parking fee for vehicle "
                + vehicleId
                + ": ₹"
                + fee);

        payStrategy.pay(fee);

        // free spot
        t.getSpot().free();

        System.out.println("Spot "
                + t.getSpot().getId()
                + " on Floor "
                + t.getFloorNumber()
                + " is now free.");
    }
}


// ---------------------------------------------------------
// Main Driver
// ---------------------------------------------------------

/*
Main class simulates concurrent parking using threads.
*/
public class Main {

    public static void main(String[] args) {

        MultiFloorParkingLot lot =
                new MultiFloorParkingLot(2, 2, 2);

        lot.setFeeStrategy(new HourlyFee());
        lot.setPayStrategy(new UpiPay());

        Runnable carTask = () -> {

            Vehicle car =
                    new Car(UUID.randomUUID().toString());

            Ticket t = lot.park(car);

            if (t != null) {

                try {
                    Thread.sleep(500);
                }
                catch (InterruptedException ignored) {}

                lot.exit(car.getId());
            }
        };

        // simulate concurrent parking
        Thread t1 = new Thread(carTask);
        Thread t2 = new Thread(carTask);
        Thread t3 = new Thread(carTask);

        t1.start();
        t2.start();
        t3.start();
    }
}