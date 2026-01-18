import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.geom.Point2D;
import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class App extends Application {

    // ============== DARK BLUE THEME ==============
    private static final String DARK_BG = "#0d1b2a";
    private static final String DARKER_BG = "#081420";
    private static final String ACCENT_BLUE = "#1b3a4b";
    private static final String HIGHLIGHT = "#415a77";
    private static final String LIGHT_TEXT = "#e0e1dd";
    private static final String CYAN_ACCENT = "#00d4ff";

    // ============== ICON PATHS ==============
    private static final String ICON_VEHICLES = "Images/Multiple_Cars.png";
    private static final String ICON_TRAFFIC_LIGHTS = "Images/Traffic_Light.png";
    private static final String ICON_ROADS = "Images/Road.png";
    private static final String ICON_CHARTS = "Images/Chart.png";
    private static final String ICON_MENU = "Images/menu1.png";

    // === SUMO Configuration ===
    private static final String SUMO_BIN = "sumo-gui";
    private String sumoCfgPath = null;
    private String netFilePath = null;

    private double x, y = 0;
    private AnchorPane slider;
    private Label menuLabel, menuCloseLabel;
    private Slider zoomSlider;
    private Pane sumoMapPane, mapContent;
    private Canvas mapCanvas;

    // === SUMO Components ===
    private SimulationController simController;
    private List<LaneShape> lanes = new ArrayList<>();
    private List<SumoVehicle> vehicles = new CopyOnWriteArrayList<>();
    private List<TrafficLightBar> trafficLightBars = new CopyOnWriteArrayList<>();
    private NetXmlReader netXmlReader;
    private Map<String, TlsPhaseCollector> tlsCollectors = new HashMap<>();

    // Auswahl
    private String selectedVehicleId = null;
    private String selectedEdge = null;
    private String selectedTrafficLight = null;
    private String selectedRoute = null;

    // Map bounds
    private double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
    private double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;

    // Animation
    private AnimationTimer simulationLoop;
    private boolean simulationRunning = false;

    // Info Labels
    private Label vehicleIdLabel, vehicleSpeedLabel, vehicleColorLabel, vehicleLocationLabel;
    private Label selectedTlLabel, selectedEdgeLabel;

    // Statistics
    private VehicleCountSeries vehicleCountSeries = new VehicleCountSeries(300);
    private int vehiclesEver = 0;
    private Set<String> seenVehicles = new HashSet<>();

    // Analytics & Filter
    private AnalyticsService analyticsService = new AnalyticsService();
    private VehicleFilter vehicleFilter = new VehicleFilter();

    // Vehicle position tracking für Rotation
    private Map<String, double[]> vehicleTracking = new HashMap<>(); // [lastX, lastY, angle]

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        
        AnchorPane root = new AnchorPane();
        root.setStyle("-fx-background-color: " + DARK_BG + ";");

        BorderPane borderPane = new BorderPane();
        setAnchors(borderPane, 0.0, 0.0, 0.0, 0.0);

        AnchorPane topPane = createTopPane();
        borderPane.setTop(topPane);

        sumoMapPane = createSumoMapPane();
        borderPane.setCenter(sumoMapPane);

        slider = createSlider();
        borderPane.setLeft(slider);

        root.getChildren().add(borderPane);
        initializeSliderAnimation();

        primaryStage.setMaximized(true);
        primaryStage.setTitle("SUMO Traffic Simulation GUI");

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        mapCanvas.widthProperty().bind(sumoMapPane.widthProperty());
        mapCanvas.heightProperty().bind(sumoMapPane.heightProperty());

        Platform.runLater(this::initializeSumo);
    }

    private void initializeSumo() {
        try {
            System.out.println("═══════════════════════════════════════════");
            System.out.println("[DEBUG] Working Directory: " + System.getProperty("user.dir"));
            System.out.println("[DEBUG] sumoCfgPath: " + sumoCfgPath);
            System.out.println("[DEBUG] netFilePath: " + netFilePath);
            
            java.io.File cfgFile = new java.io.File(sumoCfgPath);
            java.io.File netFile = new java.io.File(netFilePath);
            
            System.out.println("[DEBUG] CFG exists: " + cfgFile.exists() + " → " + cfgFile.getAbsolutePath());
            System.out.println("[DEBUG] NET exists: " + netFile.exists() + " → " + netFile.getAbsolutePath());
            System.out.println("═══════════════════════════════════════════");

            if (netFilePath != null && !netFilePath.isEmpty() && netFile.exists()) {
                lanes = MapDataLoader.loadLanes(netFilePath);
                computeBounds();

                netXmlReader = new NetXmlReader();
                netXmlReader.parse(netFilePath);

                showNotification("✓ Netzwerk geladen: " + lanes.size() + " Lanes");
            }

            if (sumoCfgPath != null && !sumoCfgPath.isEmpty() && cfgFile.exists()) {
                simController = new SimulationController(SUMO_BIN, sumoCfgPath);
                simController.start();

                for (String tlsId : simController.getTrafficLightIds()) {
                    TlsPhaseCollector collector = new TlsPhaseCollector(simController, tlsId);
                    collector.initialize(
                        netXmlReader.getDurations(tlsId),
                        netXmlReader.getStates(tlsId)
                    );
                    tlsCollectors.put(tlsId, collector);
                    createTrafficLightBars(tlsId);
                }

                showNotification("✓ SUMO gestartet");
                startSimulationLoop();
            }

            renderMap();

        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTrafficLightBars(String tlsId) {
        try {
            List<String> controlledLanes = simController.getControlledLanes(tlsId);
            int index = 0;
            for (String laneId : controlledLanes) {
                List<Point2D.Double> shape = simController.getLaneShapePoints(laneId);
                if (shape.size() >= 2) {
                    Point2D.Double p1 = shape.get(shape.size() - 2);
                    Point2D.Double p2 = shape.get(shape.size() - 1);
                    trafficLightBars.add(new TrafficLightBar(tlsId, laneId, index, p1.x, p1.y, p2.x, p2.y));
                }
                index++;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void computeBounds() {
        for (LaneShape lane : lanes) {
            for (Point2D.Double p : lane.getPoints()) {
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
            }
        }
    }

    private void startSimulationLoop() {
        simulationRunning = true;
        simulationLoop = new AnimationTimer() {
            private long lastUpdate = 0;
            @Override
            public void handle(long now) {
                if (now - lastUpdate >= 50_000_000) {
                    lastUpdate = now;
                    simulationStep();
                }
            }
        };
        simulationLoop.start();
    }

    private void simulationStep() {
        if (simController == null || !simController.isRunning()) return;

        try {
            simController.step();

            vehicles.clear();
            for (String vehId : simController.getVehicleIds()) {
                SumoVehicle v = new SumoVehicle(vehId, simController);
                v.refresh();
                vehicles.add(v);

                if (!seenVehicles.contains(vehId)) {
                    seenVehicles.add(vehId);
                    vehiclesEver++;
                }
            }

            vehicleCountSeries.add(System.currentTimeMillis(), vehicles.size());

            for (String tlsId : simController.getTrafficLightIds()) {
                String state = simController.getRedYellowGreenState(tlsId);
                for (TrafficLightBar bar : trafficLightBars) {
                    if (bar.tlsId.equals(tlsId) && bar.indexInState < state.length()) {
                        bar.setStateChar(state.charAt(bar.indexInState));
                    }
                }
            }

            updateSelectedVehicleInfo();
            
            // Cleanup: Entferne Tracking-Daten für nicht mehr existierende Fahrzeuge
            Set<String> currentVehicleIds = vehicles.stream()
                .map(SumoVehicle::getId)
                .collect(Collectors.toSet());
            vehicleTracking.keySet().removeIf(id -> !currentVehicleIds.contains(id));
            
            Platform.runLater(this::renderMap);

        } catch (Exception e) {}
    }

    private void updateSelectedVehicleInfo() {
        if (selectedVehicleId == null) return;
        for (SumoVehicle v : vehicles) {
            if (v.getId().equals(selectedVehicleId)) {
                Platform.runLater(() -> {
                    if (vehicleIdLabel != null) vehicleIdLabel.setText("ID: " + v.getId());
                    if (vehicleSpeedLabel != null) {
                        try { vehicleSpeedLabel.setText(String.format("Speed: %.1f m/s", v.getSpeed())); } catch (Exception ex) {}
                    }
                    if (vehicleColorLabel != null) {
                        java.awt.Color c = v.getColor();
                        vehicleColorLabel.setText(String.format("Color: RGB(%d,%d,%d)", c.getRed(), c.getGreen(), c.getBlue()));
                    }
                    if (vehicleLocationLabel != null) {
                        vehicleLocationLabel.setText(String.format("Pos: %.1f, %.1f", v.getX(), v.getY()));
                    }
                });
                break;
            }
        }
    }

    private void renderMap() {
        if (mapCanvas == null) return;

        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        double w = mapCanvas.getWidth();
        double h = mapCanvas.getHeight();

        if (w <= 0 || h <= 0) return;

        gc.setFill(Color.web(DARK_BG));
        gc.fillRect(0, 0, w, h);

        if (lanes.isEmpty()) {
            gc.setFill(Color.web("#4a4a6a"));
            gc.setFont(Font.font("System", FontWeight.BOLD, 24));
            gc.fillText("🗺 SUMO Simulation Map", w/2 - 150, h/2);
            return;
        }

        double margin = 30;
        double scaleX = (w - 2 * margin) / (maxX - minX);
        double scaleY = (h - 2 * margin) / (maxY - minY);
        double scale = Math.min(scaleX, scaleY);

        // Draw lanes
        for (LaneShape lane : lanes) {
            boolean highlight = selectedEdge != null && lane.getId().startsWith(selectedEdge);
            gc.setStroke(highlight ? Color.ORANGE : Color.DARKGRAY);
            gc.setLineWidth(highlight ? 4 : 2);

            List<Point2D.Double> pts = lane.getPoints();
            for (int i = 0; i < pts.size() - 1; i++) {
                Point2D.Double p1 = pts.get(i);
                Point2D.Double p2 = pts.get(i + 1);
                double x1 = (p1.x - minX) * scale + margin;
                double y1 = h - ((p1.y - minY) * scale + margin);
                double x2 = (p2.x - minX) * scale + margin;
                double y2 = h - ((p2.y - minY) * scale + margin);
                gc.strokeLine(x1, y1, x2, y2);
            }
        }

        // Draw traffic lights
        gc.setLineWidth(5);
        for (TrafficLightBar tl : trafficLightBars) {
            java.awt.Color awtColor = tl.getColor();
            gc.setStroke(Color.rgb(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue()));
            double x1 = (tl.x1 - minX) * scale + margin;
            double y1 = h - ((tl.y1 - minY) * scale + margin);
            double x2 = (tl.x2 - minX) * scale + margin;
            double y2 = h - ((tl.y2 - minY) * scale + margin);
            gc.strokeLine(x1, y1, x2, y2);
        }

        // Draw vehicles (with filter) - als Auto-Symbole
        int renderedCount = 0;
        int filteredOut = 0;
        for (SumoVehicle v : vehicles) {
            // Filter prüfen (mit robuster Fehlerbehandlung)
            boolean shouldRender = true;
            try {
                shouldRender = vehicleFilter.matches(v);
            } catch (Exception ex) {
                // Bei Fehler trotzdem anzeigen
                shouldRender = true;
            }
            
            if (!shouldRender) {
                filteredOut++;
                continue;
            }
            renderedCount++;

            double vx = (v.getX() - minX) * scale + margin;
            double vy = h - ((v.getY() - minY) * scale + margin);
            java.awt.Color awtColor = v.getRenderColor();
            Color carColor = Color.rgb(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());

            // Berechne Fahrtrichtung basierend auf Bewegung (nicht Lane)
            double angle = 0;
            String vehId = v.getId();
            double[] lastData = vehicleTracking.get(vehId);
            
            if (lastData != null) {
                double dx = v.getX() - lastData[0];
                double dy = v.getY() - lastData[1];
                double dist = Math.sqrt(dx * dx + dy * dy);
                
                if (dist > 0.5) { // Nur bei signifikanter Bewegung Winkel aktualisieren
                    // Winkel berechnen: atan2 mit Y-Flip für Canvas
                    angle = Math.atan2(-dy, dx);
                    vehicleTracking.put(vehId, new double[]{v.getX(), v.getY(), angle});
                } else {
                    // Letzten Winkel beibehalten
                    angle = lastData[2];
                    vehicleTracking.put(vehId, new double[]{v.getX(), v.getY(), angle});
                }
            } else {
                // Erstes Mal - versuche Winkel aus Lane zu schätzen
                try {
                    String edge = v.getCurrentEdge();
                    if (edge != null && !edge.isEmpty()) {
                        for (LaneShape lane : lanes) {
                            if (lane.getId().startsWith(edge + "_")) {
                                List<Point2D.Double> pts = lane.getPoints();
                                if (pts.size() >= 2) {
                                    // Verwende erstes Segment der Lane als Initial-Richtung
                                    double dx = pts.get(1).x - pts.get(0).x;
                                    double dy = pts.get(1).y - pts.get(0).y;
                                    angle = Math.atan2(-dy, dx);
                                }
                                break;
                            }
                        }
                    }
                } catch (Exception ex) {}
                vehicleTracking.put(vehId, new double[]{v.getX(), v.getY(), angle});
            }

            // Zeichne Auto
            gc.save();
            gc.translate(vx, vy);
            gc.rotate(Math.toDegrees(angle));

            // Auto-Größe
            double carLength = 12;
            double carWidth = 6;

            // Schatten
            gc.setFill(Color.rgb(0, 0, 0, 0.3));
            gc.fillRoundRect(-carLength/2 + 1, -carWidth/2 + 1, carLength, carWidth, 3, 3);

            // Karosserie
            gc.setFill(carColor);
            gc.fillRoundRect(-carLength/2, -carWidth/2, carLength, carWidth, 3, 3);

            // Windschutzscheibe (vorne - rechts im lokalen Koordinatensystem)
            gc.setFill(Color.rgb(150, 200, 255, 0.7));
            gc.fillRoundRect(carLength/2 - 4, -carWidth/2 + 1, 3, carWidth - 2, 1, 1);

            // Heckscheibe (hinten - links im lokalen Koordinatensystem)
            gc.setFill(Color.rgb(100, 150, 200, 0.5));
            gc.fillRoundRect(-carLength/2 + 1, -carWidth/2 + 1, 2, carWidth - 2, 1, 1);

            // Scheinwerfer vorne (2 kleine gelbe Punkte)
            gc.setFill(Color.rgb(255, 255, 180));
            gc.fillOval(carLength/2 - 2, -carWidth/2 + 0.5, 1.5, 1.5);
            gc.fillOval(carLength/2 - 2, carWidth/2 - 2, 1.5, 1.5);

            // Rücklichter (2 kleine rote Rechtecke)
            gc.setFill(Color.rgb(255, 0, 0));
            gc.fillRect(-carLength/2, -carWidth/2 + 0.5, 1, 1.5);
            gc.fillRect(-carLength/2, carWidth/2 - 2, 1, 1.5);

            // Rahmen
            gc.setStroke(Color.rgb(20, 20, 20));
            gc.setLineWidth(0.5);
            gc.strokeRoundRect(-carLength/2, -carWidth/2, carLength, carWidth, 3, 3);

            gc.restore();

            // Ausgewähltes Fahrzeug hervorheben (außerhalb der Rotation)
            if (v.getId().equals(selectedVehicleId)) {
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(2);
                gc.strokeOval(vx - 12, vy - 12, 24, 24);
                
                gc.setStroke(Color.web(CYAN_ACCENT));
                gc.setLineWidth(1);
                gc.strokeOval(vx - 15, vy - 15, 30, 30);
            }
        }

        // Stats overlay
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 12));
        String statsText = "Vehicles: " + vehicles.size() + " | Total: " + vehiclesEver;
        if (vehicleFilter.isEnabled()) {
            statsText += " | Filter: " + renderedCount + " gezeigt, " + filteredOut + " versteckt";
        }
        gc.fillText(statsText, 10, h - 10);
    }

    // ============== TOP PANE ==============
    private AnchorPane createTopPane() {
        AnchorPane topPane = new AnchorPane();
        topPane.setPrefHeight(65);
        topPane.setStyle("-fx-background-color: #ffffff;");

        Pane darkBar = new Pane();
        darkBar.setPrefHeight(25);
        darkBar.setStyle("-fx-background-color: #032d4d;");
        setAnchors(darkBar, 0.0, 0.0, null, 0.0);

        HBox controlBox = new HBox(10);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        controlBox.setLayoutX(10);
        controlBox.setLayoutY(2);
        controlBox.getChildren().addAll(
            createControlButton("▶", "#27ae60", this::onPlay),
            createControlButton("⏸", "#f39c12", this::onPause),
            createControlButton("⏹", "#e74c3c", this::onStop),
            createControlButton("⚙", "#3498db", this::showConfigDialog)
        );
        darkBar.getChildren().add(controlBox);

        Button exitBtn = createControlButton("✕", "#ff5555", () -> {
            if (simController != null) simController.close();
            Platform.exit();
        });
        AnchorPane.setRightAnchor(exitBtn, 10.0);
        AnchorPane.setTopAnchor(exitBtn, 2.0);
        darkBar.getChildren().add(exitBtn);

        HBox zoomBox = new HBox(10);
        zoomBox.setAlignment(Pos.CENTER);
        zoomBox.setPrefHeight(40);
        AnchorPane.setTopAnchor(zoomBox, 25.0);
        AnchorPane.setRightAnchor(zoomBox, 20.0);

        Label zoomLabel = new Label("🔍 Zoom:");
        zoomLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        zoomSlider = new Slider(0.5, 3.0, 1.0);
        zoomSlider.setPrefWidth(200);
        zoomSlider.setShowTickLabels(true);
        Label zoomValueLabel = new Label("100%");
        zoomValueLabel.setFont(Font.font("System", FontWeight.BOLD, 12));

        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            zoomValueLabel.setText((int)(newVal.doubleValue() * 100) + "%");
            if (mapContent != null) {
                mapContent.setScaleX(newVal.doubleValue());
                mapContent.setScaleY(newVal.doubleValue());
            }
        });

        zoomBox.getChildren().addAll(zoomLabel, zoomSlider, zoomValueLabel);

        menuLabel = createMenuLabel("☰ MENU");
        AnchorPane.setLeftAnchor(menuLabel, 14.0);
        AnchorPane.setTopAnchor(menuLabel, 36.0);

        menuCloseLabel = createMenuLabel("✕ CLOSE");
        AnchorPane.setLeftAnchor(menuCloseLabel, 14.0);
        AnchorPane.setTopAnchor(menuCloseLabel, 36.0);
        menuCloseLabel.setVisible(false);

        topPane.getChildren().addAll(darkBar, zoomBox, menuLabel, menuCloseLabel);
        return topPane;
    }

    // ============== SIDEBAR ==============
    private AnchorPane createSlider() {
        AnchorPane sliderPane = new AnchorPane();
        sliderPane.setPrefSize(260, 475);
        sliderPane.setStyle("-fx-background-color: linear-gradient(to bottom, " + ACCENT_BLUE + ", " + DARK_BG + ");");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        setAnchors(scrollPane, 20.0, 0.0, 10.0, 0.0);

        VBox sidebarBox = new VBox(5);
        sidebarBox.setPadding(new Insets(10));

        // === VEHICLE MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("🚗  Vehicles", ICON_VEHICLES,
            createSubButton("🚗 Inject Vehicle", this::showInjectVehicleDialog),
            createSubButton("🎯 Select Vehicle", this::showVehicleListWindow),
            createSubButton("🔍 Filter", this::showFilterWindow),
            createExpandableSubMenu("📋 Selected Info",
                vehicleIdLabel = createInfoLabel("ID: —"),
                vehicleSpeedLabel = createInfoLabel("Speed: —"),
                vehicleColorLabel = createInfoLabel("Color: —"),
                vehicleLocationLabel = createInfoLabel("Pos: —")
            )
        ));

        // === TRAFFIC LIGHTS MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("🚦  Traffic Lights", ICON_TRAFFIC_LIGHTS,
            createSubButton("🎯 Select Traffic Light", this::onSelectTrafficLight),
            selectedTlLabel = createInfoLabel("Selected: —"),
            createSeparator(),
            createSubButton("👁 View Inspector", this::showTrafficLightInspector),
            createSeparator(),
            createSubLabel("Quick Control:"),
            createPhaseButton("🟢 GREEN", "#27ae60", this::onSetGreen),
            createPhaseButton("🟡 YELLOW", "#f39c12", this::onSetYellow),
            createPhaseButton("🔴 RED", "#e74c3c", this::onSetRed)
        ));

        // === ROADS MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("🛣  Roads", ICON_ROADS,
            createSubButton("📍 Select Route", this::onSelectRoute),
            createSubButton("🛤 Select Edge", this::onSelectEdge),
            selectedEdgeLabel = createInfoLabel("Selected: —")
        ));

        // === CHARTS MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("📊  Charts", ICON_CHARTS,
            createSubButton("📈 Average Speed", this::onShowAverageSpeed),
            createSubButton("📊 Vehicle Density", this::onShowVehicleDensity),
            createSubButton("🔥 Congestion", this::onShowCongestion),
            createSubButton("⏱ Travel Time", this::onShowTravelTime),
            createSeparator(),
            createSubButton("💾 Export Reports", this::onExportReports)
        ));

        scrollPane.setContent(sidebarBox);
        sliderPane.getChildren().add(scrollPane);
        return sliderPane;
    }

    // ============== VEHICLE LIST WINDOW ==============
    private void showVehicleListWindow() {
        if (simController == null) { showNotification("⚠ SUMO nicht verbunden!"); return; }
        if (vehicles.isEmpty()) { showNotification("⚠ Keine Fahrzeuge!"); return; }

        Stage window = createDarkWindow("🚗 Vehicle List", 400, 500);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + DARK_BG + ";");

        Label title = createTitleLabel("Aktive Fahrzeuge (" + vehicles.size() + ")");

        ListView<String> listView = new ListView<>();
        listView.setStyle("-fx-background-color: " + DARKER_BG + "; -fx-control-inner-background: " + DARKER_BG + "; -fx-text-fill: white;");
        listView.setPrefHeight(350);

        ObservableList<String> vehicleNames = FXCollections.observableArrayList();
        vehicles.forEach(v -> vehicleNames.add(v.getId()));
        listView.setItems(vehicleNames);

        listView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: " + DARKER_BG + ";");
                } else {
                    setText("🚗 " + item);
                    setTextFill(Color.WHITE);
                    setStyle("-fx-background-color: " + DARKER_BG + ";");
                }
            }
        });

        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String sel = listView.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    selectedVehicleId = sel;
                    showVehicleInspector(sel);
                }
            }
        });

        Button openBtn = createStyledButton("🔍 Öffnen", "#3498db");
        openBtn.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                selectedVehicleId = sel;
                showVehicleInspector(sel);
            }
        });

        Button refreshBtn = createStyledButton("🔄 Aktualisieren", "#27ae60");
        refreshBtn.setOnAction(e -> {
            vehicleNames.clear();
            vehicles.forEach(v -> vehicleNames.add(v.getId()));
            title.setText("Aktive Fahrzeuge (" + vehicles.size() + ")");
        });

        HBox buttons = new HBox(15, openBtn, refreshBtn);
        buttons.setAlignment(Pos.CENTER);

        root.getChildren().addAll(title, listView, buttons);
        window.setScene(new Scene(root));
        window.show();
    }

    // ============== VEHICLE INSPECTOR WINDOW ==============
    private void showVehicleInspector(String vehicleId) {
        if (simController == null) return;

        Stage window = createDarkWindow("🚗 " + vehicleId, 420, 600);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + DARK_BG + ";");

        // Header
        Label header = new Label("🚗 " + vehicleId);
        header.setFont(Font.font("System", FontWeight.BOLD, 22));
        header.setTextFill(Color.web(CYAN_ACCENT));

        // === SPEED SECTION ===
        VBox speedSection = createDarkSection("⚡ Geschwindigkeit");
        
        HBox speedInputRow = new HBox(10);
        speedInputRow.setAlignment(Pos.CENTER_LEFT);
        TextField speedInput = new TextField();
        speedInput.setPromptText("Speed (m/s) - leer = Straße");
        speedInput.setPrefWidth(180);
        styleTextField(speedInput);

        Button applySpeedBtn = createStyledButton("Apply", "#27ae60");
        applySpeedBtn.setOnAction(e -> {
            try {
                String text = speedInput.getText().trim();
                if (text.isEmpty()) {
                    simController.setVehicleSpeed(vehicleId, -1);
                    showNotification("✓ " + vehicleId + " folgt Straße");
                } else {
                    double speed = Double.parseDouble(text);
                    simController.setVehicleSpeed(vehicleId, speed);
                    showNotification("✓ " + vehicleId + " → " + speed + " m/s");
                }
            } catch (Exception ex) { showNotification("✗ Ungültig"); }
        });
        speedInputRow.getChildren().addAll(speedInput, applySpeedBtn);

        Label currentSpeedLbl = createDarkLabel("Current Speed: —");
        Label maxSpeedLbl = createDarkLabel("Max Speed: —");
        Label avgSpeedLbl = createDarkLabel("Average Speed: —");
        speedSection.getChildren().addAll(speedInputRow, currentSpeedLbl, maxSpeedLbl, avgSpeedLbl);

        // === EDGE SECTION ===
        VBox edgeSection = createDarkSection("📍 Position");
        Label edgeLbl = createDarkLabel("Edge: —");
        edgeSection.getChildren().add(edgeLbl);

        // === COLOR SECTION ===
        VBox colorSection = createDarkSection("🎨 Farbe");
        HBox colorRow = new HBox(10);
        colorRow.setAlignment(Pos.CENTER_LEFT);
        ColorPicker colorPicker = new ColorPicker(Color.BLUE);
        Button applyColorBtn = createStyledButton("Apply", "#9b59b6");
        applyColorBtn.setOnAction(e -> {
            try {
                Color c = colorPicker.getValue();
                java.awt.Color awt = new java.awt.Color((int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
                simController.setVehicleColor(vehicleId, awt);
                showNotification("✓ Farbe geändert");
            } catch (Exception ex) { showNotification("✗ " + ex.getMessage()); }
        });
        colorRow.getChildren().addAll(colorPicker, applyColorBtn);
        colorSection.getChildren().add(colorRow);

        // === ROUTE SECTION ===
        VBox routeSection = createDarkSection("🛣 Route");
        Label currentRouteLbl = createDarkLabel("Current Route: —");
        
        Label changeRouteLbl = createDarkLabel("Change Route:");
        ComboBox<String> routeCombo = new ComboBox<>();
        routeCombo.setPrefWidth(280);
        routeCombo.setStyle("-fx-background-color: " + DARKER_BG + "; -fx-text-fill: white;");

        Button changeRouteBtn = createStyledButton("Route ändern", "#e67e22");
        changeRouteBtn.setOnAction(e -> {
            String newRoute = routeCombo.getValue();
            if (newRoute != null) {
                try {
                    simController.setVehicleRoute(vehicleId, newRoute);
                    showNotification("✓ Route: " + newRoute);
                } catch (Exception ex) { showNotification("✗ " + ex.getMessage()); }
            }
        });
        routeSection.getChildren().addAll(currentRouteLbl, changeRouteLbl, routeCombo, changeRouteBtn);

        root.getChildren().addAll(header, new Separator(), speedSection, edgeSection, colorSection, routeSection);

        // === UPDATE TIMER ===
        Timeline timer = new Timeline(new KeyFrame(Duration.millis(500), e -> {
            try {
                if (!simController.vehicleExists(vehicleId)) {
                    window.setTitle("🚗 " + vehicleId + " (GONE)");
                    return;
                }

                double speed = simController.getVehicleSpeed(vehicleId);
                currentSpeedLbl.setText(String.format("Current Speed: %.1f m/s (%.1f km/h)", speed, speed * 3.6));

                String edge = simController.getVehicleEdge(vehicleId);
                edgeLbl.setText("Edge: " + (edge != null ? edge : "—"));

                if (edge != null && !edge.isEmpty()) {
                    try {
                        double maxSpeed = simController.getLaneMaxSpeed(edge + "_0");
                        maxSpeedLbl.setText(String.format("Max Speed: %.1f m/s", maxSpeed));
                    } catch (Exception ex) {}
                }

                // Average speed
                var snapshot = simController.getDataGathering().getStore().snapshot();
                double avg = snapshot.stream()
                    .filter(p -> p instanceof VehicleTelemetry)
                    .map(p -> (VehicleTelemetry) p)
                    .filter(v -> v.getVehicleId().equals(vehicleId))
                    .mapToDouble(VehicleTelemetry::getSpeed)
                    .average().orElse(0);
                avgSpeedLbl.setText(String.format("Average Speed: %.1f m/s", avg));

                // Color
                java.awt.Color awt = simController.getVehicleColor(vehicleId);
                colorPicker.setValue(Color.rgb(awt.getRed(), awt.getGreen(), awt.getBlue()));

                // Route
                String route = simController.getVehicleRoute(vehicleId);
                currentRouteLbl.setText("Current Route: " + (route != null ? route : "—"));

                // Update available routes based on current edge
                if (edge != null) {
                    try {
                        List<String> allRoutes = simController.getAllRoutes();
                        List<String> available = new ArrayList<>();
                        
                        // Versuche Routen zu finden, die die aktuelle Edge enthalten
                        for (String r : allRoutes) {
                            try {
                                List<String> edges = simController.getRouteEdges(r);
                                if (edges != null && edges.contains(edge)) {
                                    available.add(r);
                                }
                            } catch (Exception ex) {}
                        }
                        
                        // Fallback: Wenn keine passenden gefunden, zeige alle
                        if (available.isEmpty()) {
                            available.addAll(allRoutes);
                        }
                        
                        // Nur aktualisieren wenn sich die Liste geändert hat
                        List<String> currentItems = new ArrayList<>(routeCombo.getItems());
                        if (!available.equals(currentItems)) {
                            String selected = routeCombo.getValue();
                            routeCombo.getItems().setAll(available);
                            if (selected != null && available.contains(selected)) {
                                routeCombo.setValue(selected);
                            } else if (route != null && available.contains(route)) {
                                routeCombo.setValue(route);
                            } else if (!available.isEmpty()) {
                                routeCombo.setValue(available.get(0));
                            }
                        }
                    } catch (Exception ex) {}
                }

            } catch (Exception ex) {}
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();

        window.setOnCloseRequest(e -> timer.stop());
        window.setScene(new Scene(root));
        window.show();
    }

    // ============== FILTER WINDOW ==============
    private void showFilterWindow() {
        Stage window = createDarkWindow("🔍 Vehicle Filter", 500, 650);

        VBox root = new VBox(15);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: " + DARK_BG + ";");

        Label title = createTitleLabel("🔍 Filter & Tools");

        // Enable/Disable Filter
        CheckBox enableCheck = new CheckBox("Filter aktivieren");
        enableCheck.setTextFill(Color.WHITE);
        enableCheck.setFont(Font.font("System", FontWeight.BOLD, 14));
        enableCheck.setSelected(vehicleFilter.isEnabled());

        // === COLOR TOOL (nicht Filter!) ===
        VBox colorSection = createDarkSection("🎨 Alle Fahrzeuge färben");
        Label colorInfo = createDarkLabel("Ändert die Farbe ALLER Fahrzeuge:");
        colorInfo.setStyle("-fx-font-size: 12;");
        
        HBox colorRow = new HBox(15);
        colorRow.setAlignment(Pos.CENTER_LEFT);
        ColorPicker colorPicker = new ColorPicker(Color.DODGERBLUE);
        colorPicker.setPrefWidth(120);
        Button applyColorAll = createStyledButton("Farbe anwenden", "#9b59b6");
        applyColorAll.setPrefHeight(35);
        applyColorAll.setOnAction(e -> {
            if (simController == null) {
                showNotification("⚠ SUMO nicht verbunden!");
                return;
            }
            Color c = colorPicker.getValue();
            java.awt.Color awt = new java.awt.Color(
                (int)(c.getRed() * 255), 
                (int)(c.getGreen() * 255), 
                (int)(c.getBlue() * 255)
            );
            int count = 0;
            for (SumoVehicle v : vehicles) {
                try {
                    simController.setVehicleColor(v.getId(), awt);
                    count++;
                } catch (Exception ex) {}
            }
            showNotification("✓ " + count + " Fahrzeuge gefärbt");
        });
        colorRow.getChildren().addAll(colorPicker, applyColorAll);
        colorSection.getChildren().addAll(colorInfo, colorRow);

        // === SPEED FILTER ===
        VBox speedSection = createDarkSection("⚡ Geschwindigkeits-Filter");
        
        HBox minSpeedRow = new HBox(15);
        minSpeedRow.setAlignment(Pos.CENTER_LEFT);
        Label minLbl = createDarkLabel("Min Speed:");
        minLbl.setMinWidth(100);
        TextField minSpeedField = new TextField();
        minSpeedField.setPromptText("leer = kein Minimum");
        minSpeedField.setPrefWidth(140);
        styleTextField(minSpeedField);
        if (vehicleFilter.getMinSpeed() != null) {
            minSpeedField.setText(String.valueOf(vehicleFilter.getMinSpeed().intValue()));
        }
        minSpeedRow.getChildren().addAll(minLbl, minSpeedField, createDarkLabel("m/s"));

        HBox maxSpeedRow = new HBox(15);
        maxSpeedRow.setAlignment(Pos.CENTER_LEFT);
        Label maxLbl = createDarkLabel("Max Speed:");
        maxLbl.setMinWidth(100);
        TextField maxSpeedField = new TextField();
        maxSpeedField.setPromptText("leer = kein Maximum");
        maxSpeedField.setPrefWidth(140);
        styleTextField(maxSpeedField);
        if (vehicleFilter.getMaxSpeed() != null) {
            maxSpeedField.setText(String.valueOf(vehicleFilter.getMaxSpeed().intValue()));
        }
        maxSpeedRow.getChildren().addAll(maxLbl, maxSpeedField, createDarkLabel("m/s"));

        speedSection.getChildren().addAll(minSpeedRow, maxSpeedRow);

        // === EDGE FILTER ===
        VBox edgeSection = createDarkSection("🛤 Edge Filter");
        Label edgeInfo = createDarkLabel("Nur Fahrzeuge auf dieser Edge anzeigen:");
        edgeInfo.setStyle("-fx-font-size: 12;");
        
        ComboBox<String> edgeCombo = new ComboBox<>();
        edgeCombo.setPrefWidth(400);
        edgeCombo.setMaxWidth(400);
        edgeCombo.setStyle("-fx-background-color: " + DARKER_BG + "; -fx-font-size: 12;");
        edgeCombo.getItems().add("— Alle Edges (kein Filter) —");
        edgeCombo.setValue("— Alle Edges (kein Filter) —");
        
        // Sammle ALLE Edges wo aktuell Fahrzeuge sind
        Map<String, Long> edgeCounts = new HashMap<>();
        for (SumoVehicle v : vehicles) {
            try {
                String vEdge = v.getCurrentEdge();
                if (vEdge != null && !vEdge.isEmpty()) {
                    edgeCounts.merge(vEdge, 1L, Long::sum);
                }
            } catch (Exception ex) {}
        }
        
        // Zeige aktuelle Edge-Statistiken
        Label edgeStatsLabel = createDarkLabel("Fahrzeuge auf " + edgeCounts.size() + " Edges verteilt");
        edgeStatsLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #aaa;");
        
        // Füge NUR Edges mit Fahrzeugen hinzu (sortiert nach Anzahl)
        List<Map.Entry<String, Long>> sortedEdges = new ArrayList<>(edgeCounts.entrySet());
        sortedEdges.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        
        for (Map.Entry<String, Long> entry : sortedEdges) {
            edgeCombo.getItems().add(entry.getKey() + " (" + entry.getValue() + " Fzg)");
        }
        
        // Setze aktuellen Filter wenn vorhanden
        if (vehicleFilter.getEdge() != null) {
            for (String item : edgeCombo.getItems()) {
                if (item.startsWith(vehicleFilter.getEdge())) {
                    edgeCombo.setValue(item);
                    break;
                }
            }
        }
        
        Button refreshEdgesBtn = createStyledButton("🔄 Aktualisieren", "#3498db");
        refreshEdgesBtn.setOnAction(e -> {
            edgeCombo.getItems().clear();
            edgeCombo.getItems().add("— Alle Edges (kein Filter) —");
            
            Map<String, Long> counts = new HashMap<>();
            for (SumoVehicle v : vehicles) {
                try {
                    String vEdge = v.getCurrentEdge();
                    if (vEdge != null && !vEdge.isEmpty()) {
                        counts.merge(vEdge, 1L, Long::sum);
                    }
                } catch (Exception ex) {}
            }
            
            List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            
            for (Map.Entry<String, Long> entry : sorted) {
                edgeCombo.getItems().add(entry.getKey() + " (" + entry.getValue() + " Fzg)");
            }
            
            edgeStatsLabel.setText("Fahrzeuge auf " + counts.size() + " Edges verteilt");
            edgeCombo.setValue("— Alle Edges (kein Filter) —");
            showNotification("✓ Edge-Liste aktualisiert");
        });
        
        edgeSection.getChildren().addAll(edgeInfo, edgeCombo, edgeStatsLabel, refreshEdgesBtn);

        // === BUTTONS ===
        Button applyFilterBtn = createStyledButton("✓ Filter anwenden", "#27ae60");
        applyFilterBtn.setPrefWidth(180);
        applyFilterBtn.setPrefHeight(45);
        applyFilterBtn.setFont(Font.font("System", FontWeight.BOLD, 14));
        applyFilterBtn.setOnAction(e -> {
            vehicleFilter.setEnabled(enableCheck.isSelected());

            // Min Speed
            String minText = minSpeedField.getText().trim();
            if (minText.isEmpty()) {
                vehicleFilter.setMinSpeed(null);
            } else {
                try {
                    vehicleFilter.setMinSpeed(Double.parseDouble(minText));
                } catch (NumberFormatException ex) {
                    vehicleFilter.setMinSpeed(null);
                }
            }

            // Max Speed
            String maxText = maxSpeedField.getText().trim();
            if (maxText.isEmpty()) {
                vehicleFilter.setMaxSpeed(null);
            } else {
                try {
                    vehicleFilter.setMaxSpeed(Double.parseDouble(maxText));
                } catch (NumberFormatException ex) {
                    vehicleFilter.setMaxSpeed(null);
                }
            }

            // Edge - extrahiere echte Edge-ID aus dem ComboBox-Text
            String edgeSelection = edgeCombo.getValue();
            if (edgeSelection == null || edgeSelection.startsWith("—")) {
                vehicleFilter.setEdge(null);
                System.out.println("[FILTER] Edge: keine (alle anzeigen)");
            } else {
                // Entferne " (X Fzg)" falls vorhanden
                String edgeId = edgeSelection.split(" \\(")[0].trim();
                vehicleFilter.setEdge(edgeId);
                System.out.println("[FILTER] Edge gesetzt auf: '" + edgeId + "'");
                
                // Debug: Zeige welche Fahrzeuge auf dieser Edge sind
                int countOnEdge = 0;
                for (SumoVehicle v : vehicles) {
                    try {
                        String vEdge = v.getCurrentEdge();
                        if (vEdge != null && vEdge.equals(edgeId)) {
                            countOnEdge++;
                        }
                    } catch (Exception ex) {}
                }
                System.out.println("[FILTER] " + countOnEdge + " Fahrzeuge auf Edge '" + edgeId + "'");
            }

            vehicleFilter.setColor(null);

            String status = vehicleFilter.isEnabled() ? "aktiviert" : "deaktiviert";
            showNotification("✓ Filter " + status);
            renderMap();
        });

        Button resetBtn = createStyledButton("↺ Reset", "#e74c3c");
        resetBtn.setPrefHeight(45);
        resetBtn.setPrefWidth(100);
        resetBtn.setOnAction(e -> {
            vehicleFilter.setEnabled(false);
            vehicleFilter.setMinSpeed(null);
            vehicleFilter.setMaxSpeed(null);
            vehicleFilter.setEdge(null);
            vehicleFilter.setColor(null);
            
            enableCheck.setSelected(false);
            minSpeedField.clear();
            maxSpeedField.clear();
            edgeCombo.setValue("— Alle Edges (kein Filter) —");
            
            showNotification("✓ Filter zurückgesetzt");
            renderMap();
        });

        HBox buttonRow = new HBox(25, applyFilterBtn, resetBtn);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.setPadding(new Insets(15, 0, 0, 0));

        root.getChildren().addAll(
            title, 
            enableCheck, 
            new Separator(),
            colorSection,
            new Separator(), 
            speedSection, 
            edgeSection, 
            new Separator(),
            buttonRow
        );
        
        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: " + DARK_BG + "; -fx-background-color: " + DARK_BG + ";");
        
        window.setScene(new Scene(scrollPane));
        window.show();
    }

    // ============== TRAFFIC LIGHT INSPECTOR ==============
    private void showTrafficLightInspector() {
        if (selectedTrafficLight == null) {
            showNotification("⚠ Bitte erst Ampel auswählen!");
            return;
        }
        showTrafficLightInspector(selectedTrafficLight);
    }

    private void showTrafficLightInspector(String tlsId) {
        if (simController == null) return;

        Stage window = createDarkWindow("🚦 TLS Inspector - " + tlsId, 550, 700);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + DARK_BG + ";");

        // Header
        Label header = new Label("🚦 " + tlsId);
        header.setFont(Font.font("System", FontWeight.BOLD, 22));
        header.setTextFill(Color.web("#ffcc00"));

        // Current State
        VBox stateSection = createDarkSection("📊 Current State");
        Label currentPhaseLbl = createDarkLabel("Phase: —");
        currentPhaseLbl.setFont(Font.font("System", FontWeight.BOLD, 14));
        Label currentStateLbl = createDarkLabel("State: —");
        currentStateLbl.setFont(Font.font("Monospace", 14));

        // State visualization
        HBox stateVisual = new HBox(3);
        stateVisual.setAlignment(Pos.CENTER_LEFT);
        stateVisual.setPadding(new Insets(10, 0, 10, 0));

        stateSection.getChildren().addAll(currentPhaseLbl, currentStateLbl, stateVisual);

        // Controlled Lanes
        VBox lanesSection = createDarkSection("🛤 Controlled Lanes");
        ListView<String> lanesListView = new ListView<>();
        lanesListView.setPrefHeight(80);
        lanesListView.setStyle("-fx-background-color: " + DARKER_BG + "; -fx-control-inner-background: " + DARKER_BG + ";");
        try {
            lanesListView.getItems().addAll(simController.getControlledLanes(tlsId));
        } catch (Exception e) {}
        lanesSection.getChildren().add(lanesListView);

        // Phase Control Buttons
        VBox controlSection = createDarkSection("🎮 Phase Control");
        HBox phaseButtons = new HBox(15);
        phaseButtons.setAlignment(Pos.CENTER);
        
        Button prevBtn = createStyledButton("◀ Phase -", "#e74c3c");
        Button nextBtn = createStyledButton("Phase + ▶", "#27ae60");

        TlsPhaseCollector collector = tlsCollectors.computeIfAbsent(tlsId, id -> {
            TlsPhaseCollector c = new TlsPhaseCollector(simController, id);
            c.initialize(netXmlReader.getDurations(id), netXmlReader.getStates(id));
            return c;
        });

        prevBtn.setOnAction(e -> {
            try {
                int current = simController.getTlsPhase(tlsId);
                simController.setTlsPhase(tlsId, Math.max(0, current - 1));
            } catch (Exception ex) { showNotification("✗ " + ex.getMessage()); }
        });

        nextBtn.setOnAction(e -> {
            try {
                int current = simController.getTlsPhase(tlsId);
                int max = collector.getPhases().size() - 1;
                simController.setTlsPhase(tlsId, Math.min(max, current + 1));
            } catch (Exception ex) { showNotification("✗ " + ex.getMessage()); }
        });

        phaseButtons.getChildren().addAll(prevBtn, nextBtn);
        controlSection.getChildren().add(phaseButtons);

        // Phase Duration Editor
        VBox durationSection = createDarkSection("⏱ Phase Durations");
        VBox phaseRows = new VBox(8);

        for (PhaseEntry phase : collector.getPhases()) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            Label phaseLbl = createDarkLabel("Phase " + phase.index + ":");
            phaseLbl.setMinWidth(70);

            // State preview circles
            HBox preview = new HBox(2);
            for (int i = 0; i < Math.min(phase.state.length(), 8); i++) {
                Circle c = new Circle(6);
                c.setFill(getSignalColor(phase.state.charAt(i)));
                c.setStroke(Color.WHITE);
                c.setStrokeWidth(0.5);
                preview.getChildren().add(c);
            }
            if (phase.state.length() > 8) {
                preview.getChildren().add(createDarkLabel("+" + (phase.state.length() - 8)));
            }
            preview.setMinWidth(100);

            TextField durField = new TextField(String.valueOf((int) phase.duration));
            durField.setPrefWidth(60);
            styleTextField(durField);

            Label secLbl = createDarkLabel("sec");

            Button setBtn = createStyledButton("Set", "#3498db");
            final int idx = phase.index;
            setBtn.setOnAction(e -> {
                try {
                    double dur = Double.parseDouble(durField.getText());
                    collector.setPhaseDuration(idx, dur);
                    // Apply if current phase
                    if (simController.getTlsPhase(tlsId) == idx) {
                        simController.setTlsPhaseDuration(tlsId, dur);
                    }
                    showNotification("✓ Phase " + idx + " → " + dur + "s");
                } catch (Exception ex) { showNotification("✗ Ungültig"); }
            });

            row.getChildren().addAll(phaseLbl, preview, durField, secLbl, setBtn);
            phaseRows.getChildren().add(row);
        }

        ScrollPane phaseScroll = new ScrollPane(phaseRows);
        phaseScroll.setFitToWidth(true);
        phaseScroll.setPrefHeight(180);
        phaseScroll.setStyle("-fx-background: " + DARKER_BG + "; -fx-background-color: " + DARKER_BG + ";");
        durationSection.getChildren().add(phaseScroll);

        root.getChildren().addAll(header, stateSection, lanesSection, controlSection, durationSection);

        // Update timer
        Timeline timer = new Timeline(new KeyFrame(Duration.millis(200), e -> {
            try {
                int phase = simController.getTlsPhase(tlsId);
                String state = simController.getRedYellowGreenState(tlsId);

                currentPhaseLbl.setText("Phase: " + phase + " / " + (collector.getPhases().size() - 1));
                currentStateLbl.setText("State: " + state);

                stateVisual.getChildren().clear();
                for (int i = 0; i < state.length(); i++) {
                    Circle c = new Circle(10);
                    c.setFill(getSignalColor(state.charAt(i)));
                    c.setStroke(Color.WHITE);
                    c.setStrokeWidth(1);
                    stateVisual.getChildren().add(c);
                }
            } catch (Exception ex) {}
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();

        window.setOnCloseRequest(e -> timer.stop());
        window.setScene(new Scene(root));
        window.show();
    }

    private Color getSignalColor(char c) {
        return switch (Character.toLowerCase(c)) {
            case 'g' -> Color.LIME;
            case 'y' -> Color.YELLOW;
            case 'r' -> Color.RED;
            case 'o' -> Color.ORANGE;
            default -> Color.GRAY;
        };
    }

    // ============== INJECT VEHICLE DIALOG ==============
    private void showInjectVehicleDialog() {
        if (simController == null) { showNotification("⚠ SUMO nicht verbunden!"); return; }

        Stage window = createDarkWindow("🚗 Inject Vehicle", 420, 420);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + DARK_BG + ";");

        Label title = createTitleLabel("🚗 Vehicle Injection");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);

        TextField nameField = new TextField("veh_" + System.currentTimeMillis());
        styleTextField(nameField);

        Spinner<Double> speedSpinner = new Spinner<>(1.0, 50.0, 13.89, 1.0);
        speedSpinner.setEditable(true);
        speedSpinner.setPrefWidth(120);

        Spinner<Integer> countSpinner = new Spinner<>(1, 100, 1, 1);
        countSpinner.setEditable(true);
        countSpinner.setPrefWidth(120);

        ColorPicker colorPicker = new ColorPicker(Color.rgb((int)(Math.random()*255), (int)(Math.random()*255), (int)(Math.random()*255)));

        ComboBox<String> routeCombo = new ComboBox<>();
        routeCombo.setPrefWidth(200);
        try {
            List<String> routes = simController.getAllRoutes();
            routeCombo.getItems().addAll(routes);
            if (!routes.isEmpty()) routeCombo.setValue(routes.get(0));
        } catch (Exception e) {}

        ComboBox<String> edgeCombo = new ComboBox<>();
        edgeCombo.setPrefWidth(200);
        edgeCombo.getItems().add("— Auto —");
        edgeCombo.setValue("— Auto —");
        try { edgeCombo.getItems().addAll(simController.getAllEdges()); } catch (Exception e) {}

        grid.add(createDarkLabel("Name/ID:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(createDarkLabel("Speed (m/s):"), 0, 1);
        grid.add(speedSpinner, 1, 1);
        grid.add(createDarkLabel("Anzahl:"), 0, 2);
        grid.add(countSpinner, 1, 2);
        grid.add(createDarkLabel("Farbe:"), 0, 3);
        grid.add(colorPicker, 1, 3);
        grid.add(createDarkLabel("Route:"), 0, 4);
        grid.add(routeCombo, 1, 4);
        grid.add(createDarkLabel("Edge:"), 0, 5);
        grid.add(edgeCombo, 1, 5);

        Button injectBtn = createStyledButton("🚗 Inject", "#27ae60");
        injectBtn.setPrefWidth(150);
        injectBtn.setOnAction(e -> {
            injectVehicles(nameField.getText(), speedSpinner.getValue(), countSpinner.getValue(), colorPicker.getValue(), routeCombo.getValue());
            window.close();
        });

        Button cancelBtn = createStyledButton("Abbrechen", "#7f8c8d");
        cancelBtn.setOnAction(e -> window.close());

        HBox buttons = new HBox(15, injectBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER);

        root.getChildren().addAll(title, grid, buttons);
        window.setScene(new Scene(root));
        window.showAndWait();
    }

    private void injectVehicles(String baseName, double speed, int count, Color fxColor, String routeId) {
        new Thread(() -> {
            java.awt.Color awt = new java.awt.Color((int)(fxColor.getRed()*255), (int)(fxColor.getGreen()*255), (int)(fxColor.getBlue()*255));
            int success = 0;
            for (int i = 0; i < count; i++) {
                String id = count > 1 ? baseName + "_" + i : baseName;
                if (simController.vehicleExists(id)) id += "_" + System.currentTimeMillis();
                try {
                    simController.injectVehicle(id, routeId, speed, awt);
                    success++;
                    if (count > 1) Thread.sleep(100);
                } catch (Exception ex) {}
            }
            final int s = success;
            Platform.runLater(() -> showNotification("✓ " + s + "/" + count + " gespawnt"));
        }).start();
    }

    // ============== CONFIG DIALOG ==============
    private void showConfigDialog() {
        Stage window = createDarkWindow("⚙ Konfiguration", 500, 200);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + DARK_BG + ";");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField cfgField = new TextField(sumoCfgPath);
        TextField netField = new TextField(netFilePath);
        cfgField.setPrefWidth(300);
        netField.setPrefWidth(300);
        styleTextField(cfgField);
        styleTextField(netField);

        Button browseCfg = createStyledButton("...", "#3498db");
        browseCfg.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SUMO Config", "*.sumocfg"));
            File f = fc.showOpenDialog(primaryStage);
            if (f != null) cfgField.setText(f.getAbsolutePath());
        });

        Button browseNet = createStyledButton("...", "#3498db");
        browseNet.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SUMO Network", "*.net.xml"));
            File f = fc.showOpenDialog(primaryStage);
            if (f != null) netField.setText(f.getAbsolutePath());
        });

        grid.add(createDarkLabel("SUMO Config:"), 0, 0);
        grid.add(cfgField, 1, 0);
        grid.add(browseCfg, 2, 0);
        grid.add(createDarkLabel("Network:"), 0, 1);
        grid.add(netField, 1, 1);
        grid.add(browseNet, 2, 1);

        Button loadBtn = createStyledButton("Laden", "#27ae60");
        loadBtn.setOnAction(e -> {
            sumoCfgPath = cfgField.getText();
            netFilePath = netField.getText();
            if (simController != null) simController.close();
            lanes.clear();
            vehicles.clear();
            trafficLightBars.clear();
            tlsCollectors.clear();
            minX = Double.MAX_VALUE; maxX = Double.MIN_VALUE;
            minY = Double.MAX_VALUE; maxY = Double.MIN_VALUE;
            initializeSumo();
            window.close();
        });

        HBox buttons = new HBox(15, loadBtn);
        buttons.setAlignment(Pos.CENTER);

        root.getChildren().addAll(grid, buttons);
        window.setScene(new Scene(root));
        window.showAndWait();
    }

    // ============== ACTION HANDLERS ==============
    private void onPlay() {
        if (simController != null && !simulationRunning) {
            startSimulationLoop();
            showNotification("▶ Gestartet");
        }
    }

    private void onPause() {
        if (simulationLoop != null) {
            simulationLoop.stop();
            simulationRunning = false;
            showNotification("⏸ Pausiert");
        }
    }

    private void onStop() {
        if (simulationLoop != null) { simulationLoop.stop(); simulationRunning = false; }
        if (simController != null) { simController.close(); simController = null; }
        vehicles.clear();
        vehicleTracking.clear();
        seenVehicles.clear();
        renderMap();
        showNotification("⏹ Gestoppt");
    }

    private void onSelectTrafficLight() {
        if (simController == null) { showNotification("⚠ SUMO nicht verbunden!"); return; }
        try {
            List<String> ids = simController.getTrafficLightIds();
            if (ids.isEmpty()) { showNotification("⚠ Keine Ampeln!"); return; }
            ChoiceDialog<String> dialog = new ChoiceDialog<>(ids.get(0), ids);
            dialog.setTitle("Ampel wählen");
            dialog.showAndWait().ifPresent(id -> {
                selectedTrafficLight = id;
                if (selectedTlLabel != null) selectedTlLabel.setText("Selected: " + id);
                showNotification("🚦 " + id);
            });
        } catch (Exception e) { showNotification("✗ " + e.getMessage()); }
    }

    private void onSetGreen() { setPhase(0, "GREEN"); }
    private void onSetYellow() { setPhase(1, "YELLOW"); }
    private void onSetRed() { setPhase(2, "RED"); }

    private void setPhase(int phase, String name) {
        if (selectedTrafficLight == null) { showNotification("⚠ Keine Ampel!"); return; }
        try {
            simController.setTlsPhase(selectedTrafficLight, phase);
            showNotification("🚦 → " + name);
        } catch (Exception e) { showNotification("✗ " + e.getMessage()); }
    }

    private void onSelectRoute() {
        if (simController == null) { showNotification("⚠ SUMO nicht verbunden!"); return; }
        try {
            List<String> routes = simController.getAllRoutes();
            if (routes.isEmpty()) { showNotification("⚠ Keine Routen!"); return; }
            ChoiceDialog<String> dialog = new ChoiceDialog<>(routes.get(0), routes);
            dialog.setTitle("Route wählen");
            dialog.showAndWait().ifPresent(id -> {
                selectedRoute = id;
                showNotification("📍 " + id);
            });
        } catch (Exception e) { showNotification("✗ " + e.getMessage()); }
    }

    private void onSelectEdge() {
        if (simController == null) { showNotification("⚠ SUMO nicht verbunden!"); return; }
        try {
            List<String> edges = simController.getAllEdges();
            if (edges.isEmpty()) { showNotification("⚠ Keine Edges!"); return; }
            ChoiceDialog<String> dialog = new ChoiceDialog<>(edges.get(0), edges);
            dialog.setTitle("Edge wählen");
            dialog.showAndWait().ifPresent(id -> {
                selectedEdge = id;
                if (selectedEdgeLabel != null) selectedEdgeLabel.setText("Selected: " + id);
                showNotification("🛤 " + id);
                renderMap();
            });
        } catch (Exception e) { showNotification("✗ " + e.getMessage()); }
    }

    private void onShowAverageSpeed() {
        if (simController == null) return;
        var result = analyticsService.compute(simController.getDataGathering().getStore().snapshot());
        showNotification(String.format("📈 Ø %.1f m/s | %d Fahrzeuge", result.globalAvgSpeed, result.vehiclesEver));
    }

    private void onShowVehicleDensity() {
        if (simController == null) return;
        var result = analyticsService.compute(simController.getDataGathering().getStore().snapshot());
        showNotification("📊 Aktuell: " + vehicles.size() + " | Gesamt: " + result.vehiclesEver);
    }

    private void onShowCongestion() {
        if (simController == null) return;
        var result = analyticsService.compute(simController.getDataGathering().getStore().snapshot());
        if (result.congestedEdges.isEmpty()) {
            showNotification("🔥 Keine Staus");
        } else {
            showNotification("🔥 Stau: " + result.congestedEdges.stream().limit(3).collect(Collectors.joining(", ")));
        }
    }

    private void onShowTravelTime() {
        if (simController == null) return;
        var result = analyticsService.compute(simController.getDataGathering().getStore().snapshot());
        String slowest = result.perEdge.entrySet().stream()
            .filter(e -> e.getValue().vehicleCount > 0)
            .min((a, b) -> Double.compare(a.getValue().avgSpeed(), b.getValue().avgSpeed()))
            .map(e -> e.getKey() + " (" + String.format("%.1f", e.getValue().avgSpeed()) + " m/s)")
            .orElse("—");
        showNotification("⏱ Langsamste: " + slowest);
    }

    private void onExportReports() {
        if (simController == null) return;
        String name = "report_" + System.currentTimeMillis();
        simController.exportReports(name, null, vehiclesEver);
        showNotification("💾 " + name);
    }

    // ============== UI HELPERS ==============
    private Stage createDarkWindow(String title, int width, int height) {
        Stage window = new Stage();
        window.initModality(Modality.NONE);
        window.initOwner(primaryStage);
        window.setTitle(title);
        window.setWidth(width);
        window.setHeight(height);
        return window;
    }

    private VBox createDarkSection(String title) {
        VBox section = new VBox(8);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: " + DARKER_BG + "; -fx-background-radius: 8;");
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 13));
        lbl.setTextFill(Color.web(CYAN_ACCENT));
        section.getChildren().add(lbl);
        return section;
    }

    private Label createTitleLabel(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 18));
        lbl.setTextFill(Color.WHITE);
        return lbl;
    }

    private Label createDarkLabel(String text) {
        Label lbl = new Label(text);
        lbl.setTextFill(Color.web(LIGHT_TEXT));
        return lbl;
    }

    private void styleTextField(TextField field) {
        field.setStyle("-fx-background-color: " + DARKER_BG + "; -fx-text-fill: white; -fx-border-color: " + HIGHLIGHT + "; -fx-border-radius: 3;");
    }

    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
        btn.setCursor(Cursor.HAND);
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: derive(" + color + ", 20%); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;"));
        return btn;
    }

    private Button createControlButton(String text, String color, Runnable action) {
        Button btn = new Button(text);
        btn.setFont(Font.font("System", FontWeight.BOLD, 12));
        btn.setTextFill(Color.WHITE);
        btn.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3;");
        btn.setCursor(Cursor.HAND);
        btn.setPrefSize(30, 20);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    // ============== SIDEBAR HELPERS ==============
    private VBox createExpandableMenu(String title, String iconPath, Node... children) {
        VBox container = new VBox();
        VBox.setMargin(container, new Insets(0, 0, 5, 0));

        Button header = new Button();
        header.setPrefWidth(250);
        header.setPrefHeight(42);
        header.setAlignment(Pos.CENTER);
        header.setCursor(Cursor.HAND);
        header.setStyle("-fx-background-color: transparent;");
        header.setGraphic(createImageView(iconPath, 26, 26));
        header.setTooltip(new Tooltip(title));

        VBox content = new VBox(3);
        content.setPadding(new Insets(8, 5, 10, 20));
        content.setVisible(false);
        content.setManaged(false);
        content.setStyle("-fx-background-color: rgba(255,255,255,0.05);");
        for (Node child : children) content.getChildren().add(child);

        header.setOnAction(e -> { content.setVisible(!content.isVisible()); content.setManaged(!content.isManaged()); });
        header.setOnMouseEntered(e -> header.setStyle("-fx-background-color: " + HIGHLIGHT + "; -fx-border-color: white; -fx-border-width: 0 0 0 3;"));
        header.setOnMouseExited(e -> header.setStyle("-fx-background-color: transparent;"));

        container.getChildren().addAll(header, content);
        return container;
    }

    private VBox createExpandableSubMenu(String title, Node... children) {
        VBox container = new VBox();
        container.setStyle("-fx-background-color: rgba(0,0,0,0.2); -fx-background-radius: 6;");
        VBox.setMargin(container, new Insets(5, 0, 5, 0));

        Button header = new Button(title);
        header.setPrefWidth(180);
        header.setPrefHeight(36);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 8, 0, 12));
        header.setFont(Font.font("System", FontWeight.BOLD, 12));
        header.setTextFill(Color.web("#aaddff"));
        header.setCursor(Cursor.HAND);
        header.setStyle("-fx-background-color: transparent;");

        Label arrow = new Label("▶");
        arrow.setTextFill(Color.web("#88ccff"));
        header.setGraphic(arrow);
        header.setContentDisplay(ContentDisplay.RIGHT);

        VBox content = new VBox(3);
        content.setPadding(new Insets(5, 5, 8, 15));
        content.setVisible(false);
        content.setManaged(false);
        for (Node child : children) content.getChildren().add(child);

        header.setOnAction(e -> {
            content.setVisible(!content.isVisible());
            content.setManaged(!content.isManaged());
            arrow.setText(content.isVisible() ? "▼" : "▶");
        });

        container.getChildren().addAll(header, content);
        return container;
    }

    private Button createSubButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setPrefWidth(200);
        btn.setPrefHeight(36);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(0, 8, 0, 12));
        btn.setFont(Font.font("System", FontWeight.BOLD, 12));
        btn.setTextFill(Color.web("#ccddee"));
        btn.setCursor(Cursor.HAND);
        btn.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 6;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: rgba(100,180,255,0.2); -fx-background-radius: 6;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 6;"));
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private Button createPhaseButton(String text, String color, Runnable action) {
        Button btn = new Button(text);
        btn.setPrefWidth(200);
        btn.setPrefHeight(40);
        btn.setAlignment(Pos.CENTER);
        btn.setFont(Font.font("System", FontWeight.BOLD, 13));
        btn.setTextFill(Color.WHITE);
        btn.setCursor(Cursor.HAND);
        btn.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 6;");
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private Label createInfoLabel(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 11));
        lbl.setTextFill(Color.web("#aaddff"));
        return lbl;
    }

    private Label createSubLabel(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 11));
        lbl.setTextFill(Color.web("#88aacc"));
        lbl.setPadding(new Insets(8, 0, 5, 0));
        return lbl;
    }

    private Region createSeparator() {
        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color: rgba(255,255,255,0.1);");
        VBox.setMargin(sep, new Insets(5, 0, 5, 0));
        return sep;
    }

    // ============== NOTIFICATION ==============
    private void showNotification(String msg) {
        System.out.println("[SUMO] " + msg);
        Platform.runLater(() -> {
            Label n = new Label(msg);
            n.setStyle("-fx-background-color: rgba(0,0,0,0.9); -fx-padding: 12 24; -fx-background-radius: 6; -fx-font-weight: bold;");
            n.setTextFill(Color.WHITE);
            n.setLayoutX(10);
            n.setLayoutY(10);
            sumoMapPane.getChildren().add(n);
            new Thread(() -> {
                try { Thread.sleep(3000); } catch (Exception e) {}
                Platform.runLater(() -> sumoMapPane.getChildren().remove(n));
            }).start();
        });
    }

    // ============== OTHER ==============
    private void initializeSliderAnimation() {
        slider.setTranslateX(-260);
        menuLabel.setOnMouseClicked(e -> {
            TranslateTransition t = new TranslateTransition(Duration.seconds(0.4), slider);
            t.setToX(0);
            t.play();
            slider.setTranslateX(-260);
            t.setOnFinished(ev -> { menuLabel.setVisible(false); menuCloseLabel.setVisible(true); });
        });
        menuCloseLabel.setOnMouseClicked(e -> {
            TranslateTransition t = new TranslateTransition(Duration.seconds(0.4), slider);
            t.setToX(-260);
            t.play();
            slider.setTranslateX(0);
            t.setOnFinished(ev -> { menuLabel.setVisible(true); menuCloseLabel.setVisible(false); });
        });
    }

    private Pane createSumoMapPane() {
        sumoMapPane = new Pane();
        sumoMapPane.setStyle("-fx-background-color: " + DARK_BG + ";");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(sumoMapPane.widthProperty());
        clip.heightProperty().bind(sumoMapPane.heightProperty());
        sumoMapPane.setClip(clip);
        mapContent = new Pane();
        mapCanvas = new Canvas(800, 600);
        mapContent.getChildren().add(mapCanvas);
        sumoMapPane.getChildren().add(mapContent);
        return sumoMapPane;
    }

    private Label createMenuLabel(String text) {
        Label l = new Label(text);
        l.setPrefSize(100, 22);
        l.setTextFill(Color.web("#555555"));
        l.setFont(Font.font("System", FontWeight.BOLD, 14));
        l.setCursor(Cursor.HAND);
        return l;
    }

    private ImageView createImageView(String path, double w, double h) {
        ImageView iv = new ImageView();
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setPreserveRatio(true);
        try {
            Image img = new Image(getClass().getResourceAsStream(path));
            if (img != null && !img.isError()) iv.setImage(img);
        } catch (Exception e) {}
        return iv;
    }

    private void setAnchors(javafx.scene.Node n, Double t, Double r, Double b, Double l) {
        if (t != null) AnchorPane.setTopAnchor(n, t);
        if (r != null) AnchorPane.setRightAnchor(n, r);
        if (b != null) AnchorPane.setBottomAnchor(n, b);
        if (l != null) AnchorPane.setLeftAnchor(n, l);
    }

    public static void main(String[] args) { launch(args); }
}
