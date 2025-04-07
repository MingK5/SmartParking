package smartparking;

import java.util.UUID;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
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

            if (selected == null) return; // User cancelled

            UserProfile.Role role = UserProfile.Role.valueOf(selected.toUpperCase());
            String userId = UUID.randomUUID().toString();
            UserProfile profile = new UserProfile(userId, role);

            ParkingLotManager manager = ParkingLotManager.getInstance();
            manager.registerUser(profile);

            GUI gui = new GUI(manager, userId);
            manager.registerGUI(gui);
            gui.setVisible(true);

            new Thread(new SensorSimulation(manager)::run, "SensorSim").start();
            new Thread(new UserSimulation(manager)::run, "UserSim").start();

            // Optional: Start monitoring system stats
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