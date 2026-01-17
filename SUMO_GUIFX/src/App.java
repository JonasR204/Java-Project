/*import javafx.animation.AnimationTimer;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
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
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.geom.Point2D;
import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class App extends Application {

    // ============== ICON PATHS - HIER EIGENE ICONS EINTRAGEN ==============
    private static final String ICON_VEHICLES = "Images/Multiple_Cars.png";
    private static final String ICON_TRAFFIC_LIGHTS = "Images/Traffic_Light.png";
    private static final String ICON_ROADS = "Images/Road.png";
    private static final String ICON_CHARTS = "Images/Chart.png";
    private static final String ICON_MENU = "Images/menu1.png";
    // ======================================================================

    // === SUMO Configuration ===
    private static final String SUMO_BIN = "sumo-gui"; // oder vollständiger Pfad
    private String sumoCfgPath = null;
    private String netFilePath = null;

    private double x, y = 0;
    private AnchorPane slider;
    private Label menuLabel;
    private Label menuCloseLabel;
    private Slider zoomSlider;
    private Pane sumoMapPane;
    private Pane mapContent;
    private Canvas mapCanvas;

    // === SUMO Components ===
    private SimulationController simController;
    private List<LaneShape> lanes = new ArrayList<>();
    private List<SumoVehicle> vehicles = new CopyOnWriteArrayList<>();
    private List<TrafficLightBar> trafficLightBars = new CopyOnWriteArrayList<>();
    private NetXmlReader netXmlReader;
    private Map<String, TlsPhaseCollector> tlsCollectors = new HashMap<>();

    // Aktuelle Auswahl
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

    // Info Labels (für Updates)
    private Label vehicleIdLabel, vehicleSpeedLabel, vehicleColorLabel, vehicleLocationLabel;
    private Label selectedTlLabel, selectedEdgeLabel;

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        
        AnchorPane root = new AnchorPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

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

        // Vollbildmodus
        primaryStage.setMaximized(true);
        primaryStage.setTitle("SUMO Traffic Simulation GUI");

        root.setOnMousePressed(event -> {
            x = event.getSceneX();
            y = event.getSceneY();
        });

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Canvas-Größe an Fenster anpassen
        mapCanvas.widthProperty().bind(sumoMapPane.widthProperty());
        mapCanvas.heightProperty().bind(sumoMapPane.heightProperty());

        // Initial-Dialog für Konfiguration
        Platform.runLater(this::showConfigDialog);
    }

    private void showConfigDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("SUMO Konfiguration");
        dialog.setHeaderText("Bitte wählen Sie die SUMO-Dateien aus");

        ButtonType loadButton = new ButtonType("Laden", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Ohne SUMO starten", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(loadButton, cancelButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField cfgField = new TextField();
        cfgField.setPromptText("Pfad zur .sumocfg Datei");
        cfgField.setPrefWidth(300);

        TextField netField = new TextField();
        netField.setPromptText("Pfad zur .net.xml Datei");
        netField.setPrefWidth(300);

        Button browseCfg = new Button("...");
        browseCfg.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("SUMO Config wählen");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SUMO Config", "*.sumocfg"));
            File file = fc.showOpenDialog(primaryStage);
            if (file != null) cfgField.setText(file.getAbsolutePath());
        });

        Button browseNet = new Button("...");
        browseNet.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Net-Datei wählen");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SUMO Network", "*.net.xml"));
            File file = fc.showOpenDialog(primaryStage);
            if (file != null) netField.setText(file.getAbsolutePath());
        });

        grid.add(new Label("SUMO Config:"), 0, 0);
        grid.add(cfgField, 1, 0);
        grid.add(browseCfg, 2, 0);
        grid.add(new Label("Network File:"), 0, 1);
        grid.add(netField, 1, 1);
        grid.add(browseNet, 2, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(result -> {
            if (result == loadButton) {
                sumoCfgPath = cfgField.getText();
                netFilePath = netField.getText();
                initializeSumo();
            }
        });
    }

    private void initializeSumo() {
        try {
            // Lade Lanes aus net.xml
            if (netFilePath != null && !netFilePath.isEmpty()) {
                lanes = MapDataLoader.loadLanes(netFilePath);
                computeBounds();

                // Lade TLS-Daten
                netXmlReader = new NetXmlReader();
                netXmlReader.parse(netFilePath);

                showNotification("✓ Netzwerk geladen: " + lanes.size() + " Lanes");
            }

            // Starte SUMO
            if (sumoCfgPath != null && !sumoCfgPath.isEmpty()) {
                simController = new SimulationController(SUMO_BIN, sumoCfgPath);
                simController.start();

                // Initialisiere TLS Collectors
                for (String tlsId : simController.getTrafficLightIds()) {
                    TlsPhaseCollector collector = new TlsPhaseCollector(simController, tlsId);
                    collector.initialize(
                        netXmlReader.getDurations(tlsId),
                        netXmlReader.getStates(tlsId)
                    );
                    tlsCollectors.put(tlsId, collector);

                    // Traffic Light Bars erstellen
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                if (now - lastUpdate >= 50_000_000) { // ~20 FPS
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

            // Update vehicles
            vehicles.clear();
            for (String vehId : simController.getVehicleIds()) {
                SumoVehicle v = new SumoVehicle(vehId, simController);
                v.refresh();
                vehicles.add(v);
            }

            // Update traffic lights
            for (String tlsId : simController.getTrafficLightIds()) {
                String state = simController.getRedYellowGreenState(tlsId);
                for (TrafficLightBar bar : trafficLightBars) {
                    if (bar.tlsId.equals(tlsId) && bar.indexInState < state.length()) {
                        bar.setStateChar(state.charAt(bar.indexInState));
                    }
                }
            }

            // Update selected vehicle info
            updateSelectedVehicleInfo();

            Platform.runLater(this::renderMap);

        } catch (Exception e) {
            // Simulation might have ended
        }
    }

    private void updateSelectedVehicleInfo() {
        if (selectedVehicleId == null) return;

        try {
            for (SumoVehicle v : vehicles) {
                if (v.getId().equals(selectedVehicleId)) {
                    Platform.runLater(() -> {
                        if (vehicleIdLabel != null) vehicleIdLabel.setText(v.getId());
                        if (vehicleSpeedLabel != null) {
                            try {
                                vehicleSpeedLabel.setText(String.format("%.1f m/s", v.getSpeed()));
                            } catch (Exception ex) {}
                        }
                        if (vehicleColorLabel != null) {
                            java.awt.Color c = v.getColor();
                            vehicleColorLabel.setText(String.format("RGB(%d,%d,%d)", c.getRed(), c.getGreen(), c.getBlue()));
                        }
                        if (vehicleLocationLabel != null) {
                            vehicleLocationLabel.setText(String.format("%.1f, %.1f", v.getX(), v.getY()));
                        }
                    });
                    break;
                }
            }
        } catch (Exception e) {}
    }

    private void renderMap() {
        if (mapCanvas == null) return;

        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        double w = mapCanvas.getWidth();
        double h = mapCanvas.getHeight();

        if (w <= 0 || h <= 0) return;

        // Clear
        gc.setFill(Color.web("#1a1a2e"));
        gc.fillRect(0, 0, w, h);

        if (lanes.isEmpty()) {
            gc.setFill(Color.web("#4a4a6a"));
            gc.setFont(Font.font("System", FontWeight.BOLD, 24));
            gc.fillText("🗺 SUMO Simulation Map", w/2 - 150, h/2);
            gc.setFont(Font.font("System", FontWeight.BOLD, 14));
            gc.fillText("Konfiguration laden um Netzwerk anzuzeigen", w/2 - 160, h/2 + 30);
            return;
        }

        double margin = 30;
        double scaleX = (w - 2 * margin) / (maxX - minX);
        double scaleY = (h - 2 * margin) / (maxY - minY);
        double scale = Math.min(scaleX, scaleY);

        // Lanes zeichnen
        gc.setStroke(Color.DARKGRAY);
        gc.setLineWidth(2);

        for (LaneShape lane : lanes) {
            boolean highlight = lane.getId().equals(selectedEdge + "_0");
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

        // Traffic Light Bars zeichnen
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

        // Vehicles zeichnen
        for (SumoVehicle v : vehicles) {
            double vx = (v.getX() - minX) * scale + margin;
            double vy = h - ((v.getY() - minY) * scale + margin);

            java.awt.Color awtColor = v.getRenderColor();
            gc.setFill(Color.rgb(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue()));

            double r = 6;

            // Highlight selected vehicle
            if (v.getId().equals(selectedVehicleId)) {
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(2);
                gc.strokeOval(vx - r - 3, vy - r - 3, (r + 3) * 2, (r + 3) * 2);
            }

            gc.fillOval(vx - r, vy - r, r * 2, r * 2);
        }
    }

    private AnchorPane createTopPane() {
        AnchorPane topPane = new AnchorPane();
        topPane.setPrefHeight(65);
        topPane.setStyle("-fx-background-color: #ffffff;");

        Pane darkBar = new Pane();
        darkBar.setPrefHeight(25);
        darkBar.setStyle("-fx-background-color: #032d4d;");
        setAnchors(darkBar, 0.0, 0.0, null, 0.0);

        // Control Buttons
        HBox controlBox = new HBox(10);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        controlBox.setLayoutX(10);
        controlBox.setLayoutY(2);

        Button playBtn = createControlButton("▶", "#27ae60", this::onPlay);
        Button pauseBtn = createControlButton("⏸", "#f39c12", this::onPause);
        Button stopBtn = createControlButton("⏹", "#e74c3c", this::onStop);
        Button configBtn = createControlButton("⚙", "#3498db", this::showConfigDialog);

        controlBox.getChildren().addAll(playBtn, pauseBtn, stopBtn, configBtn);
        darkBar.getChildren().add(controlBox);

        // Exit Button
        Button exitBtn = createControlButton("✕", "#ff5555", () -> {
            if (simController != null) simController.close();
            Platform.exit();
        });
        exitBtn.setLayoutX(10);
        exitBtn.setLayoutY(2);
        AnchorPane.setRightAnchor(exitBtn, 10.0);
        AnchorPane.setTopAnchor(exitBtn, 2.0);
        darkBar.getChildren().add(exitBtn);

        // Zoom Controls
        HBox zoomBox = new HBox(10);
        zoomBox.setAlignment(Pos.CENTER);
        zoomBox.setPrefHeight(40);
        AnchorPane.setTopAnchor(zoomBox, 25.0);
        AnchorPane.setRightAnchor(zoomBox, 20.0);
        AnchorPane.setBottomAnchor(zoomBox, 0.0);

        Label zoomLabel = new Label("🔍 Zoom:");
        zoomLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        zoomLabel.setTextFill(Color.web("#333333"));

        zoomSlider = new Slider(0.5, 3.0, 1.0);
        zoomSlider.setPrefWidth(200);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setMajorTickUnit(0.5);
        zoomSlider.setCursor(Cursor.HAND);

        Label zoomValueLabel = new Label("100%");
        zoomValueLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        zoomValueLabel.setPrefWidth(50);

        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int percentage = (int) (newVal.doubleValue() * 100);
            zoomValueLabel.setText(percentage + "%");
            if (mapContent != null) {
                mapContent.setScaleX(newVal.doubleValue());
                mapContent.setScaleY(newVal.doubleValue());
            }
        });

        zoomBox.getChildren().addAll(zoomLabel, zoomSlider, zoomValueLabel);

        menuLabel = createMenuLabel("☰ MENU", ICON_MENU);
        AnchorPane.setLeftAnchor(menuLabel, 14.0);
        AnchorPane.setTopAnchor(menuLabel, 36.0);

        menuCloseLabel = createMenuLabel("✕ CLOSE", ICON_MENU);
        AnchorPane.setLeftAnchor(menuCloseLabel, 14.0);
        AnchorPane.setTopAnchor(menuCloseLabel, 36.0);
        menuCloseLabel.setVisible(false);

        topPane.getChildren().addAll(darkBar, zoomBox, menuLabel, menuCloseLabel);

        return topPane;
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

    private AnchorPane createSlider() {
        AnchorPane sliderPane = new AnchorPane();
        sliderPane.setPrefSize(260, 475);
        sliderPane.setStyle("-fx-background-color: linear-gradient(to bottom, #0A4969, #063450);");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        setAnchors(scrollPane, 20.0, 0.0, 10.0, 0.0);

        VBox sidebarBox = new VBox(5);
        sidebarBox.setPadding(new Insets(10));
        sidebarBox.setStyle("-fx-background-color: transparent;");

        // === VEHICLE MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("🚗  Vehicles", ICON_VEHICLES,
            createSubButton("➕ Spawn Vehicle", this::onSpawnVehicle),
            createSubButton("⚡ Stress Test (100)", this::onStressTest),
            createSubButton("🎯 Select Vehicle", this::onSelectVehicle),
            createExpandableSubMenu("📋 Selected Vehicle",
                vehicleIdLabel = createInfoValue("ID:", "—"),
                vehicleSpeedLabel = createInfoValue("Speed:", "—"),
                vehicleColorLabel = createInfoValue("Color:", "—"),
                vehicleLocationLabel = createInfoValue("Location:", "—"),
                createSubButton("🔄 Refresh Info", this::onRefreshVehicleInfo)
            )
        ));

        // === TRAFFIC LIGHTS MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("🚦  Traffic Lights", ICON_TRAFFIC_LIGHTS,
            createSubButton("🎯 Select Traffic Light", this::onSelectTrafficLight),
            selectedTlLabel = createInfoValue("Selected:", "—"),
            createSeparator(),
            createSubButton("👁 View Current Phase", this::onViewPhase),
            createSeparator(),
            createSubLabel("Manual Phase Control:"),
            createPhaseButton("🟢 Set GREEN", "#27ae60", this::onSetGreen),
            createPhaseButton("🟡 Set YELLOW", "#f39c12", this::onSetYellow),
            createPhaseButton("🔴 Set RED", "#e74c3c", this::onSetRed)
        ));

        // === ROADS MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("🛣  Roads", ICON_ROADS,
            createSubButton("📍 Select Route", this::onSelectRoute),
            createSubButton("🛤 Select Edge", this::onSelectEdge),
            createSeparator(),
            selectedEdgeLabel = createInfoValue("Selected:", "—"),
            createSubButton("🚗 Spawn at Selection", this::onSpawnAtSelection)
        ));

        // === CHARTS MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("📊  Charts", ICON_CHARTS,
            createSubButton("📈 Average Speed", this::onShowAverageSpeed),
            createSubButton("📊 Vehicle Density", this::onShowVehicleDensity),
            createSubButton("🔥 Congestion Hotspots", this::onShowCongestion),
            createSubButton("⏱ Travel Time Distribution", this::onShowTravelTime)
        ));

        scrollPane.setContent(sidebarBox);
        sliderPane.getChildren().add(scrollPane);

        return sliderPane;
    }

    // ============== EXPANDABLE MENU ==============
    private VBox createExpandableMenu(String title, String iconPath, Node... children) {
        VBox container = new VBox();
        container.setStyle("-fx-background-color: transparent;");
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
        content.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 0 0 8 8;");

        for (Node child : children) {
            content.getChildren().add(child);
        }

        header.setOnAction(e -> {
            boolean isExpanded = content.isVisible();
            content.setVisible(!isExpanded);
            content.setManaged(!isExpanded);
        });

        header.setOnMouseEntered(e -> header.setStyle(
            "-fx-background-color: #146886; -fx-border-color: WHITE; -fx-border-width: 0px 0px 0px 3px;"
        ));
        header.setOnMouseExited(e -> header.setStyle("-fx-background-color: transparent;"));

        container.getChildren().addAll(header, content);
        return container;
    }

    // ============== EXPANDABLE SUB-MENU ==============
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
        arrow.setFont(Font.font("System", FontWeight.BOLD, 8));
        header.setGraphic(arrow);
        header.setContentDisplay(ContentDisplay.RIGHT);

        VBox content = new VBox(3);
        content.setPadding(new Insets(5, 5, 8, 15));
        content.setVisible(false);
        content.setManaged(false);

        for (Node child : children) {
            content.getChildren().add(child);
        }

        header.setOnAction(e -> {
            boolean isExpanded = content.isVisible();
            content.setVisible(!isExpanded);
            content.setManaged(!isExpanded);
            arrow.setText(isExpanded ? "▶" : "▼");
        });

        header.setOnMouseEntered(e -> header.setStyle("-fx-background-color: rgba(255,255,255,0.05);"));
        header.setOnMouseExited(e -> header.setStyle("-fx-background-color: transparent;"));

        container.getChildren().addAll(header, content);
        return container;
    }

    // ============== UI HELPER METHODS ==============
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

        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: rgba(100,180,255,0.2); -fx-background-radius: 6;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 6;"
        ));

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
        btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-background-radius: 6;" +
            "-fx-opacity: 0.85;"
        );

        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-background-radius: 6;" +
            "-fx-opacity: 1.0;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: " + color + ";" +
            "-fx-background-radius: 6;" +
            "-fx-opacity: 0.85;"
        ));

        btn.setOnAction(e -> action.run());
        return btn;
    }

    private Label createInfoValue(String labelText, String initialValue) {
        Label label = new Label(initialValue);
        label.setFont(Font.font("System", FontWeight.BOLD, 11));
        label.setTextFill(Color.web("#aaddff"));
        label.setUserData(labelText);
        return label;
    }

    private Label createSubLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 11));
        label.setTextFill(Color.web("#88aacc"));
        label.setPadding(new Insets(8, 0, 5, 0));
        return label;
    }

    private Region createSeparator() {
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setStyle("-fx-background-color: rgba(255,255,255,0.1);");
        VBox.setMargin(separator, new Insets(5, 0, 5, 0));
        return separator;
    }

    // ============== ACTION HANDLERS ==============

    private void onPlay() {
        if (simController != null && !simulationRunning) {
            startSimulationLoop();
            showNotification("▶ Simulation gestartet");
        }
    }

    private void onPause() {
        if (simulationLoop != null) {
            simulationLoop.stop();
            simulationRunning = false;
            showNotification("⏸ Simulation pausiert");
        }
    }

    private void onStop() {
        if (simulationLoop != null) {
            simulationLoop.stop();
            simulationRunning = false;
        }
        if (simController != null) {
            simController.close();
            simController = null;
        }
        vehicles.clear();
        renderMap();
        showNotification("⏹ Simulation gestoppt");
    }

    // --- Vehicle Actions ---
    private void onSpawnVehicle() {
        if (simController == null) {
            showNotification("⚠ SUMO nicht verbunden!");
            return;
        }

        try {
            List<String> routes = simController.getAllRoutes();
            if (routes.isEmpty()) {
                showNotification("⚠ Keine Routen verfügbar!");
                return;
            }

            String routeId = selectedRoute != null ? selectedRoute : routes.get(0);
            String vehId = "veh_" + System.currentTimeMillis();

            java.awt.Color color = new java.awt.Color(
                (int)(Math.random() * 255),
                (int)(Math.random() * 255),
                (int)(Math.random() * 255)
            );

            simController.injectVehicle(vehId, routeId, 10.0, color);
            showNotification("🚗 Fahrzeug gespawnt: " + vehId);

        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onStressTest() {
        if (simController == null) {
            showNotification("⚠ SUMO nicht verbunden!");
            return;
        }

        new Thread(() -> {
            try {
                List<String> routes = simController.getAllRoutes();
                if (routes.isEmpty()) {
                    Platform.runLater(() -> showNotification("⚠ Keine Routen verfügbar!"));
                    return;
                }

                for (int i = 0; i < 100; i++) {
                    String routeId = routes.get(i % routes.size());
                    String vehId = "stress_" + System.currentTimeMillis() + "_" + i;

                    java.awt.Color color = new java.awt.Color(
                        (int)(Math.random() * 255),
                        (int)(Math.random() * 255),
                        (int)(Math.random() * 255)
                    );

                    simController.injectVehicle(vehId, routeId, 5.0 + Math.random() * 10, color);
                    Thread.sleep(50);
                }

                Platform.runLater(() -> showNotification("⚡ 100 Fahrzeuge gespawnt!"));

            } catch (Exception e) {
                Platform.runLater(() -> showNotification("✗ Fehler: " + e.getMessage()));
            }
        }).start();
    }

    private void onSelectVehicle() {
        if (vehicles.isEmpty()) {
            showNotification("⚠ Keine Fahrzeuge vorhanden!");
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(
            vehicles.get(0).getId(),
            vehicles.stream().map(SumoVehicle::getId).toList()
        );
        dialog.setTitle("Fahrzeug auswählen");
        dialog.setHeaderText("Wählen Sie ein Fahrzeug:");

        dialog.showAndWait().ifPresent(id -> {
            selectedVehicleId = id;
            showNotification("🎯 Fahrzeug ausgewählt: " + id);
            onRefreshVehicleInfo();
        });
    }

    private void onRefreshVehicleInfo() {
        updateSelectedVehicleInfo();
    }

    // --- Traffic Light Actions ---
    private void onSelectTrafficLight() {
        if (simController == null) {
            showNotification("⚠ SUMO nicht verbunden!");
            return;
        }

        try {
            List<String> tlsIds = simController.getTrafficLightIds();
            if (tlsIds.isEmpty()) {
                showNotification("⚠ Keine Ampeln vorhanden!");
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(tlsIds.get(0), tlsIds);
            dialog.setTitle("Ampel auswählen");
            dialog.setHeaderText("Wählen Sie eine Ampel:");

            dialog.showAndWait().ifPresent(id -> {
                selectedTrafficLight = id;
                if (selectedTlLabel != null) selectedTlLabel.setText(id);
                showNotification("🚦 Ampel ausgewählt: " + id);
            });

        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onViewPhase() {
        if (selectedTrafficLight == null) {
            showNotification("⚠ Keine Ampel ausgewählt!");
            return;
        }

        try {
            int phase = simController.getTlsPhase(selectedTrafficLight);
            String state = simController.getRedYellowGreenState(selectedTrafficLight);
            showNotification("👁 Phase " + phase + ": " + state);
        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onSetGreen() {
        setTrafficLightPhase(0, "GREEN");
    }

    private void onSetYellow() {
        setTrafficLightPhase(1, "YELLOW");
    }

    private void onSetRed() {
        setTrafficLightPhase(2, "RED");
    }

    private void setTrafficLightPhase(int phase, String name) {
        if (selectedTrafficLight == null) {
            showNotification("⚠ Keine Ampel ausgewählt!");
            return;
        }

        try {
            simController.setTlsPhase(selectedTrafficLight, phase);
            showNotification("🚦 " + selectedTrafficLight + " → " + name);
        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    // --- Roads Actions ---
    private void onSelectRoute() {
        if (simController == null) {
            showNotification("⚠ SUMO nicht verbunden!");
            return;
        }

        try {
            List<String> routes = simController.getAllRoutes();
            if (routes.isEmpty()) {
                showNotification("⚠ Keine Routen verfügbar!");
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(routes.get(0), routes);
            dialog.setTitle("Route auswählen");
            dialog.setHeaderText("Wählen Sie eine Route:");

            dialog.showAndWait().ifPresent(id -> {
                selectedRoute = id;
                showNotification("📍 Route ausgewählt: " + id);
            });

        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onSelectEdge() {
        if (simController == null) {
            showNotification("⚠ SUMO nicht verbunden!");
            return;
        }

        try {
            List<String> edges = simController.getAllEdges();
            if (edges.isEmpty()) {
                showNotification("⚠ Keine Edges verfügbar!");
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(edges.get(0), edges);
            dialog.setTitle("Edge auswählen");
            dialog.setHeaderText("Wählen Sie eine Edge:");

            dialog.showAndWait().ifPresent(id -> {
                selectedEdge = id;
                if (selectedEdgeLabel != null) selectedEdgeLabel.setText(id);
                showNotification("🛤 Edge ausgewählt: " + id);
                renderMap();
            });

        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onSpawnAtSelection() {
        if (selectedRoute == null && selectedEdge == null) {
            showNotification("⚠ Bitte zuerst Route oder Edge auswählen!");
            return;
        }
        onSpawnVehicle();
    }

    // --- Charts Actions ---
    private void onShowAverageSpeed() {
        if (vehicles.isEmpty()) {
            showNotification("⚠ Keine Fahrzeuge für Statistik!");
            return;
        }

        try {
            double avgSpeed = vehicles.stream()
                .mapToDouble(v -> {
                    try { return v.getSpeed(); }
                    catch (Exception e) { return 0; }
                })
                .average()
                .orElse(0);

            showNotification(String.format("📈 Durchschnittsgeschwindigkeit: %.2f m/s", avgSpeed));
        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onShowVehicleDensity() {
        showNotification("📊 Fahrzeugdichte: " + vehicles.size() + " Fahrzeuge aktiv");
    }

    private void onShowCongestion() {
        Map<String, Integer> edgeCounts = new HashMap<>();
        for (SumoVehicle v : vehicles) {
            try {
                String edge = v.getCurrentEdge();
                edgeCounts.merge(edge, 1, Integer::sum);
            } catch (Exception e) {}
        }

        String hotspot = edgeCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> e.getKey() + " (" + e.getValue() + " Fahrzeuge)")
            .orElse("Keine Hotspots");

        showNotification("🔥 Congestion Hotspot: " + hotspot);
    }

    private void onShowTravelTime() {
        showNotification("⏱ Travel Time Distribution wird geladen...");
    }

    // ============== NOTIFICATION SYSTEM ==============
    private void showNotification(String message) {
        System.out.println("[SUMO GUI] " + message);

        Platform.runLater(() -> {
            Label notification = new Label(message);
            notification.setStyle(
                "-fx-background-color: rgba(0,0,0,0.85);" +
                "-fx-padding: 12 24;" +
                "-fx-background-radius: 6;" +
                "-fx-font-size: 13;" +
                "-fx-font-weight: bold;"
            );
            notification.setTextFill(Color.WHITE);
            notification.setLayoutX(10);
            notification.setLayoutY(10);

            sumoMapPane.getChildren().add(notification);

            new Thread(() -> {
                try { Thread.sleep(3000); }
                catch (InterruptedException ignored) {}
                Platform.runLater(() -> sumoMapPane.getChildren().remove(notification));
            }).start();
        });
    }

    // ============== OTHER METHODS ==============
    private void initializeSliderAnimation() {
        slider.setTranslateX(-260);

        menuLabel.setOnMouseClicked(event -> {
            TranslateTransition slide = new TranslateTransition();
            slide.setDuration(Duration.seconds(0.4));
            slide.setNode(slider);
            slide.setToX(0);
            slide.play();

            slider.setTranslateX(-260);

            slide.setOnFinished(e -> {
                menuLabel.setVisible(false);
                menuCloseLabel.setVisible(true);
            });
        });

        menuCloseLabel.setOnMouseClicked(event -> {
            TranslateTransition slide = new TranslateTransition();
            slide.setDuration(Duration.seconds(0.4));
            slide.setNode(slider);
            slide.setToX(-260);
            slide.play();

            slider.setTranslateX(0);

            slide.setOnFinished(e -> {
                menuLabel.setVisible(true);
                menuCloseLabel.setVisible(false);
            });
        });
    }

    private Pane createSumoMapPane() {
        sumoMapPane = new Pane();
        sumoMapPane.setStyle("-fx-background-color: #1a1a2e;");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(sumoMapPane.widthProperty());
        clip.heightProperty().bind(sumoMapPane.heightProperty());
        sumoMapPane.setClip(clip);

        mapContent = new Pane();
        mapContent.setStyle("-fx-background-color: #1a1a2e;");

        mapCanvas = new Canvas(800, 600);
        mapContent.getChildren().add(mapCanvas);

        sumoMapPane.getChildren().add(mapContent);

        return sumoMapPane;
    }

    private Label createMenuLabel(String text, String imagePath) {
        Label label = new Label(text);
        label.setPrefSize(100, 22);
        label.setTextFill(Color.web("#555555"));
        label.setFont(Font.font("System", FontWeight.BOLD, 14));
        label.setCursor(Cursor.HAND);
        return label;
    }

    private ImageView createImageView(String imagePath, double width, double height) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPickOnBounds(true);
        imageView.setPreserveRatio(true);

        try {
            Image image = new Image(getClass().getResourceAsStream(imagePath));
            if (image != null && !image.isError()) {
                imageView.setImage(image);
            }
        } catch (Exception e) {
            System.out.println("Image not found: " + imagePath);
        }

        return imageView;
    }

    private void setAnchors(javafx.scene.Node node, Double top, Double right, Double bottom, Double left) {
        if (top != null) AnchorPane.setTopAnchor(node, top);
        if (right != null) AnchorPane.setRightAnchor(node, right);
        if (bottom != null) AnchorPane.setBottomAnchor(node, bottom);
        if (left != null) AnchorPane.setLeftAnchor(node, left);
    }

    public Pane getSumoMapPane() { return sumoMapPane; }
    public Pane getMapContent() { return mapContent; }
    public Slider getZoomSlider() { return zoomSlider; }

    public void setSelectedVehicle(String vehicleId) {
        this.selectedVehicleId = vehicleId;
        showNotification("🚗 Fahrzeug ausgewählt: " + vehicleId);
    }

    public void setSelectedEdge(String edgeId) {
        this.selectedEdge = edgeId;
        if (selectedEdgeLabel != null) selectedEdgeLabel.setText(edgeId);
    }

    public void setSelectedTrafficLight(String tlsId) {
        this.selectedTrafficLight = tlsId;
        if (selectedTlLabel != null) selectedTlLabel.setText(tlsId);
        showNotification("🚦 Ampel ausgewählt: " + tlsId);
    }

    public static void main(String[] args) {
        launch(args);
    }
} */






import javafx.animation.AnimationTimer;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
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
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.geom.Point2D;
import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class App extends Application {

    // ============== ICON PATHS - HIER EIGENE ICONS EINTRAGEN ==============
    private static final String ICON_VEHICLES = "Images/Multiple_Cars.png";
    private static final String ICON_TRAFFIC_LIGHTS = "Images/Traffic_Light.png";
    private static final String ICON_ROADS = "Images/Road.png";
    private static final String ICON_CHARTS = "Images/Chart.png";
    private static final String ICON_MENU = "Images/menu1.png";
    // ======================================================================

    // === SUMO Configuration ===
    private static final String SUMO_BIN = "sumo-gui"; // oder vollständiger Pfad
    private String sumoCfgPath = null;
    private String netFilePath = null;

    private double x, y = 0;
    private AnchorPane slider;
    private Label menuLabel;
    private Label menuCloseLabel;
    private Slider zoomSlider;
    private Pane sumoMapPane;
    private Pane mapContent;
    private Canvas mapCanvas;

    // === SUMO Components ===
    private SimulationController simController;
    private List<LaneShape> lanes = new ArrayList<>();
    private List<SumoVehicle> vehicles = new CopyOnWriteArrayList<>();
    private List<TrafficLightBar> trafficLightBars = new CopyOnWriteArrayList<>();
    private NetXmlReader netXmlReader;
    private Map<String, TlsPhaseCollector> tlsCollectors = new HashMap<>();

    // Aktuelle Auswahl
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

    // Info Labels (für Updates)
    private Label vehicleIdLabel, vehicleSpeedLabel, vehicleColorLabel, vehicleLocationLabel;
    private Label selectedTlLabel, selectedEdgeLabel;

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        AnchorPane root = new AnchorPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

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

        // Vollbildmodus
        primaryStage.setMaximized(true);
        primaryStage.setTitle("SUMO Traffic Simulation GUI");

        root.setOnMousePressed(event -> {
            x = event.getSceneX();
            y = event.getSceneY();
        });

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Canvas-Größe an Fenster anpassen
        mapCanvas.widthProperty().bind(sumoMapPane.widthProperty());
        mapCanvas.heightProperty().bind(sumoMapPane.heightProperty());

        // Direkt SUMO laden (ohne Dialog)
        Platform.runLater(this::initializeSumo);
    }

    private void showConfigDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("SUMO Konfiguration");
        dialog.setHeaderText("Bitte wählen Sie die SUMO-Dateien aus");

        ButtonType loadButton = new ButtonType("Laden", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Ohne SUMO starten", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(loadButton, cancelButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField cfgField = new TextField();
        cfgField.setPromptText("Pfad zur .sumocfg Datei");
        cfgField.setPrefWidth(300);

        TextField netField = new TextField();
        netField.setPromptText("Pfad zur .net.xml Datei");
        netField.setPrefWidth(300);

        Button browseCfg = new Button("...");
        browseCfg.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("SUMO Config wählen");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SUMO Config", "*.sumocfg"));
            File file = fc.showOpenDialog(primaryStage);
            if (file != null) cfgField.setText(file.getAbsolutePath());
        });

        Button browseNet = new Button("...");
        browseNet.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Net-Datei wählen");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SUMO Network", "*.net.xml"));
            File file = fc.showOpenDialog(primaryStage);
            if (file != null) netField.setText(file.getAbsolutePath());
        });

        grid.add(new Label("SUMO Config:"), 0, 0);
        grid.add(cfgField, 1, 0);
        grid.add(browseCfg, 2, 0);
        grid.add(new Label("Network File:"), 0, 1);
        grid.add(netField, 1, 1);
        grid.add(browseNet, 2, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(result -> {
            if (result == loadButton) {
                sumoCfgPath = cfgField.getText();
                netFilePath = netField.getText();
                initializeSumo();
            }
        });
    }

    private void initializeSumo() {
        try {
            // Lade Lanes aus net.xml
            if (netFilePath != null && !netFilePath.isEmpty()) {
                lanes = MapDataLoader.loadLanes(netFilePath);
                computeBounds();

                // Lade TLS-Daten
                netXmlReader = new NetXmlReader();
                netXmlReader.parse(netFilePath);

                showNotification("✓ Netzwerk geladen: " + lanes.size() + " Lanes");
            }

            // Starte SUMO
            if (sumoCfgPath != null && !sumoCfgPath.isEmpty()) {
                simController = new SimulationController(SUMO_BIN, sumoCfgPath);
                simController.start();

                // Initialisiere TLS Collectors
                for (String tlsId : simController.getTrafficLightIds()) {
                    TlsPhaseCollector collector = new TlsPhaseCollector(simController, tlsId);
                    collector.initialize(
                            netXmlReader.getDurations(tlsId),
                            netXmlReader.getStates(tlsId)
                    );
                    tlsCollectors.put(tlsId, collector);

                    // Traffic Light Bars erstellen
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
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                if (now - lastUpdate >= 50_000_000) { // ~20 FPS
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

            // Update vehicles
            vehicles.clear();
            for (String vehId : simController.getVehicleIds()) {
                SumoVehicle v = new SumoVehicle(vehId, simController);
                v.refresh();
                vehicles.add(v);
            }

            // Update traffic lights
            for (String tlsId : simController.getTrafficLightIds()) {
                String state = simController.getRedYellowGreenState(tlsId);
                for (TrafficLightBar bar : trafficLightBars) {
                    if (bar.tlsId.equals(tlsId) && bar.indexInState < state.length()) {
                        bar.setStateChar(state.charAt(bar.indexInState));
                    }
                }
            }

            // Update selected vehicle info
            updateSelectedVehicleInfo();

            Platform.runLater(this::renderMap);

        } catch (Exception e) {
            // Simulation might have ended
        }
    }

    private void updateSelectedVehicleInfo() {
        if (selectedVehicleId == null) return;

        try {
            for (SumoVehicle v : vehicles) {
                if (v.getId().equals(selectedVehicleId)) {
                    Platform.runLater(() -> {
                        if (vehicleIdLabel != null) vehicleIdLabel.setText(v.getId());
                        if (vehicleSpeedLabel != null) {
                            try {
                                vehicleSpeedLabel.setText(String.format("%.1f m/s", v.getSpeed()));
                            } catch (Exception ex) {}
                        }
                        if (vehicleColorLabel != null) {
                            java.awt.Color c = v.getColor();
                            vehicleColorLabel.setText(String.format("RGB(%d,%d,%d)", c.getRed(), c.getGreen(), c.getBlue()));
                        }
                        if (vehicleLocationLabel != null) {
                            vehicleLocationLabel.setText(String.format("%.1f, %.1f", v.getX(), v.getY()));
                        }
                    });
                    break;
                }
            }
        } catch (Exception e) {}
    }

    private void renderMap() {
        if (mapCanvas == null) return;

        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        double w = mapCanvas.getWidth();
        double h = mapCanvas.getHeight();

        if (w <= 0 || h <= 0) return;

        // Clear
        gc.setFill(Color.web("#1a1a2e"));
        gc.fillRect(0, 0, w, h);

        if (lanes.isEmpty()) {
            gc.setFill(Color.web("#4a4a6a"));
            gc.setFont(Font.font("System", FontWeight.BOLD, 24));
            gc.fillText("🗺 SUMO Simulation Map", w/2 - 150, h/2);
            gc.setFont(Font.font("System", FontWeight.BOLD, 14));
            gc.fillText("Konfiguration laden um Netzwerk anzuzeigen", w/2 - 160, h/2 + 30);
            return;
        }

        double margin = 30;
        double scaleX = (w - 2 * margin) / (maxX - minX);
        double scaleY = (h - 2 * margin) / (maxY - minY);
        double scale = Math.min(scaleX, scaleY);

        // Lanes zeichnen
        gc.setStroke(Color.DARKGRAY);
        gc.setLineWidth(2);

        for (LaneShape lane : lanes) {
            boolean highlight = lane.getId().equals(selectedEdge + "_0");
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

        // Traffic Light Bars zeichnen
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

        // Vehicles zeichnen
        for (SumoVehicle v : vehicles) {
            double vx = (v.getX() - minX) * scale + margin;
            double vy = h - ((v.getY() - minY) * scale + margin);

            java.awt.Color awtColor = v.getRenderColor();
            gc.setFill(Color.rgb(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue()));

            double r = 6;

            // Highlight selected vehicle
            if (v.getId().equals(selectedVehicleId)) {
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(2);
                gc.strokeOval(vx - r - 3, vy - r - 3, (r + 3) * 2, (r + 3) * 2);
            }

            gc.fillOval(vx - r, vy - r, r * 2, r * 2);
        }
    }

    private AnchorPane createTopPane() {
        AnchorPane topPane = new AnchorPane();
        topPane.setPrefHeight(65);
        topPane.setStyle("-fx-background-color: #ffffff;");

        Pane darkBar = new Pane();
        darkBar.setPrefHeight(25);
        darkBar.setStyle("-fx-background-color: #032d4d;");
        setAnchors(darkBar, 0.0, 0.0, null, 0.0);

        // Control Buttons
        HBox controlBox = new HBox(10);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        controlBox.setLayoutX(10);
        controlBox.setLayoutY(2);

        Button playBtn = createControlButton("▶", "#27ae60", this::onPlay);
        Button pauseBtn = createControlButton("⏸", "#f39c12", this::onPause);
        Button stopBtn = createControlButton("⏹", "#e74c3c", this::onStop);
        Button configBtn = createControlButton("⚙", "#3498db", this::showConfigDialog);

        controlBox.getChildren().addAll(playBtn, pauseBtn, stopBtn, configBtn);
        darkBar.getChildren().add(controlBox);

        // Exit Button
        Button exitBtn = createControlButton("✕", "#ff5555", () -> {
            if (simController != null) simController.close();
            Platform.exit();
        });
        exitBtn.setLayoutX(10);
        exitBtn.setLayoutY(2);
        AnchorPane.setRightAnchor(exitBtn, 10.0);
        AnchorPane.setTopAnchor(exitBtn, 2.0);
        darkBar.getChildren().add(exitBtn);

        // Zoom Controls
        HBox zoomBox = new HBox(10);
        zoomBox.setAlignment(Pos.CENTER);
        zoomBox.setPrefHeight(40);
        AnchorPane.setTopAnchor(zoomBox, 25.0);
        AnchorPane.setRightAnchor(zoomBox, 20.0);
        AnchorPane.setBottomAnchor(zoomBox, 0.0);

        Label zoomLabel = new Label("🔍 Zoom:");
        zoomLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        zoomLabel.setTextFill(Color.web("#333333"));

        zoomSlider = new Slider(0.5, 3.0, 1.0);
        zoomSlider.setPrefWidth(200);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setMajorTickUnit(0.5);
        zoomSlider.setCursor(Cursor.HAND);

        Label zoomValueLabel = new Label("100%");
        zoomValueLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        zoomValueLabel.setPrefWidth(50);

        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int percentage = (int) (newVal.doubleValue() * 100);
            zoomValueLabel.setText(percentage + "%");
            if (mapContent != null) {
                mapContent.setScaleX(newVal.doubleValue());
                mapContent.setScaleY(newVal.doubleValue());
            }
        });

        zoomBox.getChildren().addAll(zoomLabel, zoomSlider, zoomValueLabel);

        menuLabel = createMenuLabel("☰ MENU", ICON_MENU);
        AnchorPane.setLeftAnchor(menuLabel, 14.0);
        AnchorPane.setTopAnchor(menuLabel, 36.0);

        menuCloseLabel = createMenuLabel("✕ CLOSE", ICON_MENU);
        AnchorPane.setLeftAnchor(menuCloseLabel, 14.0);
        AnchorPane.setTopAnchor(menuCloseLabel, 36.0);
        menuCloseLabel.setVisible(false);

        topPane.getChildren().addAll(darkBar, zoomBox, menuLabel, menuCloseLabel);

        return topPane;
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

    private AnchorPane createSlider() {
        AnchorPane sliderPane = new AnchorPane();
        sliderPane.setPrefSize(260, 475);
        sliderPane.setStyle("-fx-background-color: linear-gradient(to bottom, #0A4969, #063450);");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        setAnchors(scrollPane, 20.0, 0.0, 10.0, 0.0);

        VBox sidebarBox = new VBox(5);
        sidebarBox.setPadding(new Insets(10));
        sidebarBox.setStyle("-fx-background-color: transparent;");

        // === VEHICLE MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("🚗  Vehicles", ICON_VEHICLES,
                createSubButton("➕ Spawn Vehicle", this::onSpawnVehicle),
                createSubButton("⚡ Stress Test (100)", this::onStressTest),
                createSubButton("🎯 Select Vehicle", this::onSelectVehicle),
                createExpandableSubMenu("📋 Selected Vehicle",
                        vehicleIdLabel = createInfoValue("ID:", "—"),
                        vehicleSpeedLabel = createInfoValue("Speed:", "—"),
                        vehicleColorLabel = createInfoValue("Color:", "—"),
                        vehicleLocationLabel = createInfoValue("Location:", "—"),
                        createSubButton("🔄 Refresh Info", this::onRefreshVehicleInfo)
                )
        ));

        // === TRAFFIC LIGHTS MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("🚦  Traffic Lights", ICON_TRAFFIC_LIGHTS,
                createSubButton("🎯 Select Traffic Light", this::onSelectTrafficLight),
                selectedTlLabel = createInfoValue("Selected:", "—"),
                createSeparator(),
                createSubButton("👁 View Current Phase", this::onViewPhase),
                createSeparator(),
                createSubLabel("Manual Phase Control:"),
                createPhaseButton("🟢 Set GREEN", "#27ae60", this::onSetGreen),
                createPhaseButton("🟡 Set YELLOW", "#f39c12", this::onSetYellow),
                createPhaseButton("🔴 Set RED", "#e74c3c", this::onSetRed)
        ));

        // === ROADS MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("🛣  Roads", ICON_ROADS,
                createSubButton("📍 Select Route", this::onSelectRoute),
                createSubButton("🛤 Select Edge", this::onSelectEdge),
                createSeparator(),
                selectedEdgeLabel = createInfoValue("Selected:", "—"),
                createSubButton("🚗 Spawn at Selection", this::onSpawnAtSelection)
        ));

        // === CHARTS MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("📊  Charts", ICON_CHARTS,
                createSubButton("📈 Average Speed", this::onShowAverageSpeed),
                createSubButton("📊 Vehicle Density", this::onShowVehicleDensity),
                createSubButton("🔥 Congestion Hotspots", this::onShowCongestion),
                createSubButton("⏱ Travel Time Distribution", this::onShowTravelTime)
        ));

        scrollPane.setContent(sidebarBox);
        sliderPane.getChildren().add(scrollPane);

        return sliderPane;
    }

    // ============== EXPANDABLE MENU ==============
    private VBox createExpandableMenu(String title, String iconPath, Node... children) {
        VBox container = new VBox();
        container.setStyle("-fx-background-color: transparent;");
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
        content.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 0 0 8 8;");

        for (Node child : children) {
            content.getChildren().add(child);
        }

        header.setOnAction(e -> {
            boolean isExpanded = content.isVisible();
            content.setVisible(!isExpanded);
            content.setManaged(!isExpanded);
        });

        header.setOnMouseEntered(e -> header.setStyle(
                "-fx-background-color: #146886; -fx-border-color: WHITE; -fx-border-width: 0px 0px 0px 3px;"
        ));
        header.setOnMouseExited(e -> header.setStyle("-fx-background-color: transparent;"));

        container.getChildren().addAll(header, content);
        return container;
    }

    // ============== EXPANDABLE SUB-MENU ==============
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
        arrow.setFont(Font.font("System", FontWeight.BOLD, 8));
        header.setGraphic(arrow);
        header.setContentDisplay(ContentDisplay.RIGHT);

        VBox content = new VBox(3);
        content.setPadding(new Insets(5, 5, 8, 15));
        content.setVisible(false);
        content.setManaged(false);

        for (Node child : children) {
            content.getChildren().add(child);
        }

        header.setOnAction(e -> {
            boolean isExpanded = content.isVisible();
            content.setVisible(!isExpanded);
            content.setManaged(!isExpanded);
            arrow.setText(isExpanded ? "▶" : "▼");
        });

        header.setOnMouseEntered(e -> header.setStyle("-fx-background-color: rgba(255,255,255,0.05);"));
        header.setOnMouseExited(e -> header.setStyle("-fx-background-color: transparent;"));

        container.getChildren().addAll(header, content);
        return container;
    }

    // ============== UI HELPER METHODS ==============
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

        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: rgba(100,180,255,0.2); -fx-background-radius: 6;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 6;"
        ));

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
        btn.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-background-radius: 6;" +
                        "-fx-opacity: 0.85;"
        );

        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-background-radius: 6;" +
                        "-fx-opacity: 1.0;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: " + color + ";" +
                        "-fx-background-radius: 6;" +
                        "-fx-opacity: 0.85;"
        ));

        btn.setOnAction(e -> action.run());
        return btn;
    }

    private Label createInfoValue(String labelText, String initialValue) {
        Label label = new Label(initialValue);
        label.setFont(Font.font("System", FontWeight.BOLD, 11));
        label.setTextFill(Color.web("#aaddff"));
        label.setUserData(labelText);
        return label;
    }

    private Label createSubLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 11));
        label.setTextFill(Color.web("#88aacc"));
        label.setPadding(new Insets(8, 0, 5, 0));
        return label;
    }

    private Region createSeparator() {
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setStyle("-fx-background-color: rgba(255,255,255,0.1);");
        VBox.setMargin(separator, new Insets(5, 0, 5, 0));
        return separator;
    }

    // ============== ACTION HANDLERS ==============

    private void onPlay() {
        if (simController != null && !simulationRunning) {
            startSimulationLoop();
            showNotification("▶ Simulation gestartet");
        }
    }

    private void onPause() {
        if (simulationLoop != null) {
            simulationLoop.stop();
            simulationRunning = false;
            showNotification("⏸ Simulation pausiert");
        }
    }

    private void onStop() {
        if (simulationLoop != null) {
            simulationLoop.stop();
            simulationRunning = false;
        }
        if (simController != null) {
            simController.close();
            simController = null;
        }
        vehicles.clear();
        renderMap();
        showNotification("⏹ Simulation gestoppt");
    }

    // --- Vehicle Actions ---
    private void onSpawnVehicle() {
        if (simController == null) {
            showNotification("⚠ SUMO nicht verbunden!");
            return;
        }

        try {
            List<String> routes = simController.getAllRoutes();
            if (routes.isEmpty()) {
                showNotification("⚠ Keine Routen verfügbar!");
                return;
            }

            String routeId = selectedRoute != null ? selectedRoute : routes.get(0);
            String vehId = "veh_" + System.currentTimeMillis();

            java.awt.Color color = new java.awt.Color(
                    (int)(Math.random() * 255),
                    (int)(Math.random() * 255),
                    (int)(Math.random() * 255)
            );

            simController.injectVehicle(vehId, routeId, 10.0, color);
            showNotification("🚗 Fahrzeug gespawnt: " + vehId);

        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onStressTest() {
        if (simController == null) {
            showNotification("⚠ SUMO nicht verbunden!");
            return;
        }

        new Thread(() -> {
            try {
                List<String> routes = simController.getAllRoutes();
                if (routes.isEmpty()) {
                    Platform.runLater(() -> showNotification("⚠ Keine Routen verfügbar!"));
                    return;
                }

                for (int i = 0; i < 100; i++) {
                    String routeId = routes.get(i % routes.size());
                    String vehId = "stress_" + System.currentTimeMillis() + "_" + i;

                    java.awt.Color color = new java.awt.Color(
                            (int)(Math.random() * 255),
                            (int)(Math.random() * 255),
                            (int)(Math.random() * 255)
                    );

                    simController.injectVehicle(vehId, routeId, 5.0 + Math.random() * 10, color);
                    Thread.sleep(50);
                }

                Platform.runLater(() -> showNotification("⚡ 100 Fahrzeuge gespawnt!"));

            } catch (Exception e) {
                Platform.runLater(() -> showNotification("✗ Fehler: " + e.getMessage()));
            }
        }).start();
    }

    private void onSelectVehicle() {
        if (vehicles.isEmpty()) {
            showNotification("⚠ Keine Fahrzeuge vorhanden!");
            return;
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(
                vehicles.get(0).getId(),
                vehicles.stream().map(SumoVehicle::getId).toList()
        );
        dialog.setTitle("Fahrzeug auswählen");
        dialog.setHeaderText("Wählen Sie ein Fahrzeug:");

        dialog.showAndWait().ifPresent(id -> {
            selectedVehicleId = id;
            showNotification("🎯 Fahrzeug ausgewählt: " + id);
            onRefreshVehicleInfo();
        });
    }

    private void onRefreshVehicleInfo() {
        updateSelectedVehicleInfo();
    }

    // --- Traffic Light Actions ---
    private void onSelectTrafficLight() {
        if (simController == null) {
            showNotification("⚠ SUMO nicht verbunden!");
            return;
        }

        try {
            List<String> tlsIds = simController.getTrafficLightIds();
            if (tlsIds.isEmpty()) {
                showNotification("⚠ Keine Ampeln vorhanden!");
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(tlsIds.get(0), tlsIds);
            dialog.setTitle("Ampel auswählen");
            dialog.setHeaderText("Wählen Sie eine Ampel:");

            dialog.showAndWait().ifPresent(id -> {
                selectedTrafficLight = id;
                if (selectedTlLabel != null) selectedTlLabel.setText(id);
                showNotification("🚦 Ampel ausgewählt: " + id);
            });

        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onViewPhase() {
        if (selectedTrafficLight == null) {
            showNotification("⚠ Keine Ampel ausgewählt!");
            return;
        }

        try {
            int phase = simController.getTlsPhase(selectedTrafficLight);
            String state = simController.getRedYellowGreenState(selectedTrafficLight);
            showNotification("👁 Phase " + phase + ": " + state);
        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onSetGreen() {
        setTrafficLightPhase(0, "GREEN");
    }

    private void onSetYellow() {
        setTrafficLightPhase(1, "YELLOW");
    }

    private void onSetRed() {
        setTrafficLightPhase(2, "RED");
    }

    private void setTrafficLightPhase(int phase, String name) {
        if (selectedTrafficLight == null) {
            showNotification("⚠ Keine Ampel ausgewählt!");
            return;
        }

        try {
            simController.setTlsPhase(selectedTrafficLight, phase);
            showNotification("🚦 " + selectedTrafficLight + " → " + name);
        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    // --- Roads Actions ---
    private void onSelectRoute() {
        if (simController == null) {
            showNotification("⚠ SUMO nicht verbunden!");
            return;
        }

        try {
            List<String> routes = simController.getAllRoutes();
            if (routes.isEmpty()) {
                showNotification("⚠ Keine Routen verfügbar!");
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(routes.get(0), routes);
            dialog.setTitle("Route auswählen");
            dialog.setHeaderText("Wählen Sie eine Route:");

            dialog.showAndWait().ifPresent(id -> {
                selectedRoute = id;
                showNotification("📍 Route ausgewählt: " + id);
            });

        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onSelectEdge() {
        if (simController == null) {
            showNotification("⚠ SUMO nicht verbunden!");
            return;
        }

        try {
            List<String> edges = simController.getAllEdges();
            if (edges.isEmpty()) {
                showNotification("⚠ Keine Edges verfügbar!");
                return;
            }

            ChoiceDialog<String> dialog = new ChoiceDialog<>(edges.get(0), edges);
            dialog.setTitle("Edge auswählen");
            dialog.setHeaderText("Wählen Sie eine Edge:");

            dialog.showAndWait().ifPresent(id -> {
                selectedEdge = id;
                if (selectedEdgeLabel != null) selectedEdgeLabel.setText(id);
                showNotification("🛤 Edge ausgewählt: " + id);
                renderMap();
            });

        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onSpawnAtSelection() {
        if (selectedRoute == null && selectedEdge == null) {
            showNotification("⚠ Bitte zuerst Route oder Edge auswählen!");
            return;
        }
        onSpawnVehicle();
    }

    // --- Charts Actions ---
    private void onShowAverageSpeed() {
        if (vehicles.isEmpty()) {
            showNotification("⚠ Keine Fahrzeuge für Statistik!");
            return;
        }

        try {
            double avgSpeed = vehicles.stream()
                    .mapToDouble(v -> {
                        try { return v.getSpeed(); }
                        catch (Exception e) { return 0; }
                    })
                    .average()
                    .orElse(0);

            showNotification(String.format("📈 Durchschnittsgeschwindigkeit: %.2f m/s", avgSpeed));
        } catch (Exception e) {
            showNotification("✗ Fehler: " + e.getMessage());
        }
    }

    private void onShowVehicleDensity() {
        showNotification("📊 Fahrzeugdichte: " + vehicles.size() + " Fahrzeuge aktiv");
    }

    private void onShowCongestion() {
        Map<String, Integer> edgeCounts = new HashMap<>();
        for (SumoVehicle v : vehicles) {
            try {
                String edge = v.getCurrentEdge();
                edgeCounts.merge(edge, 1, Integer::sum);
            } catch (Exception e) {}
        }

        String hotspot = edgeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " (" + e.getValue() + " Fahrzeuge)")
                .orElse("Keine Hotspots");

        showNotification("🔥 Congestion Hotspot: " + hotspot);
    }

    private void onShowTravelTime() {
        showNotification("⏱ Travel Time Distribution wird geladen...");
    }

    // ============== NOTIFICATION SYSTEM ==============
    private void showNotification(String message) {
        System.out.println("[SUMO GUI] " + message);

        Platform.runLater(() -> {
            Label notification = new Label(message);
            notification.setStyle(
                    "-fx-background-color: rgba(0,0,0,0.85);" +
                            "-fx-padding: 12 24;" +
                            "-fx-background-radius: 6;" +
                            "-fx-font-size: 13;" +
                            "-fx-font-weight: bold;"
            );
            notification.setTextFill(Color.WHITE);
            notification.setLayoutX(10);
            notification.setLayoutY(10);

            sumoMapPane.getChildren().add(notification);

            new Thread(() -> {
                try { Thread.sleep(3000); }
                catch (InterruptedException ignored) {}
                Platform.runLater(() -> sumoMapPane.getChildren().remove(notification));
            }).start();
        });
    }

    // ============== OTHER METHODS ==============
    private void initializeSliderAnimation() {
        slider.setTranslateX(-260);

        menuLabel.setOnMouseClicked(event -> {
            TranslateTransition slide = new TranslateTransition();
            slide.setDuration(Duration.seconds(0.4));
            slide.setNode(slider);
            slide.setToX(0);
            slide.play();

            slider.setTranslateX(-260);

            slide.setOnFinished(e -> {
                menuLabel.setVisible(false);
                menuCloseLabel.setVisible(true);
            });
        });

        menuCloseLabel.setOnMouseClicked(event -> {
            TranslateTransition slide = new TranslateTransition();
            slide.setDuration(Duration.seconds(0.4));
            slide.setNode(slider);
            slide.setToX(-260);
            slide.play();

            slider.setTranslateX(0);

            slide.setOnFinished(e -> {
                menuLabel.setVisible(true);
                menuCloseLabel.setVisible(false);
            });
        });
    }

    private Pane createSumoMapPane() {
        sumoMapPane = new Pane();
        sumoMapPane.setStyle("-fx-background-color: #1a1a2e;");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(sumoMapPane.widthProperty());
        clip.heightProperty().bind(sumoMapPane.heightProperty());
        sumoMapPane.setClip(clip);

        mapContent = new Pane();
        mapContent.setStyle("-fx-background-color: #1a1a2e;");

        mapCanvas = new Canvas(800, 600);
        mapContent.getChildren().add(mapCanvas);

        sumoMapPane.getChildren().add(mapContent);

        return sumoMapPane;
    }

    private Label createMenuLabel(String text, String imagePath) {
        Label label = new Label(text);
        label.setPrefSize(100, 22);
        label.setTextFill(Color.web("#555555"));
        label.setFont(Font.font("System", FontWeight.BOLD, 14));
        label.setCursor(Cursor.HAND);
        return label;
    }

    private ImageView createImageView(String imagePath, double width, double height) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPickOnBounds(true);
        imageView.setPreserveRatio(true);

        try {
            Image image = new Image(getClass().getResourceAsStream(imagePath));
            if (image != null && !image.isError()) {
                imageView.setImage(image);
            }
        } catch (Exception e) {
            System.out.println("Image not found: " + imagePath);
        }

        return imageView;
    }

    private void setAnchors(javafx.scene.Node node, Double top, Double right, Double bottom, Double left) {
        if (top != null) AnchorPane.setTopAnchor(node, top);
        if (right != null) AnchorPane.setRightAnchor(node, right);
        if (bottom != null) AnchorPane.setBottomAnchor(node, bottom);
        if (left != null) AnchorPane.setLeftAnchor(node, left);
    }

    public Pane getSumoMapPane() { return sumoMapPane; }
    public Pane getMapContent() { return mapContent; }
    public Slider getZoomSlider() { return zoomSlider; }

    public void setSelectedVehicle(String vehicleId) {
        this.selectedVehicleId = vehicleId;
        showNotification("🚗 Fahrzeug ausgewählt: " + vehicleId);
    }

    public void setSelectedEdge(String edgeId) {
        this.selectedEdge = edgeId;
        if (selectedEdgeLabel != null) selectedEdgeLabel.setText(edgeId);
    }

    public void setSelectedTrafficLight(String tlsId) {
        this.selectedTrafficLight = tlsId;
        if (selectedTlLabel != null) selectedTlLabel.setText(tlsId);
        showNotification("🚦 Ampel ausgewählt: " + tlsId);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
