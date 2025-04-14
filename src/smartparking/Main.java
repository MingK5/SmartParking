package smartparking;

import java.util.UUID;
import javax.swing.*;

// Entry point to launch the Smart Parking System
public class Main {
    public static void main(String[] args) {
        // Start system components on the Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            // Show a dialog for the user to select their role
            String[] roles = { "Regular", "VIP", "Corporate" };
            String selected = (String) JOptionPane.showInputDialog(
                null,
                "Select your role to begin:",
                "User Role",
                JOptionPane.QUESTION_MESSAGE,
                null,
                roles,
                roles[0]
            );

            if (selected == null) return; // Exit if user cancels

            // Create a user profile with random ID and selected role
            UserProfile.Role role = UserProfile.Role.valueOf(selected.toUpperCase());
            String userId = UUID.randomUUID().toString();
            UserProfile profile = new UserProfile(userId, role);

            // Register the user with the system
            ParkingLotManager manager = ParkingLotManager.getInstance();
            manager.registerUser(profile);

            // Launch the GUI and link it to the backend Parking Lot Manager
            GUI gui = new GUI(manager, userId);
            manager.registerGUI(gui);
            gui.setVisible(true);

            // Start system sensor simulation threads (wrong parking, sensor updates)
            new Thread(new SensorSimulation(manager)::run, "SensorSim").start();
            
            // Start automated system booking & cancellation threads (random user simulation)
            new Thread(new UserSimulation(manager)::run, "UserSim").start();

            // Optional system status logger (prints stats every 5 seconds)
            new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(5000);
                        manager.printSystemStatus();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "SystemMonitor").start();
        });
    }
}