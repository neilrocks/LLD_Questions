package MeetingRoomSchedular;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
interface RoomAllocationStartegy{
    public MeetingRoom bookRoom(List<MeetingRoom>rooms,LocalDateTime start,LocalDateTime end,int cap);
}
class FirstComeFirstServeStrategy implements RoomAllocationStartegy{
    public MeetingRoom bookRoom(List<MeetingRoom>rooms,LocalDateTime start,LocalDateTime end,int cap){
        for(MeetingRoom room:rooms){
            if(room.getCapacity()<cap)continue;
            if(room.isAvailable(start,end)){
                return room;
            }
            
        }
        return null;
    }
}
class BookingRequest{
    private int id;
    private int cap;
    private LocalDateTime start;
    private LocalDateTime end;
    public BookingRequest(int id,int cap,LocalDateTime start,LocalDateTime end){
        this.id=id;
        this.cap=cap;
        this.start=start;
        this.end=end;
    }
    public LocalDateTime startTime(){
        return start;
    }
    public LocalDateTime endTime(){
        return end;
    }
    public int getReqId(){
        return id;
    }
    public int getCap(){
        return cap;
    }
    public String toString(){
        return "Booking Request "+id+" from "+start+" end "+end;
    }
} 
class Booking{
    private String name;
    private LocalDateTime start;
    private LocalDateTime end;
    public Booking(String name,LocalDateTime start,LocalDateTime end){
        this.name=name;
        this.start=start;
        this.end=end;
    }
    public LocalDateTime startTime(){
        return start;
    }
    public LocalDateTime endTime(){
        return end;
    }
    public String getName(){
        return name;
    }
    public String toString(){
        return "Booking "+name+" from "+start+" end "+end;
    }
}
class MeetingRoom{
    private int id;
    private int capacity;
    private List<Booking>bookings;
    private final ReentrantLock lock = new ReentrantLock();
    public MeetingRoom(int id,int capacity){
        this.id=id;
        this.capacity=capacity;
        bookings=new ArrayList<>();
    }
    public void addBooking(Booking book) {
        lock.lock();
        try {
            bookings.add(book);
        } finally {
            lock.unlock();
        }
    }
    public int getCapacity(){
        return capacity;
    }
    public void getHistory() {
        lock.lock();
        try {
            System.out.println("-----History for room with id " + id + "-----");
            for (Booking book : bookings) {
                System.out.println("Meeting name " + book.getName() + " from " +
                        book.startTime() + " to " + book.endTime());
            }
        } finally {
            lock.unlock();
        }
    }
    public boolean isAvailable(LocalDateTime start, LocalDateTime end) {
        lock.lock();
        try{
            boolean allocationPossible=true;
            for(Booking books:bookings){
                if (!(end.isBefore(books.startTime()) || start.isAfter(books.endTime()))) {
                    allocationPossible = false; // overlap
                }
            }
            return allocationPossible;
        }finally{
            lock.unlock();
        }
        
    }

    public String toString(){
        return "Room with id "+id+" and capacity as "+capacity;

    }
}
class MeetingRoomSchedular{
    private static MeetingRoomSchedular instance;
    private RoomAllocationStartegy strategy;
    private List<MeetingRoom>rooms;
    private MeetingRoomSchedular(){
    }
    public static MeetingRoomSchedular getInstance(){
        if(instance==null){
            instance=new MeetingRoomSchedular();
        }
        return instance;
    }
    public void setRooms(List<MeetingRoom>rooms){
        this.rooms=rooms;
    }
    public List<MeetingRoom> getRooms(){
        return rooms;
    }
    public void setRoomAllocationStrategy(RoomAllocationStartegy strategy){
        this.strategy=strategy;
    }
    public boolean sendBookingRequest(BookingRequest req){
        LocalDateTime now=req.startTime(),end=req.endTime();
        MeetingRoom room=strategy.bookRoom(rooms,now,end,req.getCap());
        if(room!=null){
            Booking book=new Booking("Name "+req.getReqId(),now,end);
            room.addBooking(book);
            return true;
        }
        return false;
    }
    public void getBookingHistory(){
        for(MeetingRoom room:rooms){
            room.getHistory();
        }
    }
}
public class Main {
    public static void main(String[] args) {
        MeetingRoom room1=new MeetingRoom(100,200);
        MeetingRoom room2=new MeetingRoom(200,50);
        MeetingRoomSchedular app=MeetingRoomSchedular.getInstance();
        app.setRooms(Arrays.asList(room1,room2));
        app.setRoomAllocationStrategy(new FirstComeFirstServeStrategy());
        Runnable task = ()->{
            BookingRequest req=new BookingRequest(1, 100,LocalDateTime.now(),LocalDateTime.now().plusHours(2));
            boolean success = app.sendBookingRequest(req);
            System.out.println(Thread.currentThread().getName() + 
                    " booking " + req.getReqId() + " -> " + (success ? "success" : "fail"));
        };
        Thread t1=new Thread(task,"User 1");
        Thread t2=new Thread(task,"User 1");
        Thread t3=new Thread(task,"User 1");
        Thread t4=new Thread(task,"User 1");
        t1.start(); t2.start(); t3.start(); t4.start();
        try{
            //join so main thread waits until thread is completed its task
            t1.join(); t2.join(); t3.join(); t4.join();
        }catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\n Booking History:");
        app.getBookingHistory();
    }
}
