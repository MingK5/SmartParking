package smartparking;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;

public class ParkingSpot {
    private final String id;
    private final AtomicBoolean booked;
    private volatile Timer timer;
    private volatile long expirationTime;
    private final ParkingLotManager manager;
    private volatile boolean softLocked = false;
    private volatile long softLockExpiry = 0;
    private volatile String lockedByUserId = null;
    private volatile String bookedByUserId = null;
    
    public ParkingSpot(String id, ParkingLotManager manager) {
        this.id = id;
        this.booked = new AtomicBoolean(false);
        this.manager = manager;
    }

    public boolean isAvailable() {
        return !booked.get();
    }

    public boolean isBooked() {
        return booked.get();
    }

    public boolean book(long millis, String userId) {
        if (userId == null) return false;
        if (!booked.compareAndSet(false, true)) return false;

        softLocked = false;
        softLockExpiry = 0;
        lockedByUserId = null;
        this.bookedByUserId = userId;

        this.expirationTime = System.currentTimeMillis() + millis;
        startTimers(millis);
        return true;
    }

    public boolean cancelBooking() {
        if (!booked.get()) {
            return false;
        }
        
        // Atomic state change
        if (booked.compareAndSet(true, false)) {
            Timer t = timer;
            if (t != null) {
                t.cancel();
            }
            return true;
        }
        return false;
    }
    
    public synchronized boolean softLock(String userId, long durationMillis) {
    long now = System.currentTimeMillis();
    
    if (!softLocked || now >= softLockExpiry || userId.equals(lockedByUserId)) {
        softLocked = true;
        softLockExpiry = now + durationMillis;
        lockedByUserId = userId;

        // Schedule automatic release of soft lock
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


    public synchronized void releaseSoftLock(String userId) {
        if (userId.equals(lockedByUserId)) {
            softLocked = false;
            softLockExpiry = 0;
            lockedByUserId = null;
        }
    }

    public boolean isSoftLocked(String userId) {
        return softLocked && System.currentTimeMillis() < softLockExpiry && userId.equals(lockedByUserId);
    }

    public synchronized boolean isSoftLockedByAnotherUser(String userId) {
        if (!softLocked || System.currentTimeMillis() >= softLockExpiry) return false;

        // Handle null userId: system simulation has no identity, so treat as different user
        return userId == null || !userId.equals(lockedByUserId);
    }

    public boolean isSoftLockedBy(String userId) {
        return softLocked && Objects.equals(this.lockedByUserId, userId) && System.currentTimeMillis() < softLockExpiry;
    }

    public boolean isSoftLocked() {
        return softLocked && System.currentTimeMillis() < softLockExpiry;
    }

    private synchronized void startTimers(long millis) {
        if (timer != null) timer.cancel();
        timer = new Timer(true);

        long warningDelay = Math.max(0, millis - (15 * 60 * 1000)); // 15 minutes before end

        timer.schedule(new TimerTask() {
            public void run() {
                manager.notifyUser("Warning: Booking for spot " + id + " expires in 15 minutes");
            }
        }, warningDelay);

        timer.schedule(new TimerTask() {
            public void run() {
                if (cancelBooking()) {
                    if (bookedByUserId != null) {
                        manager.markAsUserUnbooked(id, bookedByUserId);
                    }
                    manager.notifyListeners(id, "available");
                    manager.notifyUser("Booking for spot " + id + " has expired");
                }
            }
        }, millis);
    }

    public long getRemainingTime() {
        return booked.get() ? expirationTime - System.currentTimeMillis() : 0;
    }
}