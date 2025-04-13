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
        boolean bookThisCycle = true; // alternate actions

        while (running) {
            try {
                if (bookThisCycle) {
                    handleBooking();
                    TimeUnit.SECONDS.sleep(6); // slightly slower
                } else {
                    handleCancellation();
                    TimeUnit.SECONDS.sleep(3); // faster cancelation
                }

                bookThisCycle = !bookThisCycle; // switch to other action

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private void handleBooking() {
        String[] allSpots = parkingLotManager.getSpotIds();

        String[] availableSpots = Arrays.stream(allSpots)
                .filter(spot -> !parkingLotManager.isBooked(spot))
                .toArray(String[]::new);

        if (availableSpots.length == 0 || random.nextDouble() >= 0.3) return;

        String spot = availableSpots[random.nextInt(availableSpots.length)];
        int hours = random.nextInt(24) + 1;
        String label = (hours == 1 && random.nextDouble() < 0.2) ? "30 minutes" : hours + " hours";

        parkingLotManager.bookSpot(spot, hours, label, false, "system");
    }

    private void handleCancellation() {
        String[] systemBooked = Arrays.stream(parkingLotManager.getAllBookedSpots())
                .filter(spot -> !parkingLotManager.isUserBooked(spot))
                .toArray(String[]::new);

        if (systemBooked.length == 0 || random.nextDouble() >= 0.2) return;
        
        String spot = systemBooked[random.nextInt(systemBooked.length)];
        parkingLotManager.cancelBooking(spot);
    }

    public void stop() {
        running = false;
    }
}
