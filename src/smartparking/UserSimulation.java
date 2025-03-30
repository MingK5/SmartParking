package smartparking;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;

public class UserSimulation {
    private final ParkingLotManager parkingLotManager;
    private final Random random;
    private volatile boolean running;
    private int requestRate = 1000;

    public UserSimulation(ParkingLotManager manager) {
        this.parkingLotManager = manager;
        this.random = new Random();
    }

    public void run() {
        running = true;
        String[] allSpots = parkingLotManager.getSpotIds();
        
        while (running) {
            try {
                TimeUnit.MILLISECONDS.sleep(requestRate);
                
                // Only book spots that aren't already booked
                String[] availableSpots = Arrays.stream(allSpots)
                    .filter(spot -> !parkingLotManager.isBooked(spot))
                    .toArray(String[]::new);
                
                if (availableSpots.length > 0 && random.nextDouble() < 0.3) {
                    String spot = availableSpots[random.nextInt(availableSpots.length)];
                    parkingLotManager.bookSpot(spot, random.nextInt(24) + 1, false)
                        .thenAccept(success -> {
                            if (success) {
                                requestRate = Math.max(200, requestRate - 100);
                            } else {
                                requestRate = Math.min(5000, requestRate + 500);
                            }
                        });
                }
                
                // Only cancel bookings that weren't made by the user
                String[] systemBookedSpots = Arrays.stream(parkingLotManager.getAllBookedSpots())
                    .filter(spot -> !parkingLotManager.isUserBooked(spot))
                    .toArray(String[]::new);
                
                if (systemBookedSpots.length > 0 && random.nextDouble() < 0.2) {
                    String spotToCancel = systemBookedSpots[random.nextInt(systemBookedSpots.length)];
                    parkingLotManager.cancelBooking(spotToCancel);
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