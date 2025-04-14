package smartparking;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import java.util.stream.Collectors;

// Class to simulate sensor behavior for the smart car parking system
public class SensorSimulation {
    private final ParkingLotManager parkingLotManager; // Reference to backend manager
    private final Random random; // Random generator for simulation
    private volatile boolean running; // Flag to control simulation loop
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // For delayed tasks

    // Constructor
    public SensorSimulation(ParkingLotManager manager) {
        this.parkingLotManager = manager;
        this.random = new Random();
    }

    // Main simulation loop — periodically simulates car arrivals and wrong parking behavior
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
                    
                    // 20% chance a car exit early from system-reserved slot
                    if ("reserved_occupied".equals(currentStatus) && random.nextDouble() < 0.2) {
                        parkingLotManager.notifyListeners(spotId, "reserved");
                        continue;
                    }

                    // 80% chance of a car entering system-reserved slot
                    if ("reserved".equals(currentStatus) && random.nextDouble() < 0.8) {
                        TimeUnit.SECONDS.sleep(5);
                        if (parkingLotManager.isBooked(spotId) &&
                            "reserved".equals(parkingLotManager.getSpotStatus(spotId))) {
                            parkingLotManager.notifyListeners(spotId, "reserved_occupied");
                            System.out.println("🚗 Car entered spot " + spotId);
                        }
                    }
                }

                // 80% chance of wrong parking
                if (random.nextDouble() < 0.8) {
                    simulateWrongParkingCorrection();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    // Tracks already relocated slots
    private final Set<String> correctedSlots = ConcurrentHashMap.newKeySet();

    // Simulate a user parking in the wrong slot and show relocation popup
    private void simulateWrongParkingCorrection() {
        String[] allSpots = parkingLotManager.getSpotIds();
        
        // Get all booked user slots not already corrected
        List<String> userBooked = Arrays.stream(allSpots)
                .filter(parkingLotManager::isUserBooked)
                .filter(spot -> !correctedSlots.contains(spot))
                .collect(Collectors.toList());

        if (userBooked.isEmpty()) return;

        // Shuffle to make wrong spot and user spot truly random
        Collections.shuffle(userBooked);
        String correctSpot = userBooked.get(0); // Pick one slot for this cycle
        correctedSlots.add(correctSpot); 

        String userId = getUserIdForBookedSpot(correctSpot);
        if (userId == null) return;

        // Extract car plate info for the dialog
        Map<String, String> userBookings = parkingLotManager.getUserBookings(userId);
        String carInfo = userBookings.getOrDefault(correctSpot, "unknown");
        String carPlate = carInfo.contains("Plate: ") ? carInfo.split(",")[0].replace("Plate: ", "") : "UNKNOWN";

        // Find a nearby wrong spot that is not booked or occupied
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

        // Schedule relocation logic after delay
        scheduler.schedule(() -> {
            // Double-check that the slot is still booked before applying wrong_parking
            if (!parkingLotManager.isBooked(correctSpot) || !parkingLotManager.isUserBooked(correctSpot)) {
                System.out.println("❌ Skipping relocation — slot " + correctSpot + " was cancelled.");
                parkingLotManager.notifyListeners(wrongSpot, "available"); // clear red if any
                return;
            }

            parkingLotManager.notifyListeners(wrongSpot, "wrong_parking");

            // Display relocation dialog on GUI thread
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                    "⚠ Wrong Parking Detected!\n" +
                    "Plate: " + carPlate + "\n" +
                    "Correct Spot: " + correctSpot + "\n\n" +
                    "Please click OK to relocate the vehicle.",
                    "Relocation Required",
                    JOptionPane.WARNING_MESSAGE
                );

                // Final check on the booking status before showing relocation
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

    // Helper method to get user ID for a booked spot
    private String getUserIdForBookedSpot(String correctSpot) {
        for (String userId : parkingLotManager.getAllUserIds()) {
            Map<String, String> bookings = parkingLotManager.getUserBookings(userId);
            if (bookings.containsKey(correctSpot)) {
                return userId;
            }
        }
        return null;
    }

    // Stop the simulation loop
    public void stop() {
        running = false;
    }
}
