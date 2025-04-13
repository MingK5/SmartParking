package smartparking;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.util.stream.Collectors;

public class SensorSimulation {
    private final ParkingLotManager parkingLotManager;
    private final Random random;
    private volatile boolean running;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public SensorSimulation(ParkingLotManager manager) {
        this.parkingLotManager = manager;
        this.random = new Random();
    }

    public void run() {
        running = true;

        while (running) {
            try {
                TimeUnit.SECONDS.sleep(30); // Simulate sensor polling interval

                String[] allSpots = parkingLotManager.getSpotIds();
                for (String spotId : allSpots) {
                    // Skip user bookings and soft-locked slots
                    if (parkingLotManager.isUserBooked(spotId)) continue;
                    if (parkingLotManager.isSoftLocked(spotId)) continue;

                    String currentStatus = parkingLotManager.getSpotStatus(spotId);

                    // Only proceed for system-reserved spots
                    if (!"reserved".equals(currentStatus)) continue;

                    // Simulate 40% chance of car arriving after being reserved
                    if (random.nextDouble() < 0.4) {
                        TimeUnit.SECONDS.sleep(5); // simulate delay

                        if (parkingLotManager.isBooked(spotId) &&
                            "reserved".equals(parkingLotManager.getSpotStatus(spotId))) {
                            parkingLotManager.notifyListeners(spotId, "reserved_occupied");
                        }
                    }

                    // Simulate wrong parking scenario occasionally
                    if (random.nextDouble() < 0.80) {
                        simulateWrongParkingCorrection();
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private final Set<String> correctedSlots = ConcurrentHashMap.newKeySet();

    private void simulateWrongParkingCorrection() {
        String[] allSpots = parkingLotManager.getSpotIds();
        List<String> userBooked = Arrays.stream(allSpots)
                .filter(parkingLotManager::isUserBooked)
                .filter(spot -> !correctedSlots.contains(spot))
                .collect(Collectors.toList());

        if (userBooked.isEmpty()) return;

        // Shuffle to make wrong spot and user spot truly random
        Collections.shuffle(userBooked);
        String correctSpot = userBooked.get(0); // Pick one for this cycle
        correctedSlots.add(correctSpot); 

        String userId = getUserIdForBookedSpot(correctSpot);
        if (userId == null) return;

        Map<String, String> userBookings = parkingLotManager.getUserBookings(userId);
        String carInfo = userBookings.getOrDefault(correctSpot, "unknown");
        String carPlate = carInfo.contains("Plate: ") ? carInfo.split(",")[0].replace("Plate: ", "") : "UNKNOWN";

        // Randomize available wrong spots
        List<String> availableWrongSpots = Arrays.stream(allSpots)
                .filter(spot -> !spot.equals(correctSpot))
                .filter(spot -> !parkingLotManager.isUserBooked(spot)) // ✅ prevent hijacking user slots
                .filter(spot -> !"booked".equals(parkingLotManager.getSpotStatus(spot))) // ✅ exclude visually green
                .filter(spot -> !"reserved_occupied".equals(parkingLotManager.getSpotStatus(spot))) // ✅ exclude gray+car
                .filter(spot -> !"booked_occupied".equals(parkingLotManager.getSpotStatus(spot))) // ✅ exclude green+car
                .collect(Collectors.toList());

        if (availableWrongSpots.isEmpty()) return;
        Collections.shuffle(availableWrongSpots);
        String wrongSpot = availableWrongSpots.get(0);

        // Schedule with delay
        scheduler.schedule(() -> {
            // Double-check that the slot is still booked
            if (!parkingLotManager.isBooked(correctSpot) || !parkingLotManager.isUserBooked(correctSpot)) {
                System.out.println("❌ Skipping relocation — slot " + correctSpot + " was cancelled.");
                parkingLotManager.notifyListeners(wrongSpot, "available"); // clear red if any
                return;
            }

            parkingLotManager.notifyListeners(wrongSpot, "wrong_parking");

            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                    "⚠ Wrong Parking Detected!\n" +
                    "Plate: " + carPlate + "\n" +
                    "Correct Spot: " + correctSpot + "\n\n" +
                    "Please click OK to relocate the vehicle.",
                    "Relocation Required",
                    JOptionPane.WARNING_MESSAGE
                );

                // Check again just before relocating
                if (!parkingLotManager.isBooked(correctSpot) || !parkingLotManager.isUserBooked(correctSpot)) {
                    System.out.println("❌ Relocation skipped — booking was cancelled for " + correctSpot);
                    parkingLotManager.notifyListeners(wrongSpot, "available");
                } else {
                    parkingLotManager.notifyListeners(wrongSpot, "available");
                    parkingLotManager.notifyListeners(correctSpot, "booked_occupied");
                }
            });
        }, 15, TimeUnit.SECONDS);
    }

    // Extracted helper to get userId from a booked spot
    private String getUserIdForBookedSpot(String correctSpot) {
        for (String userId : parkingLotManager.getAllUserIds()) {
            Map<String, String> bookings = parkingLotManager.getUserBookings(userId);
            if (bookings.containsKey(correctSpot)) {
                return userId;
            }
        }
        return null;
    }

    public void stop() {
        running = false;
    }
}
