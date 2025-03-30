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
                // Adaptive sleep based on system load
                TimeUnit.MILLISECONDS.sleep(100 + random.nextInt(400));
                
                String randomSpot = allSpots[random.nextInt(allSpots.length)];
                
                if (parkingLotManager.isBooked(randomSpot)) {
                    parkingLotManager.notifyListeners(randomSpot, "booked_correct");
                } else if (random.nextDouble() < 0.1) {
                    parkingLotManager.notifyListeners(randomSpot, "wrong_parking");
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