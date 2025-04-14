package smartparking;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.SwingConstants;

// ParkingLotManager as the core backend controller of the smart car parking system
public class ParkingLotManager {
    // Singleton instance
    private static ParkingLotManager instance;

    // Core data structures
    private final ConcurrentMap<String, ParkingSpot> parkingSpots; // Stores all parking spots
    private final ConcurrentMap<String, Consumer<String>> listeners; // Registered slot listeners for GUI updates
    private final ExecutorService notificationExecutor; // For async user notifications
    private final PriorityBlockingQueue<ParkingRequest> bookingQueue; // Incoming booking requests
    private final ScheduledExecutorService monitorExecutor; // For monitoring tasks
    private final Set<String> userBookedSpots; // Track user-booked slots
    private final Map<String, String> spotStatusCache; // Slot status for GUI repaint throttling
    private final LinkedBlockingQueue<Runnable> updateBuffer; // Buffered update queue for thread-safe repainting
    private final Semaphore bookingSemaphore; // Controls max parallel bookings
    private final ReentrantLock cacheLock; // Lock for spot status cache
    private final AtomicInteger bookingsProcessed;
    private final AtomicInteger concurrentBookings;
    private final AtomicInteger failedBookings;
    
    // User booking state
    private final Map<String, Map<String, String>> userBookingDetails = new ConcurrentHashMap<>();
    private final Map<String, UserProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userBookings = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdateTimes = new ConcurrentHashMap<>();

    private GUI gui;

    // Constructor: Initializes managers, threads, data structures
    private ParkingLotManager() {
        this.parkingSpots = new ConcurrentHashMap<>();
        this.listeners = new ConcurrentHashMap<>();
        this.notificationExecutor = Executors.newSingleThreadExecutor();
        this.bookingQueue = new PriorityBlockingQueue<>(11, Comparator.comparingInt(r -> r.isPriority ? 0 : 1));
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor();
        this.userBookedSpots = Collections.synchronizedSet(new HashSet<>());
        this.spotStatusCache = new ConcurrentHashMap<>();
        this.updateBuffer = new LinkedBlockingQueue<>();
        this.bookingSemaphore = new Semaphore(5); // Allows max 5 concurrent bookings
        this.cacheLock = new ReentrantLock(true);
        this.bookingsProcessed = new AtomicInteger();
        this.concurrentBookings = new AtomicInteger();
        this.failedBookings = new AtomicInteger();

        initializeSpots();
        startBookingProcessor();
        startUpdateProcessor(); 
        startMonitoring();
    }

    // Singleton method to get a single instance of Parking Lot Manager
    public static synchronized ParkingLotManager getInstance() {
        if (instance == null) {
            instance = new ParkingLotManager();
        }
        return instance;
    }

    // Initialize all parking slots by zone and attach listeners
    private void initializeSpots() {
        for (char zone = 'A'; zone <= 'F'; zone++) {
            int limit = (zone == 'A' || zone == 'F') ? 14 : 12;
            for (int i = 1; i <= limit; i++) {
                String spotId = zone + String.valueOf(i);
                parkingSpots.put(spotId, new ParkingSpot(spotId, this));
                registerListener(spotId, status -> notifyListeners(spotId, status));
            }
        }
    }

    // Booking thread loop to process queued booking requests
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

    // Background thread to execute all UI updates from buffer
    private void startUpdateProcessor() {
        new Thread(() -> {
            while (true) {
                try {
                    Runnable updateTask = updateBuffer.take();
                    updateTask.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "BufferedUpdateProcessor").start();
    }

    // Monitor thread that periodically logs system state
    private void startMonitoring() {
        monitorExecutor.scheduleAtFixedRate(() -> {
            System.out.println("[Monitor] Queue size: " + bookingQueue.size());
            System.out.println("[Monitor] Active bookings: " + concurrentBookings.get());
        }, 1, 1, TimeUnit.MINUTES);
    }

    // Print current system booking metrics
    public void printSystemStatus() {
        System.out.println("=== System Status ===");
        System.out.println("Total bookings processed: " + bookingsProcessed.get());
        System.out.println("Failed bookings: " + failedBookings.get());
        System.out.println("Current queue size: " + bookingQueue.size());
        System.out.println("Active bookings: " + concurrentBookings.get());
    }

    // Return all spot IDs for a specific zone
    public String[] getSpotsInZone(String zone) {
        return parkingSpots.keySet().stream()
                .filter(id -> id.startsWith(zone))
                .sorted(Comparator.comparingInt(s -> Integer.parseInt(s.substring(1))))
                .toArray(String[]::new);
    }

    // Get current status for a specific spot (based on user ID)
    public String getSpotStatus(String spotId, String userId) {
        cacheLock.lock();
        try {
            if (isUserBooked(spotId)) return "booked";

            if (isSoftLocked(spotId)) {
                if (isSoftLockedByUser(spotId, userId)) {
                    return "available"; // allow the user who locked it to proceed
                } else {
                    return "soft_locked"; // someone else locked it
                }
            }

            return spotStatusCache.getOrDefault(spotId, "available");
        } finally {
            cacheLock.unlock();
        }
    }

    // Overloaded method for system booking in SensorSimulation to get spot status
    public String getSpotStatus(String spotId) {
        return getSpotStatus(spotId, null);
    }
    
    // Register new user and allocate booking map
    public void registerUser(UserProfile profile) {
        userProfiles.put(profile.getUserId(), profile);
        userBookings.putIfAbsent(profile.getUserId(), ConcurrentHashMap.newKeySet());
    }

    // Check if user has hit their booking limit
    public boolean userHasReachedLimit(String userId) {
        UserProfile profile = userProfiles.get(userId);
        Set<String> bookings = userBookings.getOrDefault(userId, Set.of());
        return profile != null && bookings.size() >= profile.getMaxBookingsAllowed();
    }

    // Mark a spot as booked by user and save car details
    public void markAsUserBooked(String spotId, String userId, String carPlate, String duration) {
        userBookings.get(userId).add(spotId);
        userBookingDetails
            .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
            .put(spotId, "Plate: " + carPlate + ", Duration: " + duration);
    }

    // Mark a spot as unbooked after cancellation or expiry
    public void markAsUserUnbooked(String spotId, String userId) {
        userBookings.getOrDefault(userId, Set.of()).remove(spotId);
        Map<String, String> details = userBookingDetails.get(userId);
        if (details != null) details.remove(spotId);
    }

    // Return all booking details of a user
    public Map<String, String> getUserBookings(String userId) {
        return userBookingDetails.getOrDefault(userId, Collections.emptyMap());
    }

    // Check if a spot is currently booked by a user
    public boolean isUserBooked(String spotId) {
        return userBookings.values().stream().anyMatch(set -> set.contains(spotId));
    }

    // Submit a booking request to the queue
    public CompletableFuture<Boolean> bookSpot(String spotId, int hours, String label, boolean isPriority, String userId){
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        bookingQueue.offer(new ParkingRequest(spotId, hours, label, isPriority, future, userId));
        return future;
    }

    // Process a booking request from the queue
    private void processBooking(ParkingRequest request) {
        try {
            bookingSemaphore.acquire(); 
            concurrentBookings.incrementAndGet();

            ParkingSpot spot = parkingSpots.get(request.spotId);
            long millis = "30 minutes".equals(request.label) ? 30 * 60 * 1000L : request.hours * 60L * 60 * 1000L;
            boolean success = spot != null && spot.book(millis, request.userId);
 
            if (success) {
                bookingsProcessed.incrementAndGet();
                if (!"system".equals(request.userId)) {
                    enqueueUpdate(request.spotId, "booked");
                    String readable = "1 hour";
                    if ("30 minutes".equals(request.label)) {
                        readable = "30 minutes";
                    } else if (request.hours > 1) {
                        readable = request.hours + " hours";
                    }
                    enqueueUserMessage("Slot " + request.spotId + " booked for " + readable + ".");
                } else {
                    enqueueUpdate(request.spotId, "reserved");
                    enqueueUserMessage("Slot " + request.spotId + " reserved.");
                }
            } else {
                failedBookings.incrementAndGet();
                enqueueUpdate(request.spotId, "available");
                enqueueUserMessage("Booking failed for spot " + request.spotId);
            }
            request.future.complete(success);
        } catch (InterruptedException e) {
            request.future.completeExceptionally(e);
        } finally {
            concurrentBookings.decrementAndGet();
            bookingSemaphore.release();
        }
    }

    // Cancel a booking and update UI
    public CompletableFuture<Boolean> cancelBooking(String spotId) {
        ParkingSpot spot = parkingSpots.get(spotId);
        if (spot == null) return CompletableFuture.completedFuture(false);

        return CompletableFuture.supplyAsync(() -> {
            boolean result = spot.cancelBooking();
            if (result) {
                String status = getSpotStatus(spotId);
                enqueueUpdate(spotId, "available");
                if (status.equals("reserved") || status.equals("reserved_occupied") ){
                    enqueueUserMessage("Slot " + spotId + " is now available.");
                } else {
                    enqueueUserMessage("Booking for " + spotId + " cancelled.");        
                }
            }
            return result;
        }, notificationExecutor); 
    }

    // ==== Utility and Helper Methods ====
    public String[] getAllBookedSpots() {
        return parkingSpots.entrySet().stream()
                .filter(entry -> entry.getValue().isBooked())
                .map(Map.Entry::getKey)
                .toArray(String[]::new);
    }
    
    public Set<String> getAllUserIds() {
        return userBookings.keySet();
    }
    
    public String[] getSpotIds() {
        return parkingSpots.keySet().toArray(new String[0]);
    }

    public boolean isBooked(String spotId) {
        ParkingSpot spot = parkingSpots.get(spotId);
        return spot != null && spot.isBooked();
    }

    public void registerGUI(GUI gui) {
        this.gui = gui;
    }

    public void notifyUser(String message) {
        enqueueUserMessage(message);
        showPopupMessage(message); 
    }
    
    // Show alert popups for booking expiry or warnings
    private void showPopupMessage(String message) {
        if (gui != null && (message.contains("Warning:") || message.contains("has expired"))) {
            SwingUtilities.invokeLater(() -> {
                try {
                    JDialog dialog = new JDialog(gui, "Alert", true);
                    dialog.setSize(350, 150);
                    dialog.setLocationRelativeTo(gui);

                    JLabel label = new JLabel("<html><center>" + message + "</center></html>", SwingConstants.CENTER);
                    label.setFont(new Font("Arial", Font.BOLD, 14));
                    dialog.add(label, BorderLayout.CENTER);

                    JButton okButton = new JButton("OK");
                    okButton.addActionListener(e -> dialog.dispose());
                    JPanel buttonPanel = new JPanel();
                    buttonPanel.add(okButton);
                    dialog.add(buttonPanel, BorderLayout.SOUTH);

                    dialog.setVisible(true);
                } catch (Exception ex) {
                    System.err.println("⚠️ Failed to show popup message: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
        }
    }

    // Attempt soft lock by user
    public boolean trySoftLock(String spotId, String userId, long millis) {
        ParkingSpot spot = parkingSpots.get(spotId);
        if (spot == null) return false;

        String status = getSpotStatus(spotId); // No userId — we check real-time view
        if (!"available".equals(status)) return false; // ❗ Prevent locking system-reserved

        boolean locked = spot.softLock(userId, millis);
        if (locked) {
            enqueueUpdate(spotId, "soft_locked");
        }
        return locked;
    }

    
    // Release a soft lock if the user currently holds it.
    public void releaseSoftLock(String spotId, String userId) {
        ParkingSpot spot = parkingSpots.get(spotId);
        if (spot != null) spot.releaseSoftLock(userId);
    }
    
    // Check if the spot is currently under any soft lock (regardless of user)
    public boolean isSoftLocked(String spotId) {
        ParkingSpot spot = parkingSpots.get(spotId);
        return spot != null && spot.isSoftLocked();
    }

    // Check if the current user holds the soft lock on the given spot
    public boolean isSoftLockedByUser(String spotId, String userId) {
        ParkingSpot spot = parkingSpots.get(spotId);
        return spot != null && spot.isSoftLockedBy(userId);
    }

    // Check if the spot is soft-locked by another user (not the current one)
    public boolean isSoftLockedByAnotherUser(String spotId, String userId) {
        ParkingSpot spot = parkingSpots.get(spotId);
        return spot != null && spot.isSoftLockedByAnotherUser(userId);
    }

    // Register a status update listener for a specific spot
    public void registerListener(String spotId, Consumer<String> listener) {
        listeners.put(spotId, listener);
    }

    // Notify registered listeners that the status of a spot has changed
    public void notifyListeners(String spotId, String status) {
        enqueueUpdate(spotId, status);
    }

    // === Buffered Update System ===
    private void enqueueUpdate(String spotId, String status) {
        long now = System.currentTimeMillis();
        long lastUpdate = lastUpdateTimes.getOrDefault(spotId, 0L);

        // Skip if update is too soon for this slot (less than 0.5 seconds)
        if (now - lastUpdate < 500) {
            System.out.println("Throttled UI update for " + spotId + " (" + status + ")");
            return;
        }

        // Update the timestamp
        lastUpdateTimes.put(spotId, now);
        
        updateBuffer.offer(() -> {
            cacheLock.lock();
            try {
                String current = spotStatusCache.get(spotId);
                if (status.equals(current)) return; // skip duplicate
                spotStatusCache.put(spotId, status);
            } finally {
                cacheLock.unlock();
            }

            Consumer<String> listener = listeners.get(spotId);
            if (listener != null) listener.accept(status);

            if (gui != null) gui.updateSlotStatus(spotId, status);
        });
    }

    private void enqueueUserMessage(String message) {
        updateBuffer.offer(() -> {
            if (gui != null) gui.displayNotification(message);
        });
    }
    
    // Force-close active booking dialogs in GUI when soft lock expires
    public void forceCloseBookingDialogs() {
        if (gui != null) {
            gui.closeBookingDialogs(); 
        }
    }
    
    // Prompt user to acknowledge expiration (time exceed)
    public void promptUserToAcknowledgeExpiry(String spotId, String userId) {
        enqueueUpdate(spotId, "time_exceeded");

        SwingUtilities.invokeLater(() -> {
            try {
                JDialog dialog = new JDialog(gui, "Booking Expired", true);
                dialog.setSize(350, 160);
                dialog.setLocationRelativeTo(gui);

                JLabel label = new JLabel("<html><center>Booking for spot " + spotId + " has expired.<br>Please acknowledge to release the spot.</center></html>", SwingConstants.CENTER);
                label.setFont(new Font("Arial", Font.BOLD, 14));
                dialog.add(label, BorderLayout.CENTER);

                JButton okButton = new JButton("OK");
                okButton.addActionListener(e -> {
                    dialog.dispose();
                    cleanupAfterExpiry(spotId, userId);
                });

                JPanel buttonPanel = new JPanel();
                buttonPanel.add(okButton);
                dialog.add(buttonPanel, BorderLayout.SOUTH);

                dialog.setVisible(true);
            } catch (Exception ex) {
                System.err.println("⚠️ Failed to prompt expiry acknowledgment for " + spotId);
                ex.printStackTrace();
            }
        });
    }
    
    // Clear expired booking after user confirmation
    private void cleanupAfterExpiry(String spotId, String userId) {
        ParkingSpot spot = parkingSpots.get(spotId);
        if (spot != null) {
            spot.cancelBooking(); // clear booking state
            if (userId != null && !"system".equals(userId)) {
                markAsUserUnbooked(spotId, userId);
            }
            notifyListeners(spotId, "available"); // white slot
            notifyUser("Spot " + spotId + " is now available.");
        }
    }

    // Inner Class to store booking request details
    private static class ParkingRequest {
        final String spotId;
        final int hours;
        final String label;
        final boolean isPriority;
        final CompletableFuture<Boolean> future;
        final String userId; 

        ParkingRequest(String spotId, int hours, String label, boolean isPriority, CompletableFuture<Boolean> future, String userId) {
            this.spotId = spotId;
            this.hours = hours;
            this.label = label;
            this.isPriority = isPriority;
            this.future = future;
            this.userId = userId;
        }
    }

}
