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
    private final Set<String> userSimulatedSlots = ConcurrentHashMap.newKeySet(); // Track user-booked slots that have been simulated

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
                
                // === SYSTEM-RESERVED SLOT BEHAVIOR ===
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

                // === USER-BOOKED SLOT BEHAVIOR ===
                // 50% chance of correct/wrong parking per user-booked slot
                for (String spotId : allSpots) {
                    if (!parkingLotManager.isUserBooked(spotId)) continue;
                    if (userSimulatedSlots.contains(spotId)) continue;

                    userSimulatedSlots.add(spotId); // Prevent re-simulating
                    scheduler.schedule(() -> {
                        boolean simulateCorrectParking = random.nextBoolean(); // 50%
                        if (simulateCorrectParking) {
                            parkingLotManager.notifyListeners(spotId, "booked_occupied");
                        } else {
                            simulateWrongParkingCorrection(spotId);
                        }
                    }, 15, TimeUnit.SECONDS); // Delay decision by 15 seconds
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    // Simulate a user parking in the wrong slot and show relocation popup
    private void simulateWrongParkingCorrection(String correctSpot) {
        String userId = getUserIdForBookedSpot(correctSpot);
        if (userId == null) return;

        Map<String, String> userBookings = parkingLotManager.getUserBookings(userId);
        String carInfo = userBookings.getOrDefault(correctSpot, "unknown");
        String carPlate = carInfo.contains("Plate: ") ? carInfo.split(",")[0].replace("Plate: ", "") : "UNKNOWN";

        List<String> allSpots = Arrays.asList(parkingLotManager.getSpotIds());
        List<String> availableWrongSpots = allSpots.stream()
            .filter(spot -> !spot.equals(correctSpot))
            .filter(spot -> !parkingLotManager.isUserBooked(spot))
            .filter(spot -> !"booked".equals(parkingLotManager.getSpotStatus(spot)))
            .filter(spot -> !"reserved_occupied".equals(parkingLotManager.getSpotStatus(spot)))
            .filter(spot -> !"booked_occupied".equals(parkingLotManager.getSpotStatus(spot)))
            .collect(Collectors.toList());

        // Enforce live check to remove any slot that is still booked by the user
        availableWrongSpots.removeIf(spot -> parkingLotManager.isUserBooked(spot));
        
        if (availableWrongSpots.isEmpty()) return;
        Collections.shuffle(availableWrongSpots);
        String wrongSpot = availableWrongSpots.get(0);

        scheduler.schedule(() -> {
            if (!parkingLotManager.isBooked(correctSpot) || !parkingLotManager.isUserBooked(correctSpot)) {
                parkingLotManager.notifyListeners(wrongSpot, "available");
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

                if (!parkingLotManager.isBooked(correctSpot) || !parkingLotManager.isUserBooked(correctSpot)) {
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
