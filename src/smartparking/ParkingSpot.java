package smartparking;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class ParkingSpot {
    private final String id;
    private final AtomicBoolean booked;
    private volatile Timer timer;
    private volatile long expirationTime;
    private final ParkingLotManager manager;

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

    public boolean book(int hours) {
        if (!booked.compareAndSet(false, true)) {
            return false;
        }
        
        this.expirationTime = System.currentTimeMillis() + hours * 60 * 60 * 1000;
        startTimers(hours);
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

    private synchronized void startTimers(int hours) {
        Timer t = timer;
        if (t != null) {
            t.cancel();
        }
        
        timer = new Timer(true);
        
        // Warning timer
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                manager.notifyUser("Warning: Booking for spot " + id + " expires in 15 minutes");
            }
        }, Math.max(0, (hours * 60L - 15) * 60 * 1000));

        // Expiration timer
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (cancelBooking()) {
                    manager.notifyListeners(id, "available");
                    manager.notifyUser("Booking for spot " + id + " has expired");
                }
            }
        }, hours * 60 * 60 * 1000);
    }

    public long getRemainingTime() {
        return booked.get() ? expirationTime - System.currentTimeMillis() : 0;
    }
}