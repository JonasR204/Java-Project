import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class MainApp extends Application {

    // ============== ICON PATHS - HIER EIGENE ICONS EINTRAGEN ==============
    private static final String ICON_VEHICLES = "Images/Multiple_Cars.png";
    private static final String ICON_TRAFFIC_LIGHTS = "Images/Traffic_Light.png";
    private static final String ICON_ROADS = "Images/Road.png";
    private static final String ICON_CHARTS = "Images/Chart.png";
    private static final String ICON_MENU = "Images/menu1.png";
    // ======================================================================

    private double x, y = 0;
    private AnchorPane slider;
    private Label menuLabel;
    private Label menuCloseLabel;
    private Slider zoomSlider;
    private Pane sumoMapPane;

    // Aktuelle Auswahl
    private String selectedVehicleId = null;
    private String selectedEdge = null;
    private String selectedTrafficLight = null;

    @Override
    public void start(Stage primaryStage) {
        AnchorPane root = new AnchorPane();
        root.setPrefSize(900, 500);

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

        primaryStage.initStyle(StageStyle.UNDECORATED);

        root.setOnMousePressed(event -> {
            x = event.getSceneX();
            y = event.getSceneY();
        });

        root.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - x);
            primaryStage.setY(event.getScreenY() - y);
        });

        Scene scene = new Scene(root, 900, 500);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private AnchorPane createTopPane() {
        AnchorPane topPane = new AnchorPane();
        topPane.setPrefHeight(65);
        topPane.setStyle("-fx-background-color: WHITE;");

        Pane darkBar = new Pane();
        darkBar.setPrefHeight(25);
        darkBar.setStyle("-fx-background-color: #032d4d;");
        setAnchors(darkBar, 0.0, 0.0, null, 0.0);

        // Exit Button
        Button exitBtn = createIconButton("✕", "#ff5555");
        exitBtn.setLayoutX(870);
        exitBtn.setLayoutY(2);
        exitBtn.setOnAction(e -> System.exit(0));
        darkBar.getChildren().add(exitBtn);

        // Zoom Controls
        HBox zoomBox = new HBox(10);
        zoomBox.setAlignment(Pos.CENTER);
        zoomBox.setPrefSize(525, 43);
        AnchorPane.setTopAnchor(zoomBox, 25.0);
        AnchorPane.setRightAnchor(zoomBox, 10.0);
        AnchorPane.setBottomAnchor(zoomBox, 0.0);

        Label zoomLabel = new Label("🔍 Zoom:");
        zoomLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        zoomLabel.setTextFill(Color.web("#333333"));

        zoomSlider = new Slider(0.5, 3.0, 1.0);
        zoomSlider.setPrefWidth(300);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setMajorTickUnit(0.5);
        zoomSlider.setBlockIncrement(0.1);
        zoomSlider.setCursor(Cursor.HAND);

        Label zoomValueLabel = new Label("100%");
        zoomValueLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        zoomValueLabel.setPrefWidth(50);

        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int percentage = (int) (newVal.doubleValue() * 100);
            zoomValueLabel.setText(percentage + "%");
            if (sumoMapPane != null) {
                sumoMapPane.setScaleX(newVal.doubleValue());
                sumoMapPane.setScaleY(newVal.doubleValue());
            }
        });

        zoomBox.getChildren().addAll(zoomLabel, zoomSlider, zoomValueLabel);

        menuLabel = createMenuLabel("MENU", "Images/menu1.png");
        AnchorPane.setLeftAnchor(menuLabel, 14.0);
        AnchorPane.setTopAnchor(menuLabel, 36.0);
        AnchorPane.setBottomAnchor(menuLabel, 7.0);

        menuCloseLabel = createMenuLabel("✕ CLOSE", "Images/menu1.png");
        AnchorPane.setLeftAnchor(menuCloseLabel, 14.0);
        AnchorPane.setTopAnchor(menuCloseLabel, 36.0);
        AnchorPane.setBottomAnchor(menuCloseLabel, 7.0);
        menuCloseLabel.setVisible(false);

        topPane.getChildren().addAll(darkBar, zoomBox, menuLabel, menuCloseLabel);

        return topPane;
    }

    private AnchorPane createSlider() {
        AnchorPane sliderPane = new AnchorPane();
        sliderPane.setPrefSize(240, 475);
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
                createExpandableSubMenu("📋 Selected Vehicle",
                        createInfoLabel("ID:", "vehicle-id-label"),
                        createInfoLabel("Speed:", "vehicle-speed-label"),
                        createInfoLabel("Color:", "vehicle-color-label"),
                        createInfoLabel("Location:", "vehicle-location-label"),
                        createSubButton("🔄 Refresh Info", this::onRefreshVehicleInfo)
                )
        ));

        // === TRAFFIC LIGHTS MENU ===
        sidebarBox.getChildren().add(createExpandableMenu("🚦  Traffic Lights", ICON_TRAFFIC_LIGHTS,
                createSubButton("🎯 Select Traffic Light", this::onSelectTrafficLight),
                createInfoLabel("Selected:", "selected-tl-label"),
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
                createInfoLabel("Selected:", "selected-edge-label"),
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

        // Header Button - nur Icon wie vorher
        Button header = new Button();
        header.setPrefWidth(240);
        header.setPrefHeight(42);
        header.setAlignment(Pos.CENTER);
        header.setCursor(Cursor.HAND);
        header.setStyle("-fx-background-color: transparent;");
        header.setGraphic(createImageView(iconPath, 26, 26));

        // Tooltip für Beschreibung
        header.setTooltip(new Tooltip(title));

        // Content container
        VBox content = new VBox(3);
        content.setPadding(new Insets(8, 5, 10, 20));
        content.setVisible(false);
        content.setManaged(false);
        content.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 0 0 8 8;");

        for (Node child : children) {
            content.getChildren().add(child);
        }

        // Toggle
        header.setOnAction(e -> {
            boolean isExpanded = content.isVisible();
            content.setVisible(!isExpanded);
            content.setManaged(!isExpanded);
        });

        // Hover-Effekt wie alte Sidebar-Buttons
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
        header.setFont(Font.font("System", FontWeight.NORMAL, 12));
        header.setTextFill(Color.web("#aaddff"));
        header.setCursor(Cursor.HAND);
        header.setStyle("-fx-background-color: transparent;");

        Label arrow = new Label("▶");
        arrow.setTextFill(Color.web("#88ccff"));
        arrow.setFont(Font.font("System", 8));
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
        btn.setPrefWidth(180);
        btn.setPrefHeight(36);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(0, 8, 0, 12));
        btn.setFont(Font.font("System", 12));
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
        btn.setPrefWidth(180);
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

    private HBox createInfoLabel(String labelText, String valueId) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 0, 4, 0));

        Label label = new Label(labelText);
        label.setFont(Font.font("System", FontWeight.BOLD, 11));
        label.setTextFill(Color.web("#88aacc"));
        label.setMinWidth(60);

        Label value = new Label("—");
        value.setId(valueId);
        value.setFont(Font.font("Monospace", 11));
        value.setTextFill(Color.web("#aaddff"));

        box.getChildren().addAll(label, value);
        return box;
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

    private Button createIconButton(String text, String color) {
        Button btn = new Button(text);
        btn.setFont(Font.font("System", FontWeight.BOLD, 12));
        btn.setTextFill(Color.web(color));
        btn.setStyle("-fx-background-color: transparent;");
        btn.setCursor(Cursor.HAND);
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: rgba(255,255,255,0.1);"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent;"));
        return btn;
    }

    // ============== ACTION HANDLERS ==============

    // --- Vehicle Actions ---
    private void onSpawnVehicle() {
        showNotification("🚗 Spawning new vehicle...");
        // TODO: SUMO TraCI integration
        // traci.vehicle.add(...)
    }

    private void onStressTest() {
        showNotification("⚡ Stress Test: Spawning 100 vehicles...");
        // TODO: Loop to spawn 100 vehicles
        // for (int i = 0; i < 100; i++) { traci.vehicle.add(...) }
    }

    private void onRefreshVehicleInfo() {
        if (selectedVehicleId != null) {
            showNotification("🔄 Refreshing info for: " + selectedVehicleId);
            // TODO: Get vehicle info from SUMO
            // updateLabel("vehicle-id-label", selectedVehicleId);
            // updateLabel("vehicle-speed-label", traci.vehicle.getSpeed(selectedVehicleId));
            // updateLabel("vehicle-color-label", traci.vehicle.getColor(selectedVehicleId));
            // updateLabel("vehicle-location-label", traci.vehicle.getPosition(selectedVehicleId));
        } else {
            showNotification("⚠ No vehicle selected!");
        }
    }

    // --- Traffic Light Actions ---
    private void onSelectTrafficLight() {
        showNotification("🎯 Click on a traffic light on the map to select it...");
        // TODO: Enable traffic light selection mode on map
        // When selected, call: setSelectedTrafficLight(tlsId);
    }

    private void onViewPhase() {
        if (selectedTrafficLight != null) {
            showNotification("👁 Viewing phase for: " + selectedTrafficLight);
            // TODO: traci.trafficlight.getPhase(selectedTrafficLight)
        } else {
            showNotification("⚠ No traffic light selected! Please select one first.");
        }
    }

    private void onSetGreen() {
        if (selectedTrafficLight != null) {
            showNotification("🟢 Setting " + selectedTrafficLight + " to GREEN");
            // TODO: traci.trafficlight.setPhase(selectedTrafficLight, greenPhaseIndex)
        } else {
            showNotification("⚠ No traffic light selected!");
        }
    }

    private void onSetYellow() {
        if (selectedTrafficLight != null) {
            showNotification("🟡 Setting " + selectedTrafficLight + " to YELLOW");
            // TODO: traci.trafficlight.setPhase(selectedTrafficLight, yellowPhaseIndex)
        } else {
            showNotification("⚠ No traffic light selected!");
        }
    }

    private void onSetRed() {
        if (selectedTrafficLight != null) {
            showNotification("🔴 Setting " + selectedTrafficLight + " to RED");
            // TODO: traci.trafficlight.setPhase(selectedTrafficLight, redPhaseIndex)
        } else {
            showNotification("⚠ No traffic light selected!");
        }
    }

    // --- Roads Actions ---
    private void onSelectRoute() {
        showNotification("📍 Click on map to select a route...");
        // TODO: Enable route selection mode on map
    }

    private void onSelectEdge() {
        showNotification("🛤 Click on map to select an edge...");
        // TODO: Enable edge selection mode on map
    }

    private void onSpawnAtSelection() {
        if (selectedEdge != null) {
            showNotification("🚗 Spawning vehicle at: " + selectedEdge);
            // TODO: traci.vehicle.add(..., route=selectedEdge)
        } else {
            showNotification("⚠ No edge selected! Please select an edge first.");
        }
    }

    // --- Charts Actions ---
    private void onShowAverageSpeed() {
        showNotification("📈 Loading Average Speed chart...");
        // TODO: Open chart window with average speed data
    }

    private void onShowVehicleDensity() {
        showNotification("📊 Loading Vehicle Density per Edge...");
        // TODO: Open chart window with density data
    }

    private void onShowCongestion() {
        showNotification("🔥 Loading Congestion Hotspots...");
        // TODO: Highlight congestion areas on map
    }

    private void onShowTravelTime() {
        showNotification("⏱ Loading Travel Time Distribution...");
        // TODO: Open chart window with travel time histogram
    }

    // ============== NOTIFICATION SYSTEM ==============
    private void showNotification(String message) {
        System.out.println("[SUMO GUI] " + message);

        // Visual notification on map
        Label notification = new Label(message);
        notification.setStyle(
                "-fx-background-color: rgba(0,0,0,0.8);" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 10 20;" +
                        "-fx-background-radius: 5;" +
                        "-fx-font-size: 12;"
        );
        notification.setTextFill(Color.WHITE);
        notification.setLayoutX(10);
        notification.setLayoutY(10);

        sumoMapPane.getChildren().add(notification);

        // Auto-remove after 3 seconds
        TranslateTransition fadeOut = new TranslateTransition(Duration.seconds(3), notification);
        fadeOut.setOnFinished(e -> sumoMapPane.getChildren().remove(notification));
        fadeOut.play();
    }

    // ============== OTHER METHODS ==============
    private void initializeSliderAnimation() {
        slider.setTranslateX(-240);

        menuLabel.setOnMouseClicked(event -> {
            TranslateTransition slide = new TranslateTransition();
            slide.setDuration(Duration.seconds(0.4));
            slide.setNode(slider);
            slide.setToX(0);
            slide.play();

            slider.setTranslateX(-240);

            slide.setOnFinished(e -> {
                menuLabel.setVisible(false);
                menuCloseLabel.setVisible(true);
            });
        });

        menuCloseLabel.setOnMouseClicked(event -> {
            TranslateTransition slide = new TranslateTransition();
            slide.setDuration(Duration.seconds(0.4));
            slide.setNode(slider);
            slide.setToX(-240);
            slide.play();

            slider.setTranslateX(0);

            slide.setOnFinished(e -> {
                menuLabel.setVisible(true);
                menuCloseLabel.setVisible(false);
            });
        });
    }

    private Pane createSumoMapPane() {
        Pane mapPane = new Pane();
        mapPane.setStyle("-fx-background-color: #1a1a2e;");

        Label placeholder = new Label("🗺 SUMO Simulation Map");
        placeholder.setTextFill(Color.web("#4a4a6a"));
        placeholder.setFont(Font.font("System", FontWeight.BOLD, 24));
        placeholder.setLayoutX(280);
        placeholder.setLayoutY(200);

        Label infoLabel = new Label("Connect to SUMO to display the network");
        infoLabel.setTextFill(Color.web("#3a3a5a"));
        infoLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));
        infoLabel.setLayoutX(290);
        infoLabel.setLayoutY(240);

        mapPane.getChildren().addAll(placeholder, infoLabel);

        return mapPane;
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

    public Pane getSumoMapPane() {
        return sumoMapPane;
    }

    public Slider getZoomSlider() {
        return zoomSlider;
    }

    public void setSelectedVehicle(String vehicleId) {
        this.selectedVehicleId = vehicleId;
    }

    public void setSelectedEdge(String edgeId) {
        this.selectedEdge = edgeId;
    }

    public void setSelectedTrafficLight(String tlsId) {
        this.selectedTrafficLight = tlsId;
        showNotification("🚦 Selected traffic light: " + tlsId);
    }

    public static void main(String[] args) {
        launch(args);
    }
}