package smartparking;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class SensorSimulation {
    private final ParkingLotManager parkingLotManager;
    private final Random random;
    private volatile boolean running;

    public SensorSimulation(ParkingLotManager manager) {
        this.parkingLotManager = manager;
        this.random = new Random();
    }

    public void run() {
        running = true;
        String[] allSpots = parkingLotManager.getSpotIds();
        
        while (running) {
            try {
                // Change to 30 second intervals for less frequent changes
                TimeUnit.SECONDS.sleep(30);
                
                // Process each spot with controlled probabilities
                for (String spotId : allSpots) {
                    // Skip user-booked spots
                    if (parkingLotManager.isUserBooked(spotId)) {
                        continue;
                    }
                    
                    String currentStatus = parkingLotManager.getSpotStatus(spotId);
                    
                    // Only change status with 20% probability to make changes less frequent
                    if (random.nextDouble() > 0.2) {
                        continue;
                    }
                    
                    // Status transition logic
                    String newStatus;
                    double rand = random.nextDouble();

                    if (currentStatus.equals("available")) {
                        // Available spots:
                        // - 5% chance to become reserved
                        // - 15% chance to become reserved_occupied
                        // - 80% chance to stay available
                        if (rand < 0.05) {
                            newStatus = "reserved";
                        } else if (rand < 0.20) { // 15% cumulative (5-20%)
                            newStatus = "reserved_occupied";
                        } else {
                            continue; // 80% chance to stay available
                        }
                    } else {
                        // Non-available spots:
                        // - 40% chance reserved
                        // - 20% chance reserved_occupied
                        // - 20% chance time_exceeded
                        // - 20% chance wrong_parking
                        if (rand < 0.40) {
                            newStatus = "reserved";
                        } else if (rand < 0.60) { // 20% cumulative (40-60%)
                            newStatus = "reserved_occupied";
                        } else if (rand < 0.80) { // 20% cumulative (60-80%)
                            newStatus = "time_exceeded";
                        } else { // 20% cumulative (80-100%)
                            newStatus = "wrong_parking";
                        }
                    }
                    
                    // Update the spot status
                    parkingLotManager.notifyListeners(spotId, newStatus);
                }
            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop() {
        running = false;
    }
}