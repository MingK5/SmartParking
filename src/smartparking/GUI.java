package smartparking;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.Clip;

// GUI class for the smart car parking system
public class GUI extends JFrame {
    private JPanel mainPanel;
    private JTextArea notificationArea;
    private JPanel notificationPanel;
    private JButton notifyButton;
    private HashMap<String, JLabel> slotLabels;
    private ParkingLotManager parkingLotManager;
    private Timer notificationCleaner;
    private Set<String> userBookedSlots = ConcurrentHashMap.newKeySet();
    private final Map<String, String> lastSlotStatuses = new ConcurrentHashMap<>();
    private final ExecutorService bookingExecutor = Executors.newSingleThreadExecutor();
    private boolean isNotificationVisible = false;
    private JDialog activeBookingDialog = null;
    private JDialog activeConfirmationDialog = null;
    private final String userId;
    private List<String> notificationMessages = new ArrayList<>();
    private static final int MAX_NOTIFICATIONS = 20;

    // Constructor
    public GUI(ParkingLotManager manager, String userId) {
        this.parkingLotManager = manager;
        this.userId = userId;
        setTitle("Smart Car Parking System");
        setSize(1400, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        slotLabels = new HashMap<>();

        JLabel titleLabel = new JLabel("SMART CAR PARKING SYSTEM", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 26));
        add(titleLabel, BorderLayout.NORTH);

        add(createLegendPanel(), BorderLayout.WEST);

        mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        createZonePanels();
        createControlPanel();
        add(mainPanel, BorderLayout.CENTER);

        setupNotificationPanel();
        setupTimers();
    }

    // Method to create parking zones layout
    private void createZonePanels() {
        JPanel zoneA = new JPanel(new GridLayout(1, 14));
        for (int i = 1; i <= 14; i++) zoneA.add(createSpotLabel("A" + i, false));
        mainPanel.add(zoneA);

        JPanel zoneBCDE = new JPanel(new GridLayout(1, 4, 20, 10));
        for (String zone : new String[]{"B", "C", "D", "E"})
            zoneBCDE.add(createZonePanel(zone, 12, true));
        mainPanel.add(zoneBCDE);

        JPanel zoneF = new JPanel(new GridLayout(1, 14));
        for (int i = 1; i <= 14; i++) zoneF.add(createSpotLabel("F" + i, false));
        mainPanel.add(zoneF);
    }

    // Method to create control buttons (Book, Cancel, Find)
    private void createControlPanel() {
        JPanel controlPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        JButton bookButton = new JButton("Book Slot and Pay");
        JButton cancelButton = new JButton("Cancel Booking");
        JButton findButton = new JButton("Find Booked Slot");
        notifyButton = new JButton("Notifications");
        Font buttonFont = new Font("Arial", Font.PLAIN, 18);
        bookButton.setFont(buttonFont);
        cancelButton.setFont(buttonFont);
        findButton.setFont(buttonFont);
        notifyButton.setFont(buttonFont);
        
        controlPanel.add(bookButton);
        controlPanel.add(cancelButton);
        controlPanel.add(findButton);
        controlPanel.add(notifyButton);
        mainPanel.add(controlPanel);

        bookButton.addActionListener(e -> handleBooking());
        cancelButton.addActionListener(e -> handleCancellation());
        findButton.addActionListener(e -> showBookedSlots());
        notifyButton.addActionListener(e -> toggleNotificationPanel());
    }

    // Method to handle slot booking process
    private void handleBooking() {
        if (parkingLotManager.userHasReachedLimit(userId)) {
            JOptionPane.showMessageDialog(this,
                "You have reached your booking limit.\nCancel a booking to proceed.",
                "Limit Reached", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String selectedZone = showDropdown("Select Zone:", new String[]{"A", "B", "C", "D", "E", "F"});
        if (selectedZone == null) {
            displayNotification("Booking cancelled by user.");
            return;
        }

        String[] spots = parkingLotManager.getSpotsInZone(selectedZone);
        Arrays.sort(spots, Comparator.comparingInt(s -> Integer.parseInt(s.substring(1))));
        String selectedSpot = showDropdown("Select Spot in Zone " + selectedZone + ":", spots);
        if (selectedSpot == null) {
            displayNotification("Booking cancelled by user.");
            return;
        }
        
        // Attempt to soft lock the slot for 60 seconds
        if (!parkingLotManager.trySoftLock(selectedSpot, userId, 60000)) {
            JOptionPane.showMessageDialog(this,
                "Selected spot is currently reserved.",
                "Reservation Error",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Recheck real-time status
        if (!"available".equals(parkingLotManager.getSpotStatus(selectedSpot, userId))) {
            JOptionPane.showMessageDialog(this,
                "Selected spot is no longer available.",
                "Reservation Error",
                JOptionPane.WARNING_MESSAGE);
            parkingLotManager.releaseSoftLock(selectedSpot, userId);
            parkingLotManager.notifyListeners(selectedSpot, "available");
            return;
        }

        // Enter booking duration and car plate number
        JDialog bookingDialog = new JDialog(this, "Enter Booking Details", true);
        activeBookingDialog = bookingDialog;
        bookingDialog.setLayout(new BorderLayout());
        bookingDialog.setPreferredSize(new Dimension(420, 200)); // Fixed size
        bookingDialog.setResizable(false); // Prevents resizing

        // Center Panel for input fields
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.EAST;
        inputPanel.add(new JLabel("Select Duration:"), gbc);

        JComboBox<String> durationBox = new JComboBox<>(new String[]{
            "30 minutes", "1 hour", "2 hours", "4 hours", "8 hours", "24 hours"
        });
        gbc.gridx = 1; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        inputPanel.add(durationBox, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.EAST;
        inputPanel.add(new JLabel("Enter Car Plate:"), gbc);

        JTextField carPlateField = new JTextField(12);
        String tooltipText = "Enter 5–10 alphanumeric characters (e.g., ABC1234)";
        carPlateField.setToolTipText(tooltipText);

        // Show tooltip immediately on focus
        carPlateField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                ToolTipManager.sharedInstance().setInitialDelay(0); // show immediately
                ToolTipManager.sharedInstance().mouseMoved(
                    new MouseEvent(carPlateField, 0, 0, 0, 1, 1, // dummy coords inside field
                    0, false)
                );
            }
        });
        gbc.gridx = 1; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        inputPanel.add(carPlateField, gbc);

        // Error label
        JLabel errorLabel = new JLabel("Car plate is required.");
        errorLabel.setForeground(Color.RED);
        errorLabel.setVisible(false);
        gbc.gridx = 1; gbc.gridy = 2; gbc.anchor = GridBagConstraints.WEST;
        inputPanel.add(errorLabel, gbc);

        bookingDialog.add(inputPanel, BorderLayout.CENTER);

        // South Panel for buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        bookingDialog.add(buttonPanel, BorderLayout.SOUTH);
                    
        final boolean[] submitted = {false};

        okButton.addActionListener(e -> {
            String carPlate = carPlateField.getText().trim().toUpperCase();
            if (carPlate.isEmpty()) {
                errorLabel.setText("Car plate is required.");
                errorLabel.setVisible(true);
            } else if (!carPlate.matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{5,10}$")) {
                errorLabel.setText("Invalid car plate (5–10 alphanumeric chars).");
                errorLabel.setVisible(true);
            } else {
                errorLabel.setVisible(false);
                submitted[0] = true;
                bookingDialog.dispose();
                activeBookingDialog = null;
            }
            inputPanel.revalidate();
            inputPanel.repaint();
        });

        cancelButton.addActionListener(e -> bookingDialog.dispose());
        bookingDialog.pack();
        bookingDialog.setLocationRelativeTo(null);
        bookingDialog.setVisible(true);
        
        if (!submitted[0]) {
            displayNotification("Booking cancelled by user.");
            parkingLotManager.releaseSoftLock(selectedSpot, userId);
            parkingLotManager.notifyListeners(selectedSpot, "available");
            return;
        }

        String selectedDuration = (String) durationBox.getSelectedItem();
        String carPlate = carPlateField.getText().trim().toUpperCase();


        // Calculate parking fees
        int hours = getHoursFromDuration(selectedDuration);
        double amount = hours * 2.0;

        // Show fully non-blocking confirmation dialog (JDialog)
        JDialog confirmDialog = new JDialog(this, "Confirm Booking", false);
        activeConfirmationDialog = confirmDialog;
        confirmDialog.setLayout(new BorderLayout());
        confirmDialog.setSize(300, 150);
        confirmDialog.setLocationRelativeTo(this);

        JLabel message = new JLabel(
            String.format("<html>Book spot %s for %s?<br>Total: $%.2f</html>", selectedSpot, selectedDuration, amount),
            SwingConstants.CENTER);
        confirmDialog.add(message, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        JButton yesBtn = new JButton("Confirm");
        JButton cancelBtn = new JButton("Cancel");
        buttons.add(yesBtn);
        buttons.add(cancelBtn);
        confirmDialog.add(buttons, BorderLayout.SOUTH);

        yesBtn.addActionListener(e -> {
            confirmDialog.dispose();
            activeConfirmationDialog = null;

            if (!"available".equals(parkingLotManager.getSpotStatus(selectedSpot, userId))) {
                JOptionPane.showMessageDialog(this,
                    "Selected spot is no longer available.",
                    "Reservation Error",
                    JOptionPane.WARNING_MESSAGE);
                parkingLotManager.releaseSoftLock(selectedSpot, userId);
                return;
            }

            displayNotification("Processing booking for slot " + selectedSpot);

            // Non-blocking callback without get()
            parkingLotManager.bookSpot(selectedSpot, hours, selectedDuration, false, userId)
                .thenAccept(success -> {
                    if (success) {
                        SwingUtilities.invokeLater(() -> userBookedSlots.add(selectedSpot));
                        parkingLotManager.markAsUserBooked(selectedSpot, userId, carPlate, selectedDuration);
                    } else {
                        parkingLotManager.releaseSoftLock(selectedSpot, userId);
                        parkingLotManager.notifyListeners(selectedSpot, "available");
                        parkingLotManager.notifyUser("Booking failed for " + selectedSpot);
                    }
                });
            });

        cancelBtn.addActionListener(e -> {
            confirmDialog.dispose();
            activeConfirmationDialog = null;
            displayNotification("Booking cancelled by user.");
            parkingLotManager.releaseSoftLock(selectedSpot, userId); 
            parkingLotManager.notifyListeners(selectedSpot, "available");
        });

        confirmDialog.setVisible(true);
    }

    // Method to handle booking cancellation process
    private void handleCancellation() {
        // Clean up expired bookings
        userBookedSlots.removeIf(spot -> {
            boolean expired = !parkingLotManager.isBooked(spot);
            if (expired) parkingLotManager.markAsUserUnbooked(spot, userId);
            return expired;
        });

        String[] bookedSpots = userBookedSlots.toArray(new String[0]);
        if (bookedSpots.length == 0) {
            SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(this, "No spots booked.", "Notice", JOptionPane.INFORMATION_MESSAGE)
            );
            return;
        }

        String spotToCancel = showDropdown("Select a spot to cancel:", bookedSpots);
        if (spotToCancel == null) {
            displayNotification("Cancellation cancelled by user.");
            return;
        }

        displayNotification("Processing cancellation for " + spotToCancel);

        // Use thenAccept directly (non-blocking)
        parkingLotManager.cancelBooking(spotToCancel).thenAccept(success -> {
            if (success) {
                // Use Swing thread only for UI-related changes
                SwingUtilities.invokeLater(() -> userBookedSlots.remove(spotToCancel));
                parkingLotManager.markAsUserUnbooked(spotToCancel, userId);
                // Let the manager queue handle notifyUser and notifyListeners safely
            } else {
                parkingLotManager.notifyUser("Cancellation failed for " + spotToCancel);
            }
        });
    }

    // Method to show a dropdown selection dialog
    private String showDropdown(String title, String[] options) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JComboBox<String> comboBox = new JComboBox<>(options);
    panel.add(new JLabel(title));
    panel.add(comboBox);

    int result = JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION);
    return result == JOptionPane.OK_OPTION ? (String) comboBox.getSelectedItem() : null;
    }
    
    // Method to force close active booking dialogs upon soft lock expiry
    public void closeBookingDialogs() {
        if (activeBookingDialog != null) {
            SwingUtilities.invokeLater(() -> {
                activeBookingDialog.dispose();
                activeBookingDialog = null;
            });
        }
        if (activeConfirmationDialog != null) {
            SwingUtilities.invokeLater(() -> {
                activeConfirmationDialog.dispose();
                activeConfirmationDialog = null;
            });
        }
    }
    
    // Method to display all booked slots by the user
    private void showBookedSlots() {
        Map<String, String> bookings = parkingLotManager.getUserBookings(userId);

        // Remove expired slots
        Set<String> expired = new HashSet<>();
        for (String spot : bookings.keySet()) {
            if (!parkingLotManager.isBooked(spot)) {
                expired.add(spot);
            }
        }

        // Remove expired bookings from system
        expired.forEach(spot -> {
            parkingLotManager.markAsUserUnbooked(spot, userId);
            userBookedSlots.remove(spot); // clean up local tracking
        });
        bookings.keySet().removeAll(expired); // remove from display

        if (bookings.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No active bookings found.");
            return;
        }

        StringBuilder message = new StringBuilder("Your Active Bookings:\n");
        for (Map.Entry<String, String> entry : bookings.entrySet()) {
            message.append("- Spot: ").append(entry.getKey()).append("\n  ").append(entry.getValue()).append("\n");
        }

        JOptionPane.showMessageDialog(this, message.toString());
    }

    // Method to toggle notification panel visibility
    private void toggleNotificationPanel() {
        isNotificationVisible = !notificationPanel.isVisible();
        notificationPanel.setVisible(isNotificationVisible);
        revalidate();
        repaint();
    }

    // Method to set up notification panel
    private void setupNotificationPanel() {
        notificationPanel = new JPanel(new BorderLayout());
        notificationPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        notificationPanel.setPreferredSize(new Dimension(250, getHeight()));
        JLabel notifTitle = new JLabel("Notifications", SwingConstants.CENTER);
        notifTitle.setFont(new Font("Arial", Font.BOLD, 16));
        notificationPanel.add(notifTitle, BorderLayout.NORTH);

        notificationArea = new JTextArea("No notifications for now.");
        notificationArea.setEditable(false);
        notificationPanel.add(new JScrollPane(notificationArea), BorderLayout.CENTER);
        notificationPanel.setVisible(false);
        add(notificationPanel, BorderLayout.EAST);
    }

    // Method to configure timer for daily notification reset
    private void setupTimers() {
        notificationCleaner = new Timer();
        notificationCleaner.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                notificationArea.setText("No notifications for now.");
            }
        }, 24 * 60 * 60 * 1000, 24 * 60 * 60 * 1000);
    }

    // Method to display notifications with timestamps and sound
    public void displayNotification(String message) {
        // Get the current timestamp
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = now.format(formatter);

        // Prepend the timestamp to the notification message
        String formattedMessage = "|" + timestamp + "| " + message;

        // Add the message to the list and ensure the max limit
        notificationMessages.add(0, formattedMessage); // Add to the top
        if (notificationMessages.size() > MAX_NOTIFICATIONS) {
            notificationMessages.remove(notificationMessages.size() - 1); // Remove the oldest message
        }

        // Update the notification area
        notificationArea.setText(String.join("\n", notificationMessages));
        
        // Play the notification sound
        playNotificationSound();
    }
    
    // Method to play the notification sound
    private void playNotificationSound() {
    try {
        // Replace "Resources/sounds/notification.wav" with your actual sound file path
        File soundFile = new File("Resources/sounds/notification.wav");
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(soundFile);
        Clip clip = AudioSystem.getClip();
        clip.open(audioStream);
        clip.start();
    } catch (Exception e) {
        System.err.println("⚠️ Unable to play notification sound: " + e.getMessage());
        e.printStackTrace();
        }
    }

    // Method to create a panel for a specific parking zone
    private JPanel createZonePanel(String zone, int count, boolean vertical){
        JPanel panel = new JPanel(new GridLayout(count / 2, 2, 3, 3));
        for (int i = 1; i <= count; i++) panel.add(createSpotLabel(zone + i, vertical));
        return panel;
    }

    // Method to create a label for each parking spot
    private JLabel createSpotLabel(String spotId, boolean vertical) {
        JLabel label = new JLabel();
        label.setLayout(new BorderLayout());
        JLabel text = new JLabel(spotId, SwingConstants.CENTER);
        text.setFont(new Font("Arial", Font.BOLD, 16));
        label.add(text, vertical ? BorderLayout.WEST : BorderLayout.NORTH);
        label.setPreferredSize(new Dimension(60, 50));
        label.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        label.setOpaque(true);
        label.setBackground(Color.WHITE);
        slotLabels.put(spotId, label);
        return label;
    }

    // Method to update slot UI status and icon
    public void updateSlotStatus(String spotId, String status) {
        String currentStatus = lastSlotStatuses.get(spotId);

        // Improved duplicate status check to prevent UI loops
        if (currentStatus != null && currentStatus.equals(status)) {
            System.out.println("Skipped UI update for " + spotId + " (same status: " + status + ")");
            return;
        }

        // Update the cache before UI repaint to avoid loops
        lastSlotStatuses.put(spotId, status);

        JLabel slot = slotLabels.get(spotId);
        if (slot == null) return;

        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Updating UI slot " + spotId + " to " + status);
                slot.removeAll();
                slot.setBackground(Color.WHITE);
                JLabel text = new JLabel(spotId, SwingConstants.CENTER);
                text.setFont(new Font("Arial", Font.BOLD, 16));

                switch (status) {
                    case "reserved" -> slot.setBackground(Color.GRAY);
                    case "reserved_occupied" -> { slot.setBackground(Color.GRAY); addCarIcon(slot); }
                    case "time_exceeded" -> { slot.setBackground(Color.ORANGE); addCarIcon(slot); }
                    case "booked" -> slot.setBackground(Color.GREEN);
                    case "booked_occupied" -> {
                        if (userBookedSlots.contains(spotId)) {
                            slot.setBackground(Color.GREEN); addCarIcon(slot);
                        }
                    }
                    case "wrong_parking" -> { slot.setBackground(Color.RED); addCarIcon(slot); }
                    case "soft_locked" -> slot.setBackground(Color.LIGHT_GRAY);
                    default -> slot.setBackground(Color.WHITE);
                }

                slot.add(text, spotId.matches("[BCDE].*") ? BorderLayout.WEST : BorderLayout.NORTH);
                slot.revalidate();
                slot.repaint();

            } catch (Exception ex) {
                System.err.println("⚠️ UI update failed for slot " + spotId + ": " + ex.getMessage());
                ex.printStackTrace();
                displayNotification("UI update error on " + spotId);
            }
        });
    }

    // Method to add car icon to a parking slot
    private void addCarIcon(JLabel label) {
        ImageIcon carIcon = new ImageIcon("Resources/icons/car.png");
        Image img = carIcon.getImage().getScaledInstance(45, 45, Image.SCALE_SMOOTH);
        label.add(new JLabel(new ImageIcon(img)), BorderLayout.CENTER);
    }

    // Method to create the UI legend panel
    private JPanel createLegendPanel() {
        JPanel legend = new JPanel();
        legend.setLayout(new BoxLayout(legend, BoxLayout.Y_AXIS));
        legend.setBorder(BorderFactory.createTitledBorder("Legend"));
        legend.setPreferredSize(new Dimension(180, getHeight()));

        legend.add(createLegendRow(Color.WHITE, null, "Available"));
        legend.add(createLegendRow(Color.GRAY, null, "Reserved"));
        legend.add(createLegendRow(Color.LIGHT_GRAY, null, "Soft Locked"));
        legend.add(createLegendRow(Color.ORANGE, null, "Time Limit Exceeded"));
        legend.add(createLegendRow(Color.GREEN, null, "Your Booked Slot"));
        legend.add(createLegendRow(Color.RED, null, "Wrong Parking"));
        legend.add(createLegendRow(null, "Resources/icons/car.png", "Slot is Occupied"));

        return legend;
    }

    // Method to create a row in the legend panel
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
        descLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        return row;
    }

    // Method to convert duration text to number of hours
    private int getHoursFromDuration(String duration) {
        return switch (duration) {
            case "30 minutes", "1 hour" -> 1;
            case "2 hours" -> 2;
            case "4 hours" -> 4;
            case "8 hours" -> 8;
            case "24 hours" -> 24;
            default -> 1;
        };
    }
}

