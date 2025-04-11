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

        while (running) {
            try {
                TimeUnit.SECONDS.sleep(30); // Simulate sensor delay

                String[] allSpots = parkingLotManager.getSpotIds();
                for (String spotId : allSpots) {
                    if (parkingLotManager.isUserBooked(spotId)) continue;
                    if (parkingLotManager.isSoftLocked(spotId)) continue;

                    String currentStatus = parkingLotManager.getSpotStatus(spotId);
                    if (random.nextDouble() > 0.2) continue; // 20% chance to proceed

                    String newStatus = determineStatus(currentStatus);
                    if (newStatus != null) {
                        // Buffer status update to avoid threading issues
                        parkingLotManager.notifyListeners(spotId, newStatus);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private String determineStatus(String currentStatus) {
        double rand = random.nextDouble();
        
        if (currentStatus.equals("available")) {
            if (rand < 0.05) return "reserved";
            else if (rand < 0.20) return "reserved_occupied";
        } 
        return null;
    }

    public void stop() {
        running = false;
    }
}
