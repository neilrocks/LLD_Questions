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
interface LockProvider{
    boolean tryLock(String key,String userId,long TTL);
    void unlock(String key);
    boolean isLockExpired(String key);
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