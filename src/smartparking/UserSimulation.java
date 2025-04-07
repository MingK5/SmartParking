package smartparking;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;

public class UserSimulation {
    private final ParkingLotManager parkingLotManager;
    private final Random random;
    private volatile boolean running;

    public UserSimulation(ParkingLotManager manager) {
        this.parkingLotManager = manager;
        this.random = new Random();
    }

    public void run() {
        running = true;

        while (running) {
            try {
                TimeUnit.SECONDS.sleep(30); // simulate normal user behavior interval

                String[] allSpots = parkingLotManager.getSpotIds();

                // Try booking a random available spot
                String[] availableSpots = Arrays.stream(allSpots)
                        .filter(spot -> !parkingLotManager.isBooked(spot))
                        .toArray(String[]::new);

                if (availableSpots.length > 0 && random.nextDouble() < 0.3) {
                    String spot = availableSpots[random.nextInt(availableSpots.length)];
                    int hours = random.nextInt(24) + 1;
                    String label;
                    if (hours == 1 && random.nextDouble() < 0.2) {
                        label = "30 minutes";
                    } else {
                        label = hours + " hours";
                    }
                    parkingLotManager.bookSpot(spot, hours, label, false, null);
                }

                // Cancel a random non-user booking
                String[] systemBooked = Arrays.stream(parkingLotManager.getAllBookedSpots())
                        .filter(spot -> !parkingLotManager.isUserBooked(spot))
                        .toArray(String[]::new);

                if (systemBooked.length > 0 && random.nextDouble() < 0.2) {
                    String spot = systemBooked[random.nextInt(systemBooked.length)];
                    parkingLotManager.cancelBooking(spot); // Will run via async logic
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    public void stop() {
        running = false;
    }
}
