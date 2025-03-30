package smartparking;

public class Main {
    public static void main(String[] args) {
        ParkingLotManager parkingLotManager = ParkingLotManager.getInstance();
        GUI gui = new GUI(parkingLotManager);
        parkingLotManager.registerGUI(gui);
        gui.setVisible(true);

        // Start simulations with monitoring
        SensorSimulation sensorSim = new SensorSimulation(parkingLotManager);
        UserSimulation userSim = new UserSimulation(parkingLotManager);
        
        Thread sensorThread = new Thread(sensorSim::run, "SensorSimulation");
        Thread userThread = new Thread(userSim::run, "UserSimulation");
        
        sensorThread.start();
        userThread.start();

        // Start monitoring thread
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    parkingLotManager.printSystemStatus();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "SystemMonitor").start();
    }
}