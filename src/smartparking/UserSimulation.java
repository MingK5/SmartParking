package smartparking;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;

// Class to simulate random user behavior for bookings and cancellations
public class UserSimulation {
    private final ParkingLotManager parkingLotManager; // Reference to backend manager
    private final Random random; // Random generator for simulation
    private volatile boolean running; // Flag to control simulation loop

    // Constructor
    public UserSimulation(ParkingLotManager manager) {
        this.parkingLotManager = manager;
        this.random = new Random();
    }

    // Method to start simulation loop
    public void run() {
        running = true;
        boolean bookThisCycle = true; // alternate between booking and cancellation

        while (running) {
            try {
                if (bookThisCycle) {
                    handleBooking(); // Book a slot
                    TimeUnit.SECONDS.sleep(6); // Delay between bookings
                } else {
                    handleCancellation(); // Cancel a slot
                    TimeUnit.SECONDS.sleep(10); // Delay between cancellations
                }

                bookThisCycle = !bookThisCycle; // Switch action for next cycle

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    // Method to simulate booking a random available slot
    private void handleBooking() {
        String[] allSpots = parkingLotManager.getSpotIds();

        // Filter to find all non-booked (available) spots
        String[] availableSpots = Arrays.stream(allSpots)
                .filter(spot -> !parkingLotManager.isBooked(spot))
                .toArray(String[]::new);

        // 30% chance to book a spot
        if (availableSpots.length == 0 || random.nextDouble() >= 0.3) return;

        // Pick a random available spot
        String spot = availableSpots[random.nextInt(availableSpots.length)];
        
        // Randomly determine duration from 1–24 hours
        int hours = random.nextInt(24) + 1;
        
        // 20% chance to convert a 1-hour booking into "30 minutes"
        String label = (hours == 1 && random.nextDouble() < 0.2) ? "30 minutes" : hours + " hours";

        // Send booking request as a "system" user
        parkingLotManager.bookSpot(spot, hours, label, false, "system");
    }

    // Method to simulate cancelling a random system-booked slot
    private void handleCancellation() {
        // Filter only non-user (system) bookings
        String[] systemBooked = Arrays.stream(parkingLotManager.getAllBookedSpots())
                .filter(spot -> !parkingLotManager.isUserBooked(spot))
                .toArray(String[]::new);

        // 20% chance to cancel a system-booked spot
        if (systemBooked.length == 0 || random.nextDouble() >= 0.2) return;
        
        // Pick a random system-booked spot to cancel
        String spot = systemBooked[random.nextInt(systemBooked.length)];
        
        // Cancel booking asynchronously
        parkingLotManager.cancelBooking(spot);
    }

    // Method to stop simulation
    public void stop() {
        running = false;
    }
}
