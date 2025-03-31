package smartparking;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

public class GUI extends JFrame {
    private JPanel mainPanel;
    private JTextArea notificationArea;
    private JPanel notificationPanel;
    private boolean isNotificationVisible = false;
    private JButton notifyButton;
    private HashMap<String, JLabel> slotLabels;
    private ParkingLotManager parkingLotManager;
    private Timer notificationCleaner;
    private Timer statusRefreshTimer;
    private Set<String> userBookedSlots = new HashSet<>();

    public GUI(ParkingLotManager manager) {
        this.parkingLotManager = manager;
        setTitle("Smart Car Parking System");
        setSize(1400, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        slotLabels = new HashMap<>();

        // ===== Title =====
        JLabel titleLabel = new JLabel("SMART CAR PARKING SYSTEM", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 26));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        add(titleLabel, BorderLayout.NORTH);

        // ===== Legend Panel =====
        JPanel legendPanel = createLegendPanel();
        add(legendPanel, BorderLayout.WEST);

        // ===== Main Parking Area =====
        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Zone A
        JPanel zoneA = new JPanel(new GridLayout(1, 14, 3, 3));
        for (int i = 1; i <= 14; i++) {
            JLabel label = createSpotLabel("A" + i, false);
            zoneA.add(label);
        }
        mainPanel.add(zoneA);

        // Zone B, C, D, E
        JPanel zoneBCDE = new JPanel(new GridLayout(1, 4, 20, 10));
        zoneBCDE.add(createZonePanel("B", 12, true));
        zoneBCDE.add(createZonePanel("C", 12, true));
        zoneBCDE.add(createZonePanel("D", 12, true));
        zoneBCDE.add(createZonePanel("E", 12, true));
        mainPanel.add(zoneBCDE);

        // Zone F
        JPanel zoneF = new JPanel(new GridLayout(1, 14, 3, 3));
        for (int i = 1; i <= 14; i++) {
            JLabel label = createSpotLabel("F" + i, false);
            zoneF.add(label);
        }
        mainPanel.add(zoneF);

        // ===== Control Panel =====
        JPanel controlPanel = new JPanel(new GridLayout(2, 2, 10, 10));

        // Create control buttons
        JButton bookButton = new JButton("Book Slot and Pay");
        JButton cancelButton = new JButton("Cancel Booking");
        JButton findButton = new JButton("Find Booked Slot");
        notifyButton = new JButton("Notifications");

        // Add buttons to panel
        controlPanel.add(bookButton);
        controlPanel.add(cancelButton);
        controlPanel.add(findButton);
        controlPanel.add(notifyButton);
        mainPanel.add(controlPanel);

        add(mainPanel, BorderLayout.CENTER);

        // ===== Button Listeners =====
        bookButton.addActionListener(e -> {
            String[] zones = {"A", "B", "C", "D", "E", "F"};
            String selectedZone = (String) JOptionPane.showInputDialog(this, 
                "Select Zone:", 
                "Zone Selection", 
                JOptionPane.QUESTION_MESSAGE, 
                null, 
                zones, 
                zones[0]);
            
            if (selectedZone == null) return;
            
            String[] spotsInZone = parkingLotManager.getSpotsInZone(selectedZone);
            Arrays.sort(spotsInZone, new Comparator<String>() {
                public int compare(String s1, String s2) {
                    return Integer.compare(
                        Integer.parseInt(s1.substring(1)),
                        Integer.parseInt(s2.substring(1))
                    );
                }
            });
            
            String selectedSpot = (String) JOptionPane.showInputDialog(this, 
                "Select Spot in Zone " + selectedZone + ":", 
                "Spot Selection", 
                JOptionPane.QUESTION_MESSAGE, 
                null, 
                spotsInZone, 
                spotsInZone[0]);
            
            if (selectedSpot == null) return;
            
            String[] durations = {"30 minutes", "1 hour", "2 hours", "4 hours", "8 hours", "24 hours"};
            String selectedDuration = (String) JOptionPane.showInputDialog(this, 
                "Select Parking Duration:", 
                "Duration Selection", 
                JOptionPane.QUESTION_MESSAGE, 
                null, 
                durations, 
                durations[0]);
            
            if (selectedDuration == null) return;
            
            double rate = 2.0;
            int hours = getHoursFromDuration(selectedDuration);
            double amount = rate * hours;
            
            int confirm = JOptionPane.showConfirmDialog(this, 
                String.format("Book spot %s for %s?\nTotal amount: $%.2f", 
                    selectedSpot, selectedDuration, amount), 
                "Confirm Booking", 
                JOptionPane.YES_NO_OPTION);
            
            if (confirm == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    CompletableFuture<Boolean> bookingFuture = parkingLotManager.bookSpot(
                        selectedSpot, hours, false);
                    
                    Boolean success = bookingFuture.get(10, TimeUnit.SECONDS);
                    
                    SwingUtilities.invokeLater(() -> {
                        if (success) {
                            userBookedSlots.add(selectedSpot);
                            parkingLotManager.markAsUserBooked(selectedSpot); // Add this line
                            JOptionPane.showMessageDialog(this, 
                                String.format("Slot %s booked successfully!", selectedSpot));
                        } else {
                            JOptionPane.showMessageDialog(this, 
                                "Booking failed. Please try another spot.");
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> 
                        JOptionPane.showMessageDialog(this, 
                            "Error: " + ex.getMessage()));
                }
            }).start();
        }
    });

        // Find Booked Slot button listener
        findButton.addActionListener(e -> {
            if (userBookedSlots.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No spots are currently booked by you.");
            } else {
                StringBuilder message = new StringBuilder("Your Booked Spots:\n");
                for (String spot : userBookedSlots) {
                    message.append("- ").append(spot).append("\n");
                }
                JOptionPane.showMessageDialog(this, message.toString());
            }
        });

        // Cancel Booking button listener
        cancelButton.addActionListener(e -> {
        if (userBookedSlots.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No spots are currently booked by you.");
            return;
        }
        
        String[] bookedSpots = userBookedSlots.toArray(new String[0]);
        String spotToCancel = (String) JOptionPane.showInputDialog(this, 
            "Select Spot to Cancel:", 
            "Cancel Booking", 
            JOptionPane.QUESTION_MESSAGE, 
            null, 
            bookedSpots, 
            bookedSpots[0]);
        
        if (spotToCancel != null) {
            new Thread(() -> {
                try {
                    CompletableFuture<Boolean> cancelFuture = parkingLotManager.cancelBooking(spotToCancel);
                    Boolean success = cancelFuture.get(5, TimeUnit.SECONDS);
                    
                    SwingUtilities.invokeLater(() -> {
                        if (success) {
                            userBookedSlots.remove(spotToCancel);
                            parkingLotManager.markAsUserUnbooked(spotToCancel); // Add this line
                            JOptionPane.showMessageDialog(this, 
                                "Booking for " + spotToCancel + " canceled successfully.");
                        } else {
                            JOptionPane.showMessageDialog(this, 
                                "Cancellation failed. Spot might not be booked.");
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> 
                        JOptionPane.showMessageDialog(this, 
                            "Cancellation error: " + ex.getMessage()));
                }
            }).start();
        }
    });

        // ===== Notification Panel =====
        notificationPanel = new JPanel(new BorderLayout());
        notificationPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        notificationPanel.setPreferredSize(new Dimension(250, getHeight()));
        JLabel notifTitle = new JLabel("Notifications", SwingConstants.CENTER);
        notifTitle.setFont(new Font("Arial", Font.BOLD, 16));
        notificationPanel.add(notifTitle, BorderLayout.NORTH);

        notificationArea = new JTextArea();
        notificationArea.setEditable(false);
        notificationArea.setText("No notifications for now.");
        notificationPanel.add(new JScrollPane(notificationArea), BorderLayout.CENTER);
        notificationPanel.setVisible(false);
        add(notificationPanel, BorderLayout.EAST);

        // Notification cleaner timer
        notificationCleaner = new Timer();
        notificationCleaner.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                notificationArea.setText("No notifications for now.");
            }
        }, 24 * 60 * 60 * 1000, 24 * 60 * 60 * 1000);

        // Status refresh timer
        statusRefreshTimer = new Timer();
        statusRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshAllSlotStatuses();
            }
        }, 0, 2000); // Refresh every 2 seconds

        notifyButton.addActionListener(e -> {
            isNotificationVisible = !isNotificationVisible;
            notificationPanel.setVisible(isNotificationVisible);
            revalidate();
            repaint();
        });
    }

    private void refreshAllSlotStatuses() {
        for (String spotId : slotLabels.keySet()) {
            String status = parkingLotManager.getSpotStatus(spotId);
            SwingUtilities.invokeLater(() -> updateSlotStatus(spotId, status));
        }
    }

    public Set<String> getUserBookedSlots() {
        return userBookedSlots;
    }
    
    public void displayNotification(String message) {
        if (!message.startsWith("Warning:")) { // Filter out warning notifications
            if (notificationArea.getText().equals("No notifications for now.")) {
                notificationArea.setText(message);
            } else {
                notificationArea.append("\n" + message);
            }
        }
    }

    private JPanel createZonePanel(String zone, int count, boolean vertical) {
        JPanel panel = new JPanel(new GridLayout(count / 2, 2, 3, 3));
        for (int i = 1; i <= count; i++) {
            JLabel label = createSpotLabel(zone + i, vertical);
            panel.add(label);
        }
        return panel;
    }

    private JLabel createSpotLabel(String spotId, boolean vertical) {
        JLabel label = new JLabel();
        label.setLayout(new BorderLayout());

        JLabel text = new JLabel(spotId, SwingConstants.CENTER);
        text.setFont(new Font("Arial", Font.BOLD, 12));
        if (vertical) {
            label.add(text, BorderLayout.WEST);
        } else {
            label.add(text, BorderLayout.NORTH);
        }
        label.setPreferredSize(new Dimension(60, 50));
        label.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        label.setOpaque(true);
        label.setBackground(Color.WHITE);

        slotLabels.put(spotId, label);
        return label;
    }

    public void updateSlotStatus(String spotId, String status) {
        JLabel slot = slotLabels.get(spotId);
        if (slot == null) return;

        slot.setBackground(Color.WHITE);
        slot.removeAll();

        JLabel text = new JLabel(spotId, SwingConstants.CENTER);
        text.setFont(new Font("Arial", Font.BOLD, 12));

        switch (status) {
            case "reserved":
                slot.setBackground(new Color(100, 100, 100)); // Dark gray for reserved
                break;
            case "reserved_occupied":
                slot.setBackground(new Color(100, 100, 100)); // Dark gray + car icon
                addCarIcon(slot);
                break;
            case "time_exceeded":
                slot.setBackground(Color.ORANGE);
                addCarIcon(slot);
                break;
            case "booked":
                // Only green if user booked it
                if (userBookedSlots.contains(spotId)) {
                    slot.setBackground(Color.GREEN);
                } 
                break;
            case "booked_occupied":
                // Only green if user booked it
                if (userBookedSlots.contains(spotId)) {
                    slot.setBackground(Color.GREEN);
                } 
                addCarIcon(slot);
                break;
            case "wrong_parking":
                slot.setBackground(Color.RED);
                addCarIcon(slot);
                break;
            case "available":
                slot.setBackground(Color.WHITE);
                break;
        }


        if (spotId.startsWith("B") || spotId.startsWith("C") || spotId.startsWith("D") || spotId.startsWith("E")) {
            slot.add(text, BorderLayout.WEST);
        } else {
            slot.add(text, BorderLayout.NORTH);
        }

        slot.revalidate();
        slot.repaint();
    }

    private void addCarIcon(JLabel label) {
        ImageIcon carIcon = new ImageIcon("Resources/icons/car.png");
        Image img = carIcon.getImage().getScaledInstance(45, 45, Image.SCALE_SMOOTH);
        label.add(new JLabel(new ImageIcon(img)), BorderLayout.CENTER);
    }

    private JPanel createLegendPanel() {
        JPanel legend = new JPanel();
        legend.setLayout(new BoxLayout(legend, BoxLayout.Y_AXIS));
        legend.setBorder(BorderFactory.createTitledBorder("Legend"));
        legend.setPreferredSize(new Dimension(180, getHeight()));

        legend.add(createLegendRow(Color.WHITE, null, "Available"));
        legend.add(createLegendRow(Color.GRAY, null, "Reserved"));
        legend.add(createLegendRow(Color.ORANGE, null, "Time Limit Exceeded"));
        legend.add(createLegendRow(Color.GREEN, null, "Your Booked Slot"));
        legend.add(createLegendRow(Color.RED, null, "Wrong Parking"));
        legend.add(createLegendRow(null, "Resources/icons/car.png", "Slot is Occupied"));

        return legend;
    }

    private JPanel createLegendRow(Color color, String iconPath, String desc) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (color != null) {
            JLabel colorBox = new JLabel();
            colorBox.setPreferredSize(new Dimension(20, 20));
            colorBox.setOpaque(true);
            colorBox.setBackground(color);
            colorBox.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            row.add(colorBox);
        }

        if (iconPath != null) {
            ImageIcon icon = new ImageIcon(iconPath);
            Image img = icon.getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH);
            JLabel iconLabel = new JLabel(new ImageIcon(img));
            row.add(iconLabel);
        }

        JLabel descLabel = new JLabel(desc);
        row.add(descLabel);

        return row;
    }

    private int getHoursFromDuration(String duration) {
        switch (duration) {
            case "30 minutes": return 1;
            case "1 hour": return 1;
            case "2 hours": return 2;
            case "4 hours": return 4;
            case "8 hours": return 8;
            case "24 hours": return 24;
            default: return 1;
        }
    }
}