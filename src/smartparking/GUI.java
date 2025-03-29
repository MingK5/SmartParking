package smartparking;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

public class GUI extends JFrame {
    private JPanel mainPanel;
    private JTextArea notificationArea;
    private JPanel notificationPanel;
    private boolean isNotificationVisible = false;
    private JButton notifyButton;
    private HashMap<String, JLabel> slotLabels;

    // GUI Constructor
    public GUI() {
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

        // Book Slot and Pay button listener (dummy action)
        bookButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Book Slot feature coming soon!");
        });

        // Cancel Booking button listener (dummy action)
        cancelButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Cancel Booking feature coming soon!");
        });

        // Find Booked Slot button listener (dummy action)
        findButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Find Booked Slot feature coming soon!");
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

        // Toggle notification panel visibility
        notifyButton.addActionListener(e -> {
            isNotificationVisible = !isNotificationVisible;
            notificationPanel.setVisible(isNotificationVisible);
            revalidate();
            repaint();
        });
    }

    // Create Parking Zones
    private JPanel createZonePanel(String zone, int count, boolean vertical) {
        JPanel panel = new JPanel(new GridLayout(count / 2, 2, 3, 3));
        for (int i = 1; i <= count; i++) {
            JLabel label = createSpotLabel(zone + i, vertical);
            panel.add(label);
        }
        return panel;
    }

    // Create Parking Spots
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

    // Update the visual status of a parking slot
    public void updateSlotStatus(String spotId, String status) {
        JLabel slot = slotLabels.get(spotId);
        if (slot == null) return;

        slot.setBackground(Color.WHITE);
        slot.removeAll();

        JLabel text = new JLabel(spotId, SwingConstants.CENTER);
        text.setFont(new Font("Arial", Font.BOLD, 12));

        if (status.equals("reserved_vacant")) {
            slot.setBackground(Color.GRAY);
        } else if (status.equals("reserved_occupied")) {
            slot.setBackground(Color.GRAY);
            addCarIcon(slot);
        } else if (status.equals("time_exceeded")) {
            slot.setBackground(Color.ORANGE);
            addCarIcon(slot);
        } else if (status.equals("booked")) {
            slot.setBackground(Color.GREEN);
        } else if (status.equals("booked_correct")) {
            slot.setBackground(Color.GREEN);
            addCarIcon(slot);
        } else if (status.equals("wrong_parking")) {
            slot.setBackground(Color.RED);
            addCarIcon(slot);
        }

        if (spotId.startsWith("B") || spotId.startsWith("C") || spotId.startsWith("D") || spotId.startsWith("E")) {
            slot.add(text, BorderLayout.WEST);
        } else {
            slot.add(text, BorderLayout.NORTH);
        }

        slot.revalidate();
        slot.repaint();
    }

    // Add a car icon to parking slot
    private void addCarIcon(JLabel label) {
        ImageIcon carIcon = new ImageIcon("Resources/icons/car.png");
        Image img = carIcon.getImage().getScaledInstance(45, 45, Image.SCALE_SMOOTH);
        label.add(new JLabel(new ImageIcon(img)), BorderLayout.CENTER);
    }

    // Create the legend panel
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

    // Create the legend rows
    private JPanel createLegendRow(Color color, String iconPath, String desc) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2)); // tighter spacing
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

    // FOR TESTING ONLY
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GUI gui = new GUI();
            gui.setVisible(true);

            new Timer(2000, new ActionListener() {
                int counter = 0;
                String[] spots = {"B4", "D5", "F7", "C10"};
                String[] statuses = {"time_exceeded", "booked_correct", "wrong_parking", "reserved_vacant"};

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (counter < spots.length) {
                        gui.updateSlotStatus(spots[counter], statuses[counter]);
                        counter++;
                    } else {
                        ((Timer) e.getSource()).stop();
                    }
                }
            }).start();
        });
    }
}
