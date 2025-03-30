package smartparking;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ParkingLotManager {
    private static ParkingLotManager instance;
    private final ConcurrentMap<String, ParkingSpot> parkingSpots;
    private final ConcurrentMap<String, Consumer<String>> listeners;
    private final ExecutorService notificationExecutor;
    private final PriorityBlockingQueue<ParkingRequest> bookingQueue;
    private final ScheduledExecutorService monitorExecutor;
    private GUI gui;
    private final Random random = new Random();
    private final Map<String, String> spotStatusCache = new ConcurrentHashMap<>();
    private final Set<String> userBookedSpots = Collections.synchronizedSet(new HashSet<>());
    
    // Metrics
    private final AtomicInteger bookingsProcessed = new AtomicInteger();
    private final AtomicInteger concurrentBookings = new AtomicInteger();
    private final AtomicInteger failedBookings = new AtomicInteger();

    private ParkingLotManager() {
        this.parkingSpots = new ConcurrentHashMap<>();
        this.listeners = new ConcurrentHashMap<>();
        this.notificationExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        this.bookingQueue = new PriorityBlockingQueue<>(11, 
                Comparator.comparingInt(req -> req.isPriority ? 0 : 1));
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor();
        initializeSpots();
        startBookingProcessor();
        startMonitoring();
    }

    public static synchronized ParkingLotManager getInstance() {
        if (instance == null) {
            instance = new ParkingLotManager();
        }
        return instance;
    }

    private void initializeSpots() {
        for (char zone = 'A'; zone <= 'F'; zone++) {
            int limit = (zone == 'A' || zone == 'F') ? 14 : 12;
            for (int i = 1; i <= limit; i++) {
                String spotId = String.valueOf(zone) + i;
                parkingSpots.put(spotId, new ParkingSpot(spotId, this));
                registerListener(spotId, status -> notifyListeners(spotId, status));
            }
        }
    }

    private void startBookingProcessor() {
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    ParkingRequest request = bookingQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (request != null) {
                        processBooking(request);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "BookingProcessor").start();
    }

    private void startMonitoring() {
        monitorExecutor.scheduleAtFixedRate(() -> {
            System.out.println("[Monitor] Queue size: " + bookingQueue.size());
            System.out.println("[Monitor] Active bookings: " + concurrentBookings.get());
        }, 1, 1, TimeUnit.MINUTES);
    }

    public void printSystemStatus() {
        System.out.println("=== System Status ===");
        System.out.println("Total bookings processed: " + bookingsProcessed.get());
        System.out.println("Failed bookings: " + failedBookings.get());
        System.out.println("Current queue size: " + bookingQueue.size());
        System.out.println("Active bookings: " + concurrentBookings.get());
    }

    public String[] getSpotsInZone(String zone) {
        return parkingSpots.keySet().stream()
                .filter(spotId -> spotId.startsWith(zone))
                .sorted(Comparator.comparingInt(s -> Integer.parseInt(s.substring(1))))
                .toArray(String[]::new);
    }

    public String getSpotStatus(String spotId) {
        ParkingSpot spot = parkingSpots.get(spotId);
        if (spot == null) return "available";
        
        // Only change status with 20% probability to make changes more gradual
        if (random.nextDouble() > 0.2 && spotStatusCache.containsKey(spotId)) {
            return spotStatusCache.get(spotId);
        }
        
        String status;
        if (spot.isBooked()) {
            if (isUserBooked(spotId)) {
                status = "booked"; // Always show user bookings as green
            } else {
                // Other bookings show as reserved
                if (random.nextDouble() < 0.3) {
                    status = "reserved_occupied";
                } else {
                    status = "reserved";
                }
            }
        } else if (random.nextDouble() < 0.05) {
            status = "wrong_parking";
        } else {
            status = "available";
        }
        
        spotStatusCache.put(spotId, status);
        return status;
    }

    public void markAsUserBooked(String spotId) {
        userBookedSpots.add(spotId);
        notifyListeners(spotId, "booked");
    }

    public void markAsUserUnbooked(String spotId) {
        userBookedSpots.remove(spotId);
        notifyListeners(spotId, "available");
    }

    public boolean isUserBooked(String spotId) {
        return userBookedSpots.contains(spotId);
    }

    public CompletableFuture<Boolean> bookSpot(String spotId, int hours, boolean isPriority) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        bookingQueue.put(new ParkingRequest(spotId, hours, isPriority, future));
        return future;
    }

    private void processBooking(ParkingRequest request) {
        concurrentBookings.incrementAndGet();
        try {
            ParkingSpot spot = parkingSpots.get(request.spotId);
            boolean success = spot != null && spot.book(request.hours);
            
            if (success) {
                notifyListeners(request.spotId, "booked");
                notifyUser("Slot " + request.spotId + " booked for " + request.hours + " hours.");
                bookingsProcessed.incrementAndGet();
            } else {
                failedBookings.incrementAndGet();
                notifyUser("Booking failed for spot " + request.spotId);
            }
            request.future.complete(success);
        } finally {
            concurrentBookings.decrementAndGet();
        }
    }

    public CompletableFuture<Boolean> cancelBooking(String spotId) {
        ParkingSpot spot = parkingSpots.get(spotId);
        if (spot == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            if (spot.cancelBooking()) {
                notifyListeners(spotId, "available");
                notifyUser("Booking for " + spotId + " canceled.");
                return true;
            }
            return false;
        });
    }

    public String[] getAllBookedSpots() {
        return parkingSpots.entrySet().stream()
                .filter(entry -> entry.getValue().isBooked())
                .map(Map.Entry::getKey)
                .toArray(String[]::new);
    }

    public boolean isBooked(String spotId) {
        ParkingSpot spot = parkingSpots.get(spotId);
        return spot != null && spot.isBooked();
    }

    public String[] getSpotIds() {
        return parkingSpots.keySet().toArray(new String[0]);
    }

    public void registerGUI(GUI gui) {
        this.gui = gui;
    }

    public synchronized void notifyUser(String message) {
        if (gui != null) {
            gui.displayNotification(message);
        }
    }

    public void registerListener(String spotId, Consumer<String> listener) {
        listeners.put(spotId, listener);
    }

    public void notifyListeners(String spotId, String status) {
        Consumer<String> listener = listeners.get(spotId);
        if (listener != null) {
            notificationExecutor.execute(() -> listener.accept(status));
        }
        if (gui != null) {
            gui.updateSlotStatus(spotId, status);
        }
    }
    
    private static class ParkingRequest {
        final String spotId;
        final int hours;
        final boolean isPriority;
        final CompletableFuture<Boolean> future;

        ParkingRequest(String spotId, int hours, boolean isPriority, CompletableFuture<Boolean> future) {
            this.spotId = spotId;
            this.hours = hours;
            this.isPriority = isPriority;
            this.future = future;
        }
    }
}