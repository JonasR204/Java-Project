import javax.swing.*;
import javax.swing.Timer;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

public class GUI extends JFrame {

    private NetXmlReader netXmlReader = new NetXmlReader();
    private Timer autoSpawnTimer;
    private final JTextField spawnIntervalField = new JTextField("5", 5);
    private final JButton autoSpawnButton = new JButton("Start Auto Spawn");
    private boolean autoSpawnRunning = false;
    private boolean gaveDuration = false;
    private final Map<String, TlsPhaseCollector> tlsCollectors = new HashMap<>();
    private Set<String> defaultRoutes = new HashSet<>();
    private Timer vehicleInspectorTimer;

    private TlsPhaseCollector tlsPhaseCollector;
    private JDialog vehicleInspector;
    private JComboBox<String> edgeFilterBox;

    private SimulationController controller;
    private MapPanel mapPanel;
    private Timer timer;
    private String selectedVehicleId = null;
    private String selectedTlsId = null;

    private final Random rand = new Random();
    private final VehicleFilter vehicleFilter = new VehicleFilter();
    private final JTextField sumoExeField = new JTextField(30);
    private final JTextField cfgField     = new JTextField(30);

    private final JButton startButton   = new JButton("Start / Resume");
    private final JButton pauseButton   = new JButton("Pause");
    private final JButton restartButton = new JButton("Restart");

    private final JButton spawnButton   = new JButton("Spawn Vehicle");
    private final JButton stressTestButton = new JButton("Stresstest (50)");

    private final JButton injectVehicleButton = new JButton("Inject Vehicle");
    private final JButton filterButton        = new JButton("Filter");
    private final JButton selectVehicleButton = new JButton("Select Vehicle");
    private final JButton selectTlsButton     = new JButton("Select Traffic Light");

    private final List<TrafficLightBar> trafficLightBars = new ArrayList<>();

    private final JLabel statusLabel = new JLabel("Ready");

    private JComboBox<String> tlsDropdown;
    private JButton phasePlusBtn;
    private JButton phaseMinusBtn;
    private JButton hold10Btn;
    private JLabel tlsInfoLabel;


    public GUI() {
        super("SUMO Vehicle Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        initLayout();
        loadTlsDurationsFromNet();
        initTimer();
        updateUiState(false);
    }


    private void positionRightOfMainWindow(Window dialog) {
        Point p = getLocationOnScreen();
        int x = p.x + getWidth() + 5;
        int y = p.y;
        dialog.setLocation(x, y);
    }

    private void loadTlsDurationsFromNet() {
        try {
            String netPath = "C:\\Users\\hamza\\Desktop\\JAVA2Project\\Milestone2\\sumo\\Demo2.net.xml";
            netXmlReader.parse(netPath);
            System.out.println("TLS durations loaded for " + netXmlReader.tlsDurations.size() + " traffic lights.");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to parse network XML: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openVehicleInspectorSkeleton(String vehicleId) {

        if (vehicleInspector != null) {
            if (vehicleInspectorTimer != null) {
                vehicleInspectorTimer.stop();
            }
            vehicleInspector.dispose();
        }

        vehicleInspector = new JDialog(this, "Vehicle Inspector: " + vehicleId, false);
        vehicleInspector.setSize(440, 560);
        vehicleInspector.setLayout(new BorderLayout());

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField changeSpeedField = new JTextField();
        JLabel currentSpeedLabel = new JLabel("-");
        JLabel maxSpeedLabel = new JLabel("-");
        JLabel avgSpeedLabel = new JLabel("-");
        JLabel edgeLabel = new JLabel("-");
        JLabel currentRouteLabel = new JLabel("-");

        JComboBox<Color> colorBox = new JComboBox<>(new Color[]{
                Color.BLUE, Color.RED, Color.GREEN, Color.ORANGE, Color.MAGENTA
        });

        JComboBox<String> routeBox = new JComboBox<>();
        routeBox.addItem("None");

        final double[] speedSum = {0};
        final int[] speedCount = {0};
        Color originalColor = null;

        try {
            SumoVehicle v = new SumoVehicle(vehicleId, controller);

            currentRouteLabel.setText(controller.getVehicleRoute(vehicleId));
            edgeLabel.setText(controller.getVehicleEdge(vehicleId));

            String laneId = controller.getVehicleEdge(vehicleId) + "_0";
            maxSpeedLabel.setText(String.format("%.2f", controller.getLaneMaxSpeed(laneId)));

            originalColor = controller.getVehicleColor(vehicleId);
            if (originalColor != null) colorBox.setSelectedItem(originalColor);

        } catch (Exception e) {
            e.printStackTrace();
        }

        int y = 0;
        gbc.gridx = 0; gbc.gridy = y;
        content.add(new JLabel("Change speed:"), gbc);
        gbc.gridx = 1; content.add(changeSpeedField, gbc);

        gbc.gridx = 0; gbc.gridy = ++y;
        content.add(new JLabel("Current speed:"), gbc);
        gbc.gridx = 1; content.add(currentSpeedLabel, gbc);

        gbc.gridx = 0; gbc.gridy = ++y;
        content.add(new JLabel("Max speed:"), gbc);
        gbc.gridx = 1; content.add(maxSpeedLabel, gbc);

        gbc.gridx = 0; gbc.gridy = ++y;
        content.add(new JLabel("Avg speed:"), gbc);
        gbc.gridx = 1; content.add(avgSpeedLabel, gbc);

        gbc.gridx = 0; gbc.gridy = ++y;
        content.add(new JLabel("Edge:"), gbc);
        gbc.gridx = 1; content.add(edgeLabel, gbc);

        gbc.gridx = 0; gbc.gridy = ++y;
        content.add(new JLabel("Color:"), gbc);
        gbc.gridx = 1; content.add(colorBox, gbc);

        gbc.gridx = 0; gbc.gridy = ++y;
        content.add(new JLabel("Current route:"), gbc);
        gbc.gridx = 1; content.add(currentRouteLabel, gbc);

        gbc.gridx = 0; gbc.gridy = ++y;
        content.add(new JLabel("Change route:"), gbc);
        gbc.gridx = 1; content.add(routeBox, gbc);

        JButton applyBtn = new JButton("Apply");
        JButton closeBtn = new JButton("Close");

        // ================= APPLY BUTTON =================
        applyBtn.addActionListener(e -> {
            try {
                if (!controller.vehicleExists(vehicleId)) {
                    JOptionPane.showMessageDialog(vehicleInspector,
                            "Vehicle no longer exists.",
                            "Vehicle gone",
                            JOptionPane.WARNING_MESSAGE);
                    vehicleInspector.dispose();
                    return;
                }

                SumoVehicle v = new SumoVehicle(vehicleId, controller);

                // --- apply speed ---
                String text = changeSpeedField.getText().trim();
                double appliedSpeed;
                if (!text.isEmpty()) {
                    double requested = Double.parseDouble(text);
                    double maxSpeed = controller.getLaneMaxSpeed(controller.getVehicleEdge(vehicleId) + "_0");
                    appliedSpeed = Math.min(requested, maxSpeed);
                } else {
                    appliedSpeed = controller.getLaneMaxSpeed(controller.getVehicleEdge(vehicleId) + "_0");
                }
                controller.setVehicleSpeed(vehicleId, appliedSpeed);

                // --- apply color ---
                Color selectedColor = (Color) colorBox.getSelectedItem();
                if (selectedColor != null) {
                    controller.setVehicleColor(vehicleId, selectedColor);
                }

                // --- apply route ---
                String selectedRoute = (String) routeBox.getSelectedItem();
                if (selectedRoute != null && !selectedRoute.equals("None")) {
                    String vehicleEdge = controller.getVehicleEdge(vehicleId);
                    List<String> edges = controller.getRouteEdges(selectedRoute);
                    if (edges.contains(vehicleEdge)) {
                        controller.setVehicleRoute(vehicleId, selectedRoute);
                        currentRouteLabel.setText(selectedRoute); // update label immediately
                    }
                }

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(vehicleInspector,
                        ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        gbc.gridx = 0; gbc.gridy = ++y;
        content.add(applyBtn, gbc);
        gbc.gridx = 1;
        content.add(closeBtn, gbc);

        closeBtn.addActionListener(ev -> vehicleInspector.dispose());

        vehicleInspector.add(content, BorderLayout.CENTER);
        positionRightOfMainWindow(vehicleInspector);

        // ================= LIVE UPDATE TIMER =================
        final String[] lastEdge = {""};
        final String[] lastRoute = {""}; // track last route change to refresh dropdown

        vehicleInspectorTimer = new Timer(300, ev -> {
            try {
                if (!controller.vehicleExists(vehicleId)) {
                    vehicleInspector.dispose();
                    return;
                }

                double speed = controller.getVehicleSpeed(vehicleId);
                String edge = controller.getVehicleEdge(vehicleId);
                String route = controller.getVehicleRoute(vehicleId);

                currentSpeedLabel.setText(String.format("%.2f", speed));
                edgeLabel.setText(edge);
                currentRouteLabel.setText(route);

                speedSum[0] += speed;
                speedCount[0]++;
                avgSpeedLabel.setText(String.format("%.2f", speedSum[0] / speedCount[0]));

                // --- refresh route dropdown if edge or route changed ---
                if (!edge.equals(lastEdge[0]) || !route.equals(lastRoute[0])) {
                    lastEdge[0] = edge;
                    lastRoute[0] = route;

                    String previousSelection = (String) routeBox.getSelectedItem();
                    routeBox.removeAllItems();
                    routeBox.addItem("None");

                    for (String r : defaultRoutes) {
                        // include all routes that contain current edge
                        List<String> edges = controller.getRouteEdges(r);
                        if (edges.contains(edge)) {
                            routeBox.addItem(r);
                        }
                    }

                    // Keep previous selection if still valid
                    if (previousSelection != null && !previousSelection.equals("None")) {
                        for (int i = 0; i < routeBox.getItemCount(); i++) {
                            if (routeBox.getItemAt(i).equals(previousSelection)) {
                                routeBox.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                }

            } catch (Exception ignored) {}
        });

        vehicleInspectorTimer.start();

        vehicleInspector.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                vehicleInspectorTimer.stop();
            }
        });

        vehicleInspector.setVisible(true);
    }
    private void openTlsInspectorSkeleton(String tlsId) {

        if (controller == null) return;

        // --- dialog setup ---
        JDialog frame = new JDialog(this, "Traffic Light Inspector - " + tlsId, false);
        frame.setSize(500, 500);
        frame.setLayout(new BorderLayout());

        JLabel header = new JLabel("Traffic Light: " + tlsId);
        JLabel currentPhaseLabel = new JLabel("Current phase: ?");
        JPanel top = new JPanel(new GridLayout(3, 1));
        top.add(header);
        top.add(currentPhaseLabel);
        frame.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(center);
        frame.add(scroll, BorderLayout.CENTER);

        JButton phaseMinus = new JButton("Phase -");
        JButton phasePlus  = new JButton("Phase +");
        JPanel bottom = new JPanel();
        bottom.add(phaseMinus);
        bottom.add(phasePlus);
        frame.add(bottom, BorderLayout.SOUTH);

        // --- get or create cached collector ---
        TlsPhaseCollector collector = tlsCollectors.computeIfAbsent(
                tlsId,
                id -> {
                    TlsPhaseCollector c = new TlsPhaseCollector(controller, id);
                    c.initialize(netXmlReader.getDurations(id), netXmlReader.getStates(id));
                    return c;
                }
        );
        this.tlsPhaseCollector = collector;

        // --- populate UI with stored durations ---
        center.removeAll();
        for (PhaseEntry p : collector.getPhases()) {
            // make sure the text field reflects current stored duration
            p.durationField.setText(String.valueOf((int)p.duration));

            JLabel row = new JLabel("Phase " + p.index + " | State: " + p.state + " | Duration: ");
            JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            rowPanel.add(row);
            rowPanel.add(p.durationField);
            center.add(rowPanel);
        }

        center.revalidate();
        center.repaint();

        // --- UI update timer ---
        Timer uiTimer = new Timer(300, ev -> {
            try {
                if (controller.isRunning()) {
                    collector.observe(); // updates current phase from SUMO
                    int current = controller.getTlsPhase(tlsId);
                    currentPhaseLabel.setText("Current phase: " + current);

                    // highlight current phase
                    for (PhaseEntry p : collector.getPhases()) {
                        if (p.index == current) {
                            p.durationField.setBackground(Color.YELLOW);
                        } else {
                            p.durationField.setBackground(Color.WHITE);
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        uiTimer.start();

        phasePlus.addActionListener(e -> changeTlsPhase(+1));
        phaseMinus.addActionListener(e -> changeTlsPhase(-1));

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                uiTimer.stop();
            }
        });

        frame.setLocationRelativeTo(this);
        frame.setVisible(true);
    }

    private void initLayout() {

        String netPath = "C:\\Users\\hamza\\Desktop\\JAVA2Project\\Milestone2\\sumo\\Demo2.net.xml";
        mapPanel = new MapPanel(MapDataLoader.loadLanes(netPath));

        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        sumoExeField.setText("C:\\Program Files (x86)\\Eclipse\\Sumo\\bin\\sumo-gui");
        cfgField.setText("\"C:\\Users\\jsreu\\OneDrive\\Dokumente\\Milestone2-Abgabe\\sumo\\Demo2.sumocfg\"");

        gbc.gridx=0; gbc.gridy=0;
        topPanel.add(new JLabel("Pfad zu sumo-gui:"), gbc);
        gbc.gridx=1; topPanel.add(sumoExeField, gbc);

        gbc.gridx=0; gbc.gridy=1;
        topPanel.add(new JLabel("Pfad zur .sumocfg:"), gbc);
        gbc.gridx=1; topPanel.add(cfgField, gbc);

        gbc.gridy=2; gbc.gridx=0;
        topPanel.add(startButton, gbc);
        gbc.gridx=1; topPanel.add(pauseButton, gbc);
        gbc.gridx=2; topPanel.add(restartButton, gbc);
        gbc.gridx=3; topPanel.add(filterButton, gbc);

        gbc.gridy=3; gbc.gridx=0;
        topPanel.add(selectVehicleButton, gbc);
        gbc.gridx=1; topPanel.add(selectTlsButton, gbc);
        gbc.gridx=2; topPanel.add(injectVehicleButton, gbc);
        gbc.gridx=3; topPanel.add(stressTestButton, gbc);

        gbc.gridy=4; gbc.gridx=0;
        topPanel.add(new JLabel("Spawn time (sec):"), gbc);
        gbc.gridx=1; topPanel.add(spawnIntervalField, gbc);
        gbc.gridx=2; topPanel.add(autoSpawnButton, gbc);

        gbc.gridy=5; gbc.gridx=0; gbc.gridwidth=4;
        topPanel.add(statusLabel, gbc);

        startButton.addActionListener(this::onStartClicked);
        pauseButton.addActionListener(this::onPauseClicked);
        restartButton.addActionListener(this::onRestartClicked);
        spawnButton.addActionListener(this::onSpawnClicked);
        stressTestButton.addActionListener(this::onStressTestClicked);

        injectVehicleButton.addActionListener(e -> openInjectVehicleDialog());
        filterButton.addActionListener(e -> openFilterDialog());
        selectVehicleButton.addActionListener(e -> openSelectVehicleDialog());
        selectTlsButton.addActionListener(e -> openSelectTlsDialog());
        autoSpawnButton.addActionListener(e -> toggleAutoSpawn());

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(topPanel, BorderLayout.NORTH);
        getContentPane().add(mapPanel, BorderLayout.CENTER);
    }

    private void toggleAutoSpawn() {

        if (controller == null || !controller.isRunning()) {
            JOptionPane.showMessageDialog(this,
                    "Simulation must be running",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (autoSpawnRunning) {
            // STOP
            autoSpawnTimer.stop();
            autoSpawnRunning = false;
            autoSpawnButton.setText("Start Auto Spawn");
            statusLabel.setText("Auto spawn stopped");
            return;
        }

        // START
        int seconds;
        try {
            seconds = Integer.parseInt(spawnIntervalField.getText().trim());
            if (seconds <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Spawn time must be a positive integer",
                    "Invalid input",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        autoSpawnTimer = new Timer(seconds * 1000, ev -> spawnRandomVehicleSafe());
        autoSpawnTimer.start();

        autoSpawnRunning = true;
        autoSpawnButton.setText("Stop Auto Spawn");
        statusLabel.setText("Auto spawn every " + seconds + " sec");
    }



    private void initTimer() {
        timer = new Timer(200, ev -> {
            try {
                updateFromSumo();
            } catch (Exception ex) {
                ex.printStackTrace();
                statusLabel.setText("Error: " + ex.getMessage());
                timer.stop();
                updateUiState(false);
            }
        });
    }

    private void spawnRandomVehicleSafe() {

        if (controller == null || !controller.isRunning())
            return;

        try {
            List<String> routes = controller.getAllRoutes();
            if (routes.isEmpty())
                return;

            String route = routes.get(rand.nextInt(routes.size()));
            String id = "auto_" + System.nanoTime();

            // speed requested (SUMO will cap it)
            double speed = 5 + rand.nextInt(20); // (max depends on lane & vType)

            Color[] colors = {
                    Color.BLUE, Color.RED, Color.GREEN,
                    Color.ORANGE, Color.MAGENTA
            };
            Color color = colors[rand.nextInt(colors.length)];

            controller.injectVehicle(id, route, speed, color);

        } catch (Exception ex) {
            // NEVER crash auto spawn
            ex.printStackTrace();
        }
    }

    private void changeTlsPhase(int delta) {

        if (selectedTlsId == null || controller == null || !controller.isRunning())
            return;

        try {
            int current = controller.getTlsPhase(selectedTlsId);

            List<PhaseEntry> phases = tlsPhaseCollector.getPhases();
            if (phases.isEmpty()) return;

            int size = phases.size();
            int next = (current + delta + size) % size;

            controller.setTlsPhase(selectedTlsId, phases.get(next).index);
            controller.setTlsPhaseDuration(selectedTlsId, 999); // hold visibly

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateTrafficLightColors() throws Exception {

        for (TrafficLightBar bar : trafficLightBars) {
            String state =
                    controller.getRedYellowGreenState(bar.tlsId);

            if (bar.indexInState < state.length()) {
                bar.setStateChar(
                        state.charAt(bar.indexInState)
                );
            }
        }
    }

    private void onStartClicked(ActionEvent e) {

        try {
            if (controller == null) {
                controller = new SimulationController(
                        sumoExeField.getText().trim(),
                        cfgField.getText().trim()
                );
            }

            controller.start();
            defaultRoutes.clear();
            defaultRoutes.addAll(controller.getAllRoutes());
            timer.start();
            updateUiState(true);
            statusLabel.setText("Running");

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    "Start failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
    private void onPauseClicked(ActionEvent e) {
        if (autoSpawnRunning) {
            autoSpawnTimer.stop();
            autoSpawnRunning = false;
            autoSpawnButton.setText("Start Auto Spawn");
        }

        timer.stop();
        updateUiState(false);
        statusLabel.setText("Paused");
    }

    private void onRestartClicked(ActionEvent e) {

        try {
            if (controller != null) {
                controller.close();
                controller = null;
            }

            if (autoSpawnRunning) {
                autoSpawnTimer.stop();
                autoSpawnRunning = false;
                autoSpawnButton.setText("Start Auto Spawn");
            }

            timer.stop();
            trafficLightBars.clear();
            mapPanel.setVehicles(Collections.emptyList());
            mapPanel.setTrafficLights(Collections.emptyList());
            mapPanel.setSelectedVehicle(null);
            mapPanel.setHighlightedTlsLanes(Collections.emptyList());

            updateUiState(false);
            statusLabel.setText("Restarted");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    private void onSpawnClicked(ActionEvent e) {

        if (controller == null || !controller.isRunning())
            return;

        try {
            String id = "veh_" + rand.nextInt(99999);
            String route = controller.getAllRoutes().get(0);

            controller.injectVehicle(
                    id,
                    route,
                    10 + rand.nextInt(10),
                    Color.BLUE
            );

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    private void onStressTestClicked(ActionEvent e) {

        if (controller == null || !controller.isRunning())
            return;

        try {
            String route = controller.getAllRoutes().get(0);

            for (int i = 0; i < 50; i++) {
                String id = "stress_" + System.nanoTime();
                controller.injectVehicle(
                        id,
                        route,
                        5 + rand.nextInt(20),
                        Color.RED
                );
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void updateUiState(boolean running) {

        startButton.setEnabled(!running);
        pauseButton.setEnabled(running);
        restartButton.setEnabled(running);

        stressTestButton.setEnabled(running);
        injectVehicleButton.setEnabled(running);
        filterButton.setEnabled(running);
        selectVehicleButton.setEnabled(running);
        selectTlsButton.setEnabled(running);

        if (tlsDropdown != null) {
            tlsDropdown.setEnabled(running);
        }
        if (phasePlusBtn != null) {
            phasePlusBtn.setEnabled(running);
        }
        if (phaseMinusBtn != null) {
            phaseMinusBtn.setEnabled(running);
        }
        if (hold10Btn != null) {
            hold10Btn.setEnabled(running);
        }

        if (running && edgeFilterBox != null && edgeFilterBox.getItemCount() == 1) {
            try {
                for (String edge : controller.getAllEdges()) {
                    edgeFilterBox.addItem(edge);
                }
            } catch (Exception ignored) {}
        }
    }
    private void initTrafficLights() throws Exception {

        trafficLightBars.clear();

        if (tlsDropdown != null) {
            tlsDropdown.removeAllItems();
        }

        for (String tlsId : controller.getTrafficLightIds()) {

            if (tlsDropdown != null) {
                tlsDropdown.addItem(tlsId);
            }

            List<String> lanes = controller.getControlledLanes(tlsId);
            String state = controller.getRedYellowGreenState(tlsId);

            for (int i = 0; i < lanes.size(); i++) {
                String laneId = lanes.get(i);

                List<Point2D.Double> pts =
                        controller.getLaneShapePoints(laneId);

                if (pts.size() < 2) continue;

                Point2D.Double p1 = pts.get(pts.size() - 2);
                Point2D.Double p2 = pts.get(pts.size() - 1);

                TrafficLightBar bar = new TrafficLightBar(
                        tlsId,
                        laneId,
                        i,
                        p1.x, p1.y,
                        p2.x, p2.y
                );

                if (i < state.length()) {
                    bar.setStateChar(state.charAt(i));
                }

                trafficLightBars.add(bar);
            }
        }

        if (tlsDropdown != null) {
            tlsDropdown.setEnabled(true);
        }
        if (phaseMinusBtn != null) {
            phaseMinusBtn.setEnabled(true);
        }
        if (phasePlusBtn != null) {
            phasePlusBtn.setEnabled(true);
        }
        if (hold10Btn != null) {
            hold10Btn.setEnabled(true);
        }
    }

    private Color nameToColor(String name) {
        if (name == null || name.equals("Any")) return null;

        return switch (name) {
            case "Blue"    -> Color.BLUE;
            case "Red"     -> Color.RED;
            case "Green"   -> Color.GREEN;
            case "Orange"  -> Color.ORANGE;
            case "Magenta" -> Color.MAGENTA;
            default -> null;
        };
    }

    private String colorToName(Color color) {
        if (color == null) return "Any";
        if (color.equals(Color.BLUE)) return "Blue";
        if (color.equals(Color.RED)) return "Red";
        if (color.equals(Color.GREEN)) return "Green";
        if (color.equals(Color.ORANGE)) return "Orange";
        if (color.equals(Color.MAGENTA)) return "Magenta";
        return "Any";
    }

    private void openSelectTlsDialog() {

        if (controller == null || !controller.isRunning()) {
            JOptionPane.showMessageDialog(this,
                    "Simulation not running",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Select Traffic Light", true);
        dialog.setSize(300, 150);
        dialog.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> tlsBox = new JComboBox<>();

        try {
            for (String id : controller.getTrafficLightIds()) {
                tlsBox.addItem(id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        JButton selectBtn = new JButton("Select");
        JButton closeBtn  = new JButton("Close");

        gbc.gridx=0; gbc.gridy=0;
        dialog.add(new JLabel("Traffic Light:"), gbc);
        gbc.gridx=1;
        dialog.add(tlsBox, gbc);

        gbc.gridx=0; gbc.gridy=1;
        dialog.add(selectBtn, gbc);
        gbc.gridx=1;
        dialog.add(closeBtn, gbc);

        selectBtn.addActionListener(e -> {
            selectedTlsId = (String) tlsBox.getSelectedItem();
            try {
                openTlsInspectorSkeleton(selectedTlsId);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Failed to open TLS inspector:\n" + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
            dialog.dispose();
        });

        closeBtn.addActionListener(e -> dialog.dispose());

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openFilterDialog() {

        JDialog dialog = new JDialog(this, "Vehicle Filter", true);
        dialog.setSize(380, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JCheckBox enableBox = new JCheckBox("Enable filter");
        enableBox.setSelected(vehicleFilter.isEnabled());

        JComboBox<String> colorBox = new JComboBox<>(new String[]{
                "Any", "Blue", "Red", "Green", "Orange", "Magenta"
        });

        JTextField minSpeedField = new JTextField(6);
        JTextField maxSpeedField = new JTextField(6);

        JComboBox<String> edgeBox = new JComboBox<>();
        edgeBox.addItem("Any");

        try {
            for (String edge : controller.getAllEdges()) {
                edgeBox.addItem(edge);
            }
        } catch (Exception ignored) {}

        colorBox.setSelectedItem(colorToName(vehicleFilter.getColor()));

        if (vehicleFilter.getMinSpeed() != null)
            minSpeedField.setText(String.valueOf(vehicleFilter.getMinSpeed()));

        if (vehicleFilter.getMaxSpeed() != null)
            maxSpeedField.setText(String.valueOf(vehicleFilter.getMaxSpeed()));

        if (vehicleFilter.getEdge() != null)
            edgeBox.setSelectedItem(vehicleFilter.getEdge());

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridwidth = 2;
        dialog.add(enableBox, gbc);

        gbc.gridwidth = 1;
        gbc.gridy++;
        dialog.add(new JLabel("Color:"), gbc);
        gbc.gridx = 1;
        dialog.add(colorBox, gbc);

        gbc.gridx = 0; gbc.gridy++;
        dialog.add(new JLabel("Min speed:"), gbc);
        gbc.gridx = 1;
        dialog.add(minSpeedField, gbc);

        gbc.gridx = 0; gbc.gridy++;
        dialog.add(new JLabel("Max speed:"), gbc);
        gbc.gridx = 1;
        dialog.add(maxSpeedField, gbc);

        gbc.gridx = 0; gbc.gridy++;
        dialog.add(new JLabel("Road:"), gbc);
        gbc.gridx = 1;
        dialog.add(edgeBox, gbc);

        JButton applyBtn = new JButton("Apply");

        gbc.gridx = 0; gbc.gridy++;
        gbc.gridwidth = 2;
        dialog.add(applyBtn, gbc);

        applyBtn.addActionListener(e -> {

            vehicleFilter.setEnabled(enableBox.isSelected());

            vehicleFilter.setColor(
                    nameToColor((String) colorBox.getSelectedItem())
            );

            try {
                vehicleFilter.setMinSpeed(
                        minSpeedField.getText().isBlank()
                                ? null
                                : Double.parseDouble(minSpeedField.getText())
                );
            } catch (NumberFormatException ex) {
                vehicleFilter.setMinSpeed(null);
            }

            try {
                vehicleFilter.setMaxSpeed(
                        maxSpeedField.getText().isBlank()
                                ? null
                                : Double.parseDouble(maxSpeedField.getText())
                );
            } catch (NumberFormatException ex) {
                vehicleFilter.setMaxSpeed(null);
            }

            String edge = (String) edgeBox.getSelectedItem();
            vehicleFilter.setEdge("Any".equals(edge) ? null : edge);

            dialog.dispose();
        });

        dialog.setVisible(true);
    }

    private void openSelectVehicleDialog() {

        if (controller == null || !controller.isRunning()) {
            JOptionPane.showMessageDialog(this,
                    "Simulation not running",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> vehicleIds;
        try {
            vehicleIds = controller.getVehicleIds();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Failed to get vehicles:\n" + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (vehicleIds.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No vehicles currently exist in the simulation.",
                    "No vehicles",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Select Vehicle", true);
        dialog.setSize(300, 150);
        dialog.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> vehicleBox = new JComboBox<>();

        for (String id : vehicleIds) {
            // Only add vehicles that actually exist
            if (controller.vehicleExists(id)) {
                vehicleBox.addItem(id);
            }
        }

        // If no vehicles exist after filtering, show warning
        if (vehicleBox.getItemCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "No valid vehicles available to select.",
                    "No vehicles",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JButton selectBtn = new JButton("Select");
        JButton closeBtn  = new JButton("Close");

        gbc.gridx=0; gbc.gridy=0;
        dialog.add(new JLabel("Vehicle:"), gbc);
        gbc.gridx=1;
        dialog.add(vehicleBox, gbc);

        gbc.gridx=0; gbc.gridy=1;
        dialog.add(selectBtn, gbc);
        gbc.gridx=1;
        dialog.add(closeBtn, gbc);

        selectBtn.addActionListener(e -> {
            String selected = (String) vehicleBox.getSelectedItem();
            if (selected == null || !controller.vehicleExists(selected)) {
                JOptionPane.showMessageDialog(dialog,
                        "Selected vehicle does not exist anymore.",
                        "Error",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            selectedVehicleId = selected;
            mapPanel.setSelectedVehicle(selectedVehicleId);
            openVehicleInspectorSkeleton(selectedVehicleId);
            dialog.dispose();
        });

        closeBtn.addActionListener(e -> dialog.dispose());

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }


    private void updateFromSumo() throws Exception {

        if (controller == null || !controller.isRunning())
            return;

        controller.step();
        if (tlsPhaseCollector != null) {
            tlsPhaseCollector.applyDurationIfPhaseChanged();
        }

        if (trafficLightBars.isEmpty()) {
            initTrafficLights();
        }

        updateTrafficLightColors();
        mapPanel.setTrafficLights(trafficLightBars);

        List<SumoVehicle> vehicles = new ArrayList<>();

        for (String id : controller.getVehicleIds()) {
            SumoVehicle v = new SumoVehicle(id, controller);
            v.refresh(); // pulls position

            if (vehicleFilter.matches(v)) {
                vehicles.add(v);
            }
        }

        mapPanel.setVehicles(vehicles);
        mapPanel.setSelectedVehicle(selectedVehicleId);
    }
    private void openInjectVehicleDialog() {

        if (controller == null || !controller.isRunning()) {
            JOptionPane.showMessageDialog(this,
                    "Start the simulation first.",
                    "Not running",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Inject Vehicle", true);
        dialog.setSize(440, 500);
        dialog.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField idField = new JTextField("veh_" + System.nanoTime());
        JTextField speedField = new JTextField("10");
        JTextField amountField = new JTextField("1");

        JLabel maxSpeedLabel = new JLabel("Max speed: -");

        JComboBox<Color> colorBox = new JComboBox<>(new Color[]{
                Color.BLUE, Color.RED, Color.GREEN, Color.ORANGE, Color.MAGENTA
        });

        JComboBox<String> edgeBox = new JComboBox<>();
        JComboBox<String> routeBox = new JComboBox<>();

        edgeBox.addItem("None");
        routeBox.addItem("None");

        // ================= BUILD EDGE LIST FROM ROUTES =================

        Map<String, List<String>> edgeToRoutes = new HashMap<>();

        try {
            for (String route : defaultRoutes) {
                List<String> edges = controller.getRouteEdges(route);
                for (String edge : edges) {
                    edgeToRoutes
                            .computeIfAbsent(edge, k -> new ArrayList<>())
                            .add(route);
                }
            }

            for (String edge : edgeToRoutes.keySet()) {
                edgeBox.addItem(edge);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // ================= LAYOUT =================

        int y = 0;

        gbc.gridx=0; gbc.gridy=y;
        dialog.add(new JLabel("Vehicle ID:"), gbc);
        gbc.gridx=1;
        dialog.add(idField, gbc);

        gbc.gridx=0; gbc.gridy=++y;
        dialog.add(new JLabel("Speed:"), gbc);
        gbc.gridx=1;
        dialog.add(speedField, gbc);

        gbc.gridx=1; gbc.gridy=++y;
        dialog.add(maxSpeedLabel, gbc);

        gbc.gridx=0; gbc.gridy=++y;
        dialog.add(new JLabel("Amount:"), gbc);
        gbc.gridx=1;
        dialog.add(amountField, gbc);

        gbc.gridx=0; gbc.gridy=++y;
        dialog.add(new JLabel("Color:"), gbc);
        gbc.gridx=1;
        dialog.add(colorBox, gbc);

        gbc.gridx=0; gbc.gridy=++y;
        dialog.add(new JLabel("Edge:"), gbc);
        gbc.gridx=1;
        dialog.add(edgeBox, gbc);

        gbc.gridx=0; gbc.gridy=++y;
        dialog.add(new JLabel("Route:"), gbc);
        gbc.gridx=1;
        dialog.add(routeBox, gbc);

        // ================= EDGE → ROUTE FILTER =================

        edgeBox.addActionListener(e -> {
            routeBox.removeAllItems();
            routeBox.addItem("None");

            String edge = (String) edgeBox.getSelectedItem();
            if (edge == null || edge.equals("None"))
                return;

            for (String r : edgeToRoutes.getOrDefault(edge, Collections.emptyList())) {
                if (defaultRoutes.contains(r)) {
                    routeBox.addItem(r);
                }
            }
        });

        // ================= ROUTE → SPEED PREVIEW =================

        routeBox.addActionListener(e -> {
            try {
                String route = (String) routeBox.getSelectedItem();
                if (route == null || route.equals("None")) return;

                List<String> edges = controller.getRouteEdges(route);
                String laneId = edges.get(0) + "_0";
                double max = controller.getLaneMaxSpeed(laneId);

                maxSpeedLabel.setText(String.format("Max speed: %.1f m/s", max));
            } catch (Exception ex) {
                maxSpeedLabel.setText("Max speed: ?");
            }
        });

        // ================= BUTTONS =================

        JButton injectBtn = new JButton("Inject");
        JButton cancelBtn = new JButton("Cancel");

        injectBtn.addActionListener(e -> {
            try {
                int amount = Integer.parseInt(amountField.getText());
                double requestedSpeed = Double.parseDouble(speedField.getText());
                Color color = (Color) colorBox.getSelectedItem();

                String edge = (String) edgeBox.getSelectedItem();
                String route = (String) routeBox.getSelectedItem();

                if (edge == null || edge.equals("None"))
                    throw new IllegalArgumentException("No edge selected");

                if (route == null || route.equals("None"))
                    throw new IllegalArgumentException("No route selected");

                List<String> fullEdges = controller.getRouteEdges(route);
                int startIndex = fullEdges.indexOf(edge);

                if (startIndex < 0)
                    throw new IllegalStateException("Edge not in route");

                List<String> subRoute = fullEdges.subList(startIndex, fullEdges.size());

                String tempRouteId = "tmp_" + route + "_" + edge + "_" + System.nanoTime();
                controller.addTemporaryRoute(tempRouteId, subRoute);

                String laneId = subRoute.get(0) + "_0";
                double maxSpeed = controller.getLaneMaxSpeed(laneId);
                double appliedSpeed = Math.min(requestedSpeed, maxSpeed);

                for (int i = 0; i < amount; i++) {
                    String id = amount == 1
                            ? idField.getText()
                            : idField.getText() + "_" + i;

                    if (!controller.vehicleExists(id)) {
                        controller.injectVehicle(id, tempRouteId, appliedSpeed, color);
                    }
                }

                dialog.dispose();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog,
                        ex.getMessage(),
                        "Injection failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        gbc.gridx=0; gbc.gridy=++y;
        dialog.add(cancelBtn, gbc);
        gbc.gridx=1;
        dialog.add(injectBtn, gbc);

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // =============================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GUI viewer = new GUI();
            viewer.setVisible(true);
        });
    }
}


