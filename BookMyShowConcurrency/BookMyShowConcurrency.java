/*
 * BOOK MY SHOW - LOW LEVEL DESIGN WITH CONCURRENCY HANDLING
 * ===========================================================
 * 
 * REQUIREMENTS:
 * -------------
 * 1. Users should be able to search for movies and shows
 * 2. Users should be able to book seats for a show
 * 3. Multiple users should be able to book different seats concurrently
 * 4. The system should handle race conditions when multiple users try to book the same seat
 * 5. Seats should be temporarily locked when a user initiates booking
 * 6. If payment is not completed within TTL (Time To Live), the lock should expire and seats become available
 * 7. Support multiple payment methods (Card, UPI, Wallet)
 * 8. System should handle booking confirmation only after successful payment
 * 9. Theater management: Add theaters, screens, and seats
 * 10. Show management: Create shows with movie, theater, screen, and timing details
 * 
 * CORE ENTITIES:
 * --------------
 * 1. Movie - Represents a movie with id, name, and duration
 * 2. Theater - Represents a cinema hall with multiple screens
 * 3. Screen - Represents a screen within a theater containing multiple seats
 * 4. Seat - Abstract class representing different seat types (Regular, Recliner)
 * 5. Show - Represents a movie screening at a specific theater, screen, and time
 * 6. Booking - Represents a user's seat reservation with payment details
 * 7. User - Identified by userId string
 * 
 * KEY DESIGN PATTERNS USED:
 * -------------------------
 * 1. Repository Pattern - For data access (MovieRepository, TheaterRepository, etc.)
 * 2. Service Pattern - Business logic layer (MovieService, TheaterService, etc.)
 * 3. Strategy Pattern - Payment processing (PaymentStrategy interface)
 * 4. Factory Pattern - Creating payment strategies (PaymentStrategyFactory)
 * 5. Provider Pattern - Lock management (LockProvider interface)
 * 
 * CONCURRENCY HANDLING:
 * ---------------------
 * - Distributed locking mechanism using LockProvider
 * - TTL-based lock expiration to prevent indefinite seat blocking
 * - ConcurrentHashMap for thread-safe lock storage
 * - Atomic operations using compute() for lock acquisition
 * - Background sweeper thread to clean up expired locks
 * 
 * BOOKING FLOW:
 * -------------
 * 1. User searches for shows
 * 2. User selects seats and initiates booking (createBooking)
 *    - System tries to acquire locks on selected seats
 *    - If successful, booking is created in CREATED state
 * 3. User completes payment (confirmBooking)
 *    - System validates locks are still held by the user
 *    - Payment is processed
 *    - Locks are released
 *    - Booking status changes to CONFIRMED
 * 4. If user doesn't complete payment within TTL, locks expire and seats become available
 */

package BookMyShowConcurrency;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class SeatNotAvailableException extends RuntimeException {
    public SeatNotAvailableException(String message) {
        super(message);
    }
}
class BookingService{
    LockProvider lockProvider;
    BookingRepository bookingRepository;
    public BookingService(LockProvider lockProvider, BookingRepository bookingRepository){
        this.lockProvider = lockProvider;
        this.bookingRepository = bookingRepository;
    }
    
    /**
     * CREATE BOOKING - First step in the booking flow
     * -----------------------------------------------
     * This method initiates a seat booking for a user.
     * 
     * WORKFLOW:
     * 1. Iterate through all requested seat IDs
     * 2. For each seat, create a unique lock key: "showId:seatId"
     * 3. Attempt to acquire a lock on the seat with TTL (5000ms = 5 seconds)
     *    - If lock acquisition fails, throw SeatNotAvailableException
     *    - This prevents race conditions when multiple users try to book the same seat
     * 4. Calculate total price by iterating through all seats in the screen
     * 5. Create a Booking object with status = CREATED
     * 6. Store booking in repository
     * 
     * CONCURRENCY HANDLING:
     * - Each seat is locked individually to allow concurrent bookings of different seats
     * - Lock key format ensures uniqueness across shows and seats
     * - TTL ensures that if user abandons booking, seats become available again
     * 
     * @param userId - The ID of the user making the booking
     * @param show - The show for which seats are being booked
     * @param seatId - List of seat IDs to book
     * @return Booking object in CREATED state
     * @throws SeatNotAvailableException if any seat cannot be locked
     */
    public Booking createBooking(String userId,Show show,List<Integer>seatId){
        for(Integer id:seatId){
            String key=show.getId()+":"+id;
            if(!lockProvider.tryLock(key,userId,5000)){
                throw new SeatNotAvailableException("Seat "+id+" is not available for booking");
            }
        }
        double totalPrice=0.0;
        for(Seat seat:show.getScreen().getSeats()){
            if(seatId.contains(seat.id)){
                totalPrice+=seat.price;
            }
        }
        Booking booking=new Booking(new Random().nextInt(1000),userId,totalPrice,seatId,show.getId(),null);
        bookingRepository.addBooking(booking);
        System.out.println("Booking created: "+booking);
        return booking;
    }
    
    /**
     * CONFIRM BOOKING - Final step after payment
     * ------------------------------------------
     * This method confirms a booking after successful payment processing.
     * 
     * WORKFLOW:
     * 1. Validate booking is in CREATED state (not already confirmed/cancelled)
     * 2. Verify all seat locks are still valid and held by the requesting user
     *    - Check if lock hasn't expired (within TTL)
     *    - Check if lock is still owned by the same user
     *    - If validation fails, throw SeatNotAvailableException
     * 3. Set payment type for the booking
     * 4. Process payment using appropriate PaymentStrategy
     * 5. Release all seat locks (as booking is now confirmed)
     * 6. Update booking status to CONFIRMED
     * 
     * SECURITY & VALIDATION:
     * - Prevents users from confirming bookings they don't own
     * - Ensures locks haven't expired before payment
     * - Atomic operation: either all seats are confirmed or none
     * 
     * LOCK RELEASE:
     * - Locks are released after payment to allow others to book
     * - Seat status is implicitly managed through booking records
     * 
     * @param booking - The booking to confirm
     * @param type - Payment method (CARD, UPI, WALLET)
     * @throws IllegalStateException if booking is not in CREATED state
     * @throws SeatNotAvailableException if locks are expired or not owned by user
     */
    public void confirmBooking(Booking booking, PaymentType type){
        if(!booking.getStatus().equals(BookingStatus.CREATED)){
           throw new IllegalStateException("Booking is not in CREATED state");
        }
        for(int seatId:booking.getSelectedSeats()){
            String key=booking.showId+":"+seatId;
            if(lockProvider.isLockExpired(key)||!lockProvider.isLockedBy(key,booking.getUserId())){
                throw new SeatNotAvailableException("Seat "+seatId+" is not locked by user "+booking.userId+" or lock expired");
            }
        }
        booking.setPaymentType(type);
        PaymentStrategy paymentStrategy=PaymentStrategyFactory.getPaymentStrategy(type);
        paymentStrategy.pay(booking);
        //Unlock locked seat
        for(int seatId:booking.getSelectedSeats()){
            String key=booking.showId+":"+seatId;
            lockProvider.unlock(key);
        }
        booking.status=BookingStatus.CONFIRMED;
        System.out.println("Booking confirmed: "+booking);
    }
}

/**
 * LOCK PROVIDER INTERFACE - Distributed Locking Mechanism
 * ========================================================
 * 
 * This interface defines the contract for implementing a distributed locking mechanism
 * to handle concurrent seat booking requests in a thread-safe manner.
 * 
 * PURPOSE:
 * - Prevent race conditions when multiple users try to book the same seat
 * - Provide temporary exclusive access to resources (seats)
 * - Support TTL (Time To Live) to prevent indefinite locking
 * - Enable distributed systems to coordinate access across multiple instances
 * 
 * IMPLEMENTATIONS:
 * 1. InMemoryLockProvider - Uses ConcurrentHashMap for single-instance applications
 * 2. RedisLockProvider - Uses Redis for distributed/multi-instance applications
 * 
 * USE CASE IN BOOKING:
 * - When user initiates booking, locks are acquired on selected seats
 * - User has TTL duration (e.g., 5 seconds) to complete payment
 * - If payment is completed within TTL, locks are released and booking is confirmed
 * - If TTL expires, locks are automatically released and seats become available again
 */
interface LockProvider{
    /**
     * TRY LOCK - Attempt to acquire a lock on a resource
     * --------------------------------------------------
     * Tries to acquire an exclusive lock on a resource identified by 'key'.
     * 
     * BEHAVIOR:
     * - If resource is unlocked or lock has expired: Acquire lock and return true
     * - If resource is already locked by another user: Return false
     * - If same user tries to lock again: Implementation-dependent (typically renew)
     * 
     * TTL (Time To Live):
     * - Lock automatically expires after TTL milliseconds
     * - Prevents deadlocks if user abandons the operation
     * - Background sweeper threads clean up expired locks
     * 
     * ATOMICITY:
     * - Lock check and acquisition must be atomic to prevent race conditions
     * - Use compute() or similar atomic operations
     * 
     * @param key - Unique identifier for the resource (e.g., "showId:seatId")
     * @param userId - ID of the user acquiring the lock
     * @param TTL - Time to live in milliseconds before lock expires
     * @return true if lock was acquired, false otherwise
     */
    boolean tryLock(String key,String userId,long TTL);
    
    /**
     * UNLOCK - Release a lock on a resource
     * -------------------------------------
     * Releases the lock on the specified resource, making it available for others.
     * 
     * WHEN TO USE:
     * - After successful booking confirmation
     * - When user cancels the booking
     * - To manually release locks before TTL expiry
     * 
     * @param key - Unique identifier for the resource to unlock
     */
    void unlock(String key);
    
    /**
     * IS LOCK EXPIRED - Check if a lock has expired
     * ---------------------------------------------
     * Verifies if the lock on a resource has passed its TTL.
     * 
     * RETURNS:
     * - true: Lock has expired or doesn't exist
     * - false: Lock is still valid
     * 
     * @param key - Unique identifier for the resource
     * @return true if lock is expired or doesn't exist, false otherwise
     */
    boolean isLockExpired(String key);
    
    /**
     * IS LOCKED BY - Check lock ownership
     * -----------------------------------
     * Verifies if a specific user currently holds the lock on a resource.
     * 
     * SECURITY:
     * - Prevents users from confirming/modifying bookings they don't own
     * - Ensures only the lock owner can perform operations on locked resources
     * 
     * @param key - Unique identifier for the resource
     * @param userId - ID of the user to check ownership for
     * @return true if the user holds a valid lock, false otherwise
     */
    boolean isLockedBy(String key, String userId);
}
class InMemoryLockProvider implements LockProvider{
    static class Expiry{
        String userId;
        long expiryTime;
        Expiry(String userId, long expiryTime){
            this.userId = userId;
            this.expiryTime = expiryTime;
        }
    }
    ConcurrentHashMap<String, Expiry> lockMap=new ConcurrentHashMap<>();
    ScheduledExecutorService sweeper=Executors.newSingleThreadScheduledExecutor();
    public InMemoryLockProvider(){
        sweeper.scheduleAtFixedRate(this::sweep,100,100,TimeUnit.MILLISECONDS);
    }
    private void sweep(){
        long currentTime=System.currentTimeMillis();
        lockMap.entrySet().removeIf(
                            e -> e.getValue().expiryTime < currentTime
                        );

    }
    public boolean tryLock(String key, String userId, long ttl) {
        long expiryTime = System.currentTimeMillis() + ttl;
        Expiry newExpiry = new Expiry(userId, expiryTime);
    
        return lockMap.compute(key, (k, existing) -> {
            if (existing == null || existing.expiryTime < System.currentTimeMillis()) {
                return newExpiry;
            }
            return existing;
        }) == newExpiry;
    }
    public void unlock(String key) {
        lockMap.remove(key);
    }
    public boolean isLockExpired(String key) {
        Expiry expiry = lockMap.get(key);
        return expiry == null || expiry.expiryTime < System.currentTimeMillis();
    }
    public boolean isLockedBy(String key, String userId) {
        Expiry expiry = lockMap.get(key);
        return expiry != null && expiry.userId.equals(userId);
    }
    
}
class ReddisLockProvider implements LockProvider{
    public boolean tryLock(String key, String userId, long ttl) {
        // Simulate Redis SETNX with expiry
        return true;
    }
    public void unlock(String key) {
        // Simulate Redis DEL
    }
    public boolean isLockExpired(String key) {
        // Simulate checking expiry in Redis
        return false;
    }
    public boolean isLockedBy(String key, String userId) {
        // Simulate checking lock ownership in Redis
        return true;
    }
}
class PaymentStrategyFactory{
    public static PaymentStrategy getPaymentStrategy(PaymentType type){
        switch(type){
            case UPI:
                return new UPIPayment();
            case CARD:
                return new CardPayment();
            default:
                throw new IllegalArgumentException("Unsupported payment type: " + type);
        }
    }
}
interface PaymentStrategy{
    boolean pay(Booking booking);
}
class UPIPayment implements PaymentStrategy{
    public boolean pay(Booking booking){
        // Simulate UPI payment processing
        System.out.println("Processing UPI payment for booking ID: " + booking.id+", Amount: " + booking.amount+", User ID: " + booking.userId+", Show ID: " + booking.showId);
        return true;
    }
}
class CardPayment implements PaymentStrategy{
    public boolean pay(Booking booking){
        // Simulate Card payment processing
        System.out.println("Processing Card payment for booking ID: " + booking.id+", Amount: " + booking.amount+", User ID: " + booking.userId+", Show ID: " + booking.showId);
        return true;
    }
}
class MovieService{
    MovieRepository movieRepository;
    MovieService(MovieRepository movieRepository){
        this.movieRepository = movieRepository;
    }
    public Movie createMovie(int id, String name, int duration){
        Movie movie = new Movie(id, name, duration);
        movieRepository.addMovie(movie);
        return movie;
    }
    public Movie getMovie(int id){
        return movieRepository.getMovie(id);
    }
}
class ShowService{
    ShowRepository showRepository;
    ShowService(ShowRepository showRepository){
        this.showRepository = showRepository;
    }
    public Show createShow(int id, Movie movie, Theater theater, Screen screen, Date startTime,int duration){
        Show show = new Show(id, movie, theater, screen, startTime);
        showRepository.addShow(show);
        return show;
    }
    public Show getShow(int id){
        return showRepository.getShow(id);
    }
    public List<Show> getAllShows(){
        return showRepository.getAll();
    }
    public List<Show> getShowsByTitle(String title){
        List<Show> result = new ArrayList<>();
        for(Show show : showRepository.getAll()){
            if(show.getMovie().getName().equalsIgnoreCase(title)){
                result.add(show);
            }
        }
        return result;
    }
}
class TheaterService{
    TheaterRepository theaterRepository;
    TheaterService(TheaterRepository theaterRepository){
        this.theaterRepository = theaterRepository;
    }
    public Theater createTheater(int id, String name){
        Theater theater = new Theater(id, name);
        theaterRepository.addTheater(theater);
        return theater;
    }
    public Theater getTheater(int id){
        return theaterRepository.getTheater(id);
    }
    public void addSeatsToScreen(int theaterId, int screenId,List<Seat> seatList){
        Theater theater = getTheater(theaterId);
        theater.addSeatsToScreen(screenId, seatList);
    }
}
class MovieRepository{
    Map<Integer,Movie> movies;
    MovieRepository(){
        movies = new HashMap<>();
    }
    public void addMovie(Movie movie){
        movies.put(movie.getId(), movie);
    }
    public Movie getMovie(int id){
        return movies.get(id);
    }
}
class TheaterRepository{
    Map<Integer,Theater> theaters;
    TheaterRepository(){
        theaters = new HashMap<>();
    }
    public void addTheater(Theater theater){
        theaters.put(theater.getId(), theater);
    }
    public Theater getTheater(int id){
        return theaters.get(id);
    }
}
class ShowRepository{
    Map<Integer,Show> shows;
    ShowRepository(){
        shows = new HashMap<>();
    }
    public void addShow(Show show){
        shows.put(show.getId(), show);
    }
    public Show getShow(int id){
        return shows.get(id);
    }
    public List<Show> getAll(){
        return new ArrayList<>(shows.values());
    }
}
class BookingRepository{
    Map<Integer,Booking> bookings;
    BookingRepository(){
        bookings = new HashMap<>();
    }
    public void addBooking(Booking booking){
        bookings.put(booking.id, booking);
    }
    public Booking getBooking(int id){
        return bookings.get(id);
    }
}
class Booking{
    int id;
    String userId;
    double amount;
    List<Integer>selectedSeats;
    int showId;
    BookingStatus status;
    PaymentType paymentType;
    Booking(int id, String userId, double amount, List<Integer> selectedSeats, int showId, PaymentType paymentType){
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.selectedSeats = selectedSeats;
        this.showId = showId;
        this.status = BookingStatus.CREATED;
        this.paymentType = paymentType;
    }
    public void setPaymentType(PaymentType paymentType){
        this.paymentType = paymentType;
    }
    public BookingStatus getStatus(){
        return status;
    }
    public String getUserId(){
        return userId;
    }
    public List<Integer> getSelectedSeats(){
        return selectedSeats;
    }
    public String toString(){
        return "Booking ID: " + id + ", User ID: " + userId + ", Amount: " + amount + ", Show ID: " + showId + ", Status: " + status+ ", Payment Type: " + paymentType+", Seats: " +selectedSeats;
    }
}
class Movie{
    int id;
    String name;
    int duration; // in minutes
    Movie(int id, String name, int duration){
        this.id = id;
        this.name = name;
        this.duration = duration;
    }
    public int getId(){
        return id;
    }
    public String getName(){
        return name;
    }
    public int getDuration(){
        return duration;
    }
}
class Show{
    int id;
    Movie movie;
    Theater theater;
    Screen screen;
    Date startTime;
    Show(int id, Movie movie, Theater theater, Screen screen, Date startTime){
        this.id = id;
        this.movie = movie;
        this.theater = theater;
        this.screen = screen;
        this.startTime = startTime;
    }
    public int getId(){
        return id;
    }
    public Movie getMovie(){
        return movie;
    }
    public Theater getTheater(){
        return theater;
    }
    public Screen getScreen(){
        return screen;
    }
    public String toString(){
        return "Show ID: " + id + ", Movie: " + movie.getName() + ", Theater: " + theater.getName() + ", Screen ID: " + screen.getId() + ", Start Time: " + startTime;
    }   
}
abstract class Seat{
    int id;
    double price;
    Seat(int id, double price){
        this.id = id;
        this.price = price;
    }
    public abstract SeatType getSeatType();
}
class RegularSeat extends Seat{
    RegularSeat(int id, double price){
        super(id, price);
    }
    public SeatType getSeatType(){
        return SeatType.REGULAR;
    }
}
class ReclinerSeat extends Seat{
    ReclinerSeat(int id, double price){
        super(id, price);
    }
    public SeatType getSeatType(){
        return SeatType.RECLINER;
    }
}
class Screen{
    int id;
    Map<Integer,Seat> seats;
    Screen(int id){
        this.id = id;
        seats = new HashMap<>();
    }
    public int getId(){
        return id;
    }
    public List<Seat> getSeats(){
        return new ArrayList<>(seats.values());
    }
}
class Theater{
    int id;
    String name;
    Map<Integer,Screen> screens;
    Theater(int id, String name){
        this.id = id;
        this.name = name;
        screens = new HashMap<>();
    }
    public void addScreen(Screen screen){
        screens.put(screen.getId(), screen);
    }
    public void addSeatsToScreen(int screenId, List<Seat> seatList){
        Screen screen = screens.get(screenId);
        for(Seat seat : seatList){
            screen.seats.put(seat.id, seat);
        }
    }
    public int getId(){
        return id;
    }
    public String getName(){
        return name;
    }
    public Screen getScreen(int id){
        return screens.get(id);
    }
}
public class BookMyShowConcurrency {
    public static void main(String[] args) throws InterruptedException{
        //Repositories
        TheaterRepository theaterRepository = new TheaterRepository();
        MovieRepository movieRepository = new MovieRepository();
        ShowRepository showRepository = new ShowRepository();
        BookingRepository bookingRepository = new BookingRepository();
        
        LockProvider lockProvider = new InMemoryLockProvider();
        //Services
        TheaterService theaterService = new TheaterService(theaterRepository);
        MovieService movieService = new MovieService(movieRepository);
        ShowService showService = new ShowService(showRepository);
        BookingService bookingService = new BookingService(lockProvider, bookingRepository);

        //Create Theater and Screen
        Theater pvr=theaterService.createTheater(1, "PVR Cinemas");
        Screen screen1=new Screen(1);
        pvr.addScreen(screen1);
        theaterService.addSeatsToScreen(1, 1, Arrays.asList(
            new RegularSeat(1, 10.0),
            new RegularSeat(2, 10.0),
            new ReclinerSeat(3, 20.0),
            new ReclinerSeat(4, 20.0)
        ));
        Movie movie=movieService.createMovie(1, "Inception", 148);
        Calendar cal=Calendar.getInstance();
        cal.set(2025, Calendar.JANUARY, 1, 18, 0);
        Date startTime=cal.getTime();
        Show show1=showService.createShow(1, movie, pvr, screen1, startTime, movie.duration);

        System.out.println("\nDemo 1: Search shows for a movie title");
        List<Show> shows=showService.getShowsByTitle("Inception");
        shows.forEach(System.out::println);
        System.out.println("\nDemo 2: 1 User tries to book a seat");
        Booking booking1=bookingService.createBooking("user1", show1, Arrays.asList(1,3));
        bookingService.confirmBooking(booking1, PaymentType.CARD);
        System.out.println("\nDemo 3: 2 Users try to book the same seat concurrently");
        ExecutorService executor=Executors.newFixedThreadPool(2);
        executor.submit(()->{
            try{
                Booking b=bookingService.createBooking("User2", show1, Arrays.asList(2,4));
                Thread.sleep(1000); // Simulate some delay
                bookingService.confirmBooking(b, PaymentType.UPI);
            }catch(Exception e){
                System.out.println("User2 booking failed: "+e.getMessage());
            }
        });
        executor.submit(()->{
            try{
                Booking b=bookingService.createBooking("User1", show1, Arrays.asList(2,6));
                Thread.sleep(500); // Simulate some delay
                bookingService.confirmBooking(b, PaymentType.CARD);
            }catch(Exception e){
                System.out.println("User1 booking failed: "+e.getMessage());
            }
        });
        System.out.println("\nDemo 4: Booking expires after TTL");
        Booking b=bookingService.createBooking("User3", show1, Arrays.asList(8,9));
        System.out.println("User 3 created the booking but did not pay for it");
        Thread.sleep(6000); // Wait for TTL to expire
        System.out.println("User 4 trying to book the same seats after TTL expiry");
        Booking b2=bookingService.createBooking("user4", show1, Arrays.asList(8,9));
        System.out.println("User 4 booking created");
        try{
            System.out.println("User 3 trying to pay post TTL expiry");
            bookingService.confirmBooking(b, PaymentType.UPI);
        }catch(Exception e){
            System.out.println("User3 booking failed: "+e.getMessage());
        }

        try{
            System.out.println("User 4 trying to pay for the booking");
            bookingService.confirmBooking(b2, PaymentType.CARD);
            System.out.println("User 4 booking confirmed");
        }catch(Exception e){
            System.out.println("User4 booking failed: "+e.getMessage());
        }
        executor.shutdown();
    }   
}
enum SeatType{
    REGULAR,
    RECLINER
}
enum PaymentType{
    CARD,
    UPI,
    WALLET
}
enum SeatStatus{
    AVAILABLE,
    BOOKED
}
enum BookingStatus{
    CREATED,
    CONFIRMED,
    CANCELLED
}