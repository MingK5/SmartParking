package smartparking;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;

// Represents an individual parking slot in the smart car parking system
public class ParkingSpot {
    private final String id;
    private final AtomicBoolean booked; // Flag to indicate if the spot is booked
    private volatile Timer timer; // Timer for booking expiration
    private volatile long expirationTime; // Expiration time in ms
    private final ParkingLotManager manager; // Reference to system manager
    private TimerTask warningTask; // Timer task that triggers a warning 15 minutes before booking expires
    private TimerTask expiryTask; // Timer task that triggers when the booking fully expires
    
    // Soft-lock related fields
    private volatile boolean softLocked = false;
    private volatile long softLockExpiry = 0;
    private volatile String lockedByUserId = null;
    private volatile String bookedByUserId = null;
    
    // Constructor to initialize parking spot with unique ID and reference to manager
    public ParkingSpot(String id, ParkingLotManager manager) {
        this.id = id;
        this.booked = new AtomicBoolean(false);
        this.manager = manager;
    }

    // Method to check if the spot is available (not booked)
    public boolean isAvailable() {
        return !booked.get();
    }

    // Method to check if the spot is currently booked
    public boolean isBooked() {
        return booked.get();
    }

    // Method to book the spot for a given duration (in milliseconds)
    public boolean book(long millis, String userId) {
        if (userId == null) return false;
        if (!booked.compareAndSet(false, true)) return false;

        // Clear soft lock and assign booking details
        softLocked = false;
        softLockExpiry = 0;
        lockedByUserId = null;
        this.bookedByUserId = userId;

        this.expirationTime = System.currentTimeMillis() + millis;
        startTimers(millis); // Start countdown timers
        return true;
    }

    // Method to cancel the current booking (if any)
    public boolean cancelBooking() {
        if (!booked.get()) {
            return false;
        }
        
        // Atomic state change
        if (booked.compareAndSet(true, false)) {
            if (warningTask != null) warningTask.cancel();
            if (expiryTask != null) expiryTask.cancel();
            if (timer != null) timer.cancel();
            return true;
        }
        return false;
    }
    
    // Method to attempt a soft lock on the spot by a specific user
    public synchronized boolean softLock(String userId, long durationMillis) {
        long now = System.currentTimeMillis();
    
        if (!softLocked || now >= softLockExpiry || userId.equals(lockedByUserId)) {
            softLocked = true;
            softLockExpiry = now + durationMillis;
            lockedByUserId = userId;

            // Schedule automatic soft lock release
            new Timer(true).schedule(new TimerTask() {
                public void run() {
                    synchronized (ParkingSpot.this) {
                        if (softLocked && System.currentTimeMillis() >= softLockExpiry &&
                            userId.equals(lockedByUserId) && !booked.get()) {
                        
                            releaseSoftLock(userId);
                            manager.notifyUser("Your hold on " + id + " has expired.");
                            manager.forceCloseBookingDialogs();
                            manager.notifyListeners(id, "available");  // GUI will repaint
                        }
                    }
                }
            }, durationMillis);
            return true;
        }
        return false;
    }

    // Method to release the soft lock (only by the same user)
    public synchronized void releaseSoftLock(String userId) {
        if (userId.equals(lockedByUserId)) {
            softLocked = false;
            softLockExpiry = 0;
            lockedByUserId = null;
        }
    }

    // Method to check if the spot is currently soft-locked by the same user
    public boolean isSoftLocked(String userId) {
        return softLocked && System.currentTimeMillis() < softLockExpiry && userId.equals(lockedByUserId);
    }

    // Method to check if the spot is soft-locked by another user
    // Used to reject booking attempts or UI actions if the current user is not the lock owner
    public synchronized boolean isSoftLockedByAnotherUser(String userId) {
        if (!softLocked || System.currentTimeMillis() >= softLockExpiry) return false;
        return userId == null || !userId.equals(lockedByUserId);
    }

    // Method to check if the soft lock belongs to a specific user ID (general checking)
    public boolean isSoftLockedBy(String userId) {
        return softLocked && Objects.equals(this.lockedByUserId, userId) && System.currentTimeMillis() < softLockExpiry;
    }

    // Method to check if the spot is soft-locked by anyone and still valid
    // Does not care who the user is — just whether the lock is active
    public boolean isSoftLocked() {
        return softLocked && System.currentTimeMillis() < softLockExpiry;
    }

    // Internal method to start timers for warnings and expiration
    private synchronized void startTimers(long millis) {
        if (timer != null) timer.cancel();
        timer = new Timer(true);

        long warningDelay = Math.max(0, millis - (15 * 60 * 1000)); // Warn 15 mins before expiry

        // Schedule expiration warning (user bookings only)
        warningTask = new TimerTask() {
            public void run() {
                if (booked.get() && bookedByUserId != null && !"system".equals(bookedByUserId)) {
                    manager.notifyUser("Warning: Booking for spot " + id + " expires in 15 minutes");
                }
            }
        };
        timer.schedule(warningTask, warningDelay);

        // Schedule booking expiration
        expiryTask = new TimerTask() {
            public void run() {
                if (cancelBooking()) {
                    if (bookedByUserId != null && !"system".equals(bookedByUserId)) {
                        manager.markAsUserUnbooked(id, bookedByUserId);
                    }
                    manager.notifyListeners(id, "time_exceeded");
                    manager.promptUserToAcknowledgeExpiry(id, bookedByUserId);
                }
            }
        };
        timer.schedule(expiryTask, millis);
    }

    // Method to get the remaining time for the booking
    public long getRemainingTime() {
        return booked.get() ? expirationTime - System.currentTimeMillis() : 0;
    }
}