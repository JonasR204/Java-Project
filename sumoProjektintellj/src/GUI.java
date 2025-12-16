// GUI KLASSE DIE SUMO STARTET; DIE LÄD DIE MAP;zeigt(MapPANEL) wird angeanzeigt;
//start/pausieren der SUMO Simulation (über simulationscontroller)
//und aktualisiert Vahrzeugpositionen import javax.swing.*;

import java.awt.*;        						//LAYOUT,FARBEN etc
import java.awt.event.ActionEvent;				//Button-Klicks ergebnisse
import java.awt.geom.Point2D;					//Koordinaten für positionen
import java.util.ArrayList;						//Listen für Lanes und fahrzeuge
import java.util.List;
import javax.swing.*;							//ALLES FÜR FENSTER,BUTTONS,LABELS, TIMER
import java.util.Map;
import java.util.HashMap;


public class GUI extends JFrame {						//GUI ist Fenster , erbt von JFrame

    
    private SimulationController controller;			//BACKEND

    													
    private MapPanel mapPanel;							//zeichnet die MAP

   
    private Timer timer;									//ruft ständig UPDATEDs auf

   
    private final JTextField sumoExeField = new JTextField(30);					//Textfeld für sumo-gui pfad und sumocfg datei
    private final JTextField cfgField     = new JTextField(30);

   
    private final JButton startButton   = new JButton("Start / Resume");		//BUTTONS FÜR STEUERUNG
    private final JButton pauseButton   = new JButton("Pause");
    private final JButton restartButton = new JButton("Restart");


 
    private final JButton spawnButton   = new JButton("Spawn Vehicle");			//BUTTON FARZEUG SPWAN
    private final JButton stressTestButton = new JButton("Stresstest (50)");

    private final List<TrafficLightBar> trafficLightBars = new ArrayList<>();

    private List<String> cachedRoutes = new ArrayList<>();					//ROUTEN WERDEN EINMAL GELADEN, dann nie wieder
   
    private final JLabel statusLabel = new JLabel("Ready");					//STATUSANZEIGE im fenster

    //FÜR TRAFFIC LIGHT CONTROL
    private JComboBox<String> tlsDropdown;
    private JButton phasePlusBtn;
    private JButton phaseMinusBtn;
    private JButton hold10Btn;
    private JLabel tlsInfoLabel;




    public GUI() {												
        super("SUMO Vehicle Simulator");						//super()->FENSTERTITEL

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);										//fenstergröße
        setLocationRelativeTo(null);						//BILDSCHIRMMITTE (null)

        initLayout();					//baut die Oberfläche (Map+Buttons)
        initTimer();					//baut die update schleife
        updateUiState(false); 			//am anfang läuft nix (button)
    }

    private void initLayout() {
      
      
        String netPath = "/usr/local/Cellar/sumo/1.20.0/share/sumo/tools/game/square/square.net.xml";		//straßengeometrie aus net.xml in LAne shape objekte
        List<LaneShape> lanes = MapDataLoader.loadLanes(netPath);
        mapPanel = new MapPanel(lanes);											//MapPanel bekommt lanes und kann sie zeichen

        																//Obere Steuerleiste
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);					//insets ->abstand elemente
        gbc.fill = GridBagConstraints.HORIZONTAL;				//elemete ziehen in die breite mit

        
        sumoExeField.setText("/usr/local/opt/sumo/share/sumo/bin/sumo-gui");
        cfgField.setText("hier config pfad eingeben");
        																//POSITIONEN im fenster
        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("Pfad zu sumo-gui:"), gbc);
        gbc.gridx = 1;
        topPanel.add(sumoExeField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        topPanel.add(new JLabel("Pfad zur .sumocfg:"), gbc);
        gbc.gridx = 1;
        topPanel.add(cfgField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        topPanel.add(startButton, gbc);
        gbc.gridx = 1;
        topPanel.add(pauseButton, gbc);
        gbc.gridx = 2;
        topPanel.add(restartButton, gbc);

        gbc.gridx = 3; gbc.gridy = 2;
        topPanel.add(spawnButton, gbc);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 4;
        topPanel.add(statusLabel, gbc);

        gbc.gridx = 4; gbc.gridy = 2;
        topPanel.add(stressTestButton, gbc);
        stressTestButton.addActionListener(this::onStressTestClicked);



        // ================= Traffic Light Control =================
        tlsDropdown = new JComboBox<>();
        tlsDropdown.setEnabled(false);

        phaseMinusBtn = new JButton("Phase -");
        phasePlusBtn  = new JButton("Phase +");
        hold10Btn     = new JButton("Hold 10s");
        tlsInfoLabel  = new JLabel("TLS: -");

        phaseMinusBtn.setEnabled(false);
        phasePlusBtn.setEnabled(false);
        hold10Btn.setEnabled(false);

// Aktionen
        phaseMinusBtn.addActionListener(ev -> changeTlsPhase(-1));
        phasePlusBtn.addActionListener(ev -> changeTlsPhase(+1));
        hold10Btn.addActionListener(ev -> holdTlsForSeconds(10));
        tlsDropdown.addActionListener(ev -> refreshSelectedTlsInfo());

// Platzierung (neue Zeile unter Status)
        gbc.gridwidth = 1;
        gbc.gridy = 4;

        gbc.gridx = 0;
        topPanel.add(new JLabel("Traffic Light:"), gbc);

        gbc.gridx = 1;
        topPanel.add(tlsDropdown, gbc);

        gbc.gridx = 2;
        topPanel.add(phaseMinusBtn, gbc);

        gbc.gridx = 3;
        topPanel.add(phasePlusBtn, gbc);

        gbc.gridx = 4;
        topPanel.add(hold10Btn, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 5;
        topPanel.add(tlsInfoLabel, gbc);
// =========================================================




        //WENN BUTTON GEDRÜCKT WIRD, DANN..
        startButton.addActionListener(this::onStartClicked);
        pauseButton.addActionListener(this::onPauseClicked);
        restartButton.addActionListener(this::onRestartClicked);

        spawnButton.addActionListener(this::onSpawnClicked);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(topPanel, BorderLayout.NORTH);
        getContentPane().add(mapPanel, BorderLayout.CENTER);
    }
    																//updated regelmäßig alle 200 ms daten aus SUMO)
    private void initTimer() {
        // Alle 200ms: Step + Fahrzeugdaten holen + Map aktualisieren
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



    private void onStartClicked(ActionEvent e) { // start BUTTON
        try {
            String sumoExe = sumoExeField.getText().trim();
            String cfgPath = cfgField.getText().trim();

            // Eingabeüberprüfung: ohne Pfad kann SUMO nicht starten
            if (sumoExe.isEmpty() || cfgPath.isEmpty() || cfgPath.equalsIgnoreCase("hier config pfad eingeben")) {
                JOptionPane.showMessageDialog(this,
                        "Bitte Pfad zu sumo-gui und zur .sumocfg angeben.",
                        "Fehlende Eingaben",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Falls Controller noch nicht existiert: erstellen
            if (controller == null) {
                controller = new SimulationController(sumoExe, cfgPath);
            }

            // Falls SUMO noch nicht läuft: starten (und direkt TLS laden)
            if (!controller.isRunning()) {
                controller.start();
                loadTrafficLightsFromSumo();   // <- MUSS hier rein (nach erfolgreichem Start)
                loadTlsDropdown();

            }

            // Resume / laufen lassen
            timer.start();
            statusLabel.setText("Running...");
            updateUiState(true);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Fehler beim Starten/Resuming:\n" + ex.getMessage(),
                    "Fehler",
                    JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("Start failed: " + ex.getMessage());
            updateUiState(false);
        }
    }


    										// Pause = nur Timer(UPDATES) stoppen, Verbindung bleibt offen
    private void onPauseClicked(ActionEvent e) {
        if (timer != null) timer.stop();
        statusLabel.setText("Paused");
        updateUiState(false);
    }

    					// Restart = Timer stoppen + Verbindung schließen + Controller neu aufbauen 
    private void onRestartClicked(ActionEvent e) {
        try {
            if (timer != null) timer.stop();

            if (controller != null) {
                try { controller.close(); } catch (Exception ignored) {}
            }
            controller = null;

            // Map resetten / vehicles clear
            mapPanel.setVehicles(new ArrayList<>());

            statusLabel.setText("Restarted (ready). Click Start.");
            updateUiState(false);

        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Restart failed: " + ex.getMessage());
            updateUiState(false);
        }


        trafficLightBars.clear();
        mapPanel.setTrafficLights(new ArrayList<>());


    }


    							//Spawn-Button: erzeugt sofort ein neues Fahrzeug auf einer zufälligen Route 
    private void onSpawnClicked(ActionEvent e) {
        try {
            if (controller == null || !controller.isRunning()) {
                JOptionPane.showMessageDialog(this,
                        "Bitte erst die Simulation starten.",
                        "Nicht gestartet",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            										// Routes bei Bedarf einmalig laden
            if (cachedRoutes == null || cachedRoutes.isEmpty()) {
                cachedRoutes = controller.getAllRoutes();
                System.out.println("Loaded routes: " + cachedRoutes);
            }

            SumoVehicle v = controller.spawnRandomVehicle(cachedRoutes);
            if (v != null) {
                statusLabel.setText("Spawned: " + v.getId());
                // optional: direkt neu zeichnen
                updateFromSumo();
            } else {
                statusLabel.setText("Spawn failed (no vehicle created).");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Spawn failed: " + ex.getMessage());
        }
    }

    private void updateUiState(boolean running) {
    													// running = timer läuft (Simulation “läuft”)
        startButton.setEnabled(!running); // Start/Resume
        pauseButton.setEnabled(running);  // Pause
        restartButton.setEnabled(true);   // immer möglich
        spawnButton.setEnabled(running && controller != null && controller.isRunning());
        stressTestButton.setEnabled(running && controller != null && controller.isRunning());

    }

    private void updateFromSumo() throws Exception {
        if (controller == null || !controller.isRunning()) return;

        if (!trafficLightBars.isEmpty()) {
            updateTrafficLightColors();
            mapPanel.setTrafficLights(trafficLightBars);
        }

        controller.step();										//schritt in SUMO

        List<String> ids = controller.getVehicleIds(); 			//FAHRZEUG ids holen
        System.out.println("vehicles=" + ids.size());

        refreshSelectedTlsInfo();

        List<VehicleState> vehStates = new ArrayList<>(ids.size());		//POSITION UM ZU FÄRBEN	
        for (String id : ids) {
            Point2D.Double pos = controller.getVehiclePosition(id);
            vehStates.add(new VehicleState(id, pos.x, pos.y, Color.BLUE));
        }

        mapPanel.setVehicles(vehStates); 			//MapPanel bekommt neue Liste und zeichet neu
    }


    private void loadTrafficLightsFromSumo() {
        trafficLightBars.clear();

        try {
            List<String> tlsIds = controller.getTrafficLightIds();
            System.out.println("TLS count = " + tlsIds.size() + " ids=" + tlsIds);

            for (String tlsId : tlsIds) {
                List<String> lanes = controller.getControlledLanes(tlsId);
                String state = controller.getRedYellowGreenState(tlsId);

                int n = Math.min(lanes.size(), state.length());
                for (int i = 0; i < n; i++) {
                    String laneId = lanes.get(i);
                    TrafficLightBar bar = buildBarFromLane(tlsId, laneId, i);
                    if (bar != null) {
                        bar.setStateChar(state.charAt(i));
                        trafficLightBars.add(bar);
                    }
                }
            }

            mapPanel.setTrafficLights(trafficLightBars);
            statusLabel.setText("Running... (TLS bars=" + trafficLightBars.size() + ")");

        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("TLS load failed: " + ex.getMessage());
        }
    }

    private void updateTrafficLightColors() {
        try {
            Map<String, String> states = new HashMap<>();
            for (TrafficLightBar bar : trafficLightBars) {
                states.computeIfAbsent(bar.tlsId, id -> {
                    try {
                        return controller.getRedYellowGreenState(id);
                    } catch (Exception e) {
                        return "";
                    }
                });

                String s = states.get(bar.tlsId);
                if (bar.indexInState >= 0 && bar.indexInState < s.length()) {
                    bar.setStateChar(s.charAt(bar.indexInState));
                }
            }
        } catch (Exception ignored) {}
    }

    private TrafficLightBar buildBarFromLane(String tlsId, String laneId, int indexInState) {
        try {
            List<Point2D.Double> pts = controller.getLaneShapePoints(laneId);
            if (pts.size() < 2) return null;

            Point2D.Double pPrev = pts.get(pts.size() - 2);
            Point2D.Double pLast = pts.get(pts.size() - 1);

            double dx = pLast.x - pPrev.x;
            double dy = pLast.y - pPrev.y;
            double len = Math.hypot(dx, dy);
            if (len < 1e-9) return null;

            // direction (unit)
            double ux = dx / len;
            double uy = dy / len;

            // perpendicular (unit)
            double nx = -uy;
            double ny = ux;

            // place slightly before end (stopline look)
            double backOffset = 1.0;
            double cx = pLast.x - ux * backOffset;
            double cy = pLast.y - uy * backOffset;

            double barLen = 4.0;
            double half = barLen / 2.0;

            double x1 = cx - nx * half;
            double y1 = cy - ny * half;
            double x2 = cx + nx * half;
            double y2 = cy + ny * half;

            return new TrafficLightBar(tlsId, laneId, indexInState, x1, y1, x2, y2);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void loadTlsDropdown() {
        try {
            tlsDropdown.removeAllItems();
            List<String> tlsIds = controller.getTrafficLightIds(); // die hast du schon
            for (String id : tlsIds) tlsDropdown.addItem(id);

            boolean hasAny = tlsIds != null && !tlsIds.isEmpty();
            tlsDropdown.setEnabled(hasAny);
            phaseMinusBtn.setEnabled(hasAny);
            phasePlusBtn.setEnabled(hasAny);
            hold10Btn.setEnabled(hasAny);

            if (hasAny) {
                tlsDropdown.setSelectedIndex(0);
                refreshSelectedTlsInfo();
            } else {
                tlsInfoLabel.setText("TLS: none");
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            tlsInfoLabel.setText("TLS load failed");
        }
    }

    private String getSelectedTlsId() {
        Object sel = tlsDropdown.getSelectedItem();
        return (sel != null) ? sel.toString() : null;
    }

    private void refreshSelectedTlsInfo() {
        String tlsId = getSelectedTlsId();
        if (tlsId == null || controller == null || !controller.isRunning()) return;

        try {
            int phase = controller.getTlsPhase(tlsId);
            String state = controller.getTlsState(tlsId);
            tlsInfoLabel.setText("TLS " + tlsId + " | phase=" + phase + " | state=" + state);
        } catch (Exception ex) {
            ex.printStackTrace();
            tlsInfoLabel.setText("TLS " + tlsId + " | info error");
        }
    }

    private void changeTlsPhase(int delta) {
        String tlsId = getSelectedTlsId();
        if (tlsId == null || controller == null || !controller.isRunning()) return;

        try {
            int current = controller.getTlsPhase(tlsId);
            int next = Math.max(0, current + delta); // nicht negativ
            controller.setTlsPhase(tlsId, next);

            // sofort UI aktualisieren (Balken werden im nächsten Timer-Tick eh neu gefärbt)
            refreshSelectedTlsInfo();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Phase ändern fehlgeschlagen:\n" + ex.getMessage(),
                    "TLS Control Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void holdTlsForSeconds(double seconds) {
        String tlsId = getSelectedTlsId();
        if (tlsId == null || controller == null || !controller.isRunning()) return;

        try {
            controller.setTlsPhaseDuration(tlsId, seconds);
            refreshSelectedTlsInfo();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Hold fehlgeschlagen:\n" + ex.getMessage(),
                    "TLS Control Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onStressTestClicked(ActionEvent e) {
        try {
            if (controller == null || !controller.isRunning()) {
                JOptionPane.showMessageDialog(this,
                        "Bitte erst die Simulation starten.",
                        "Nicht gestartet",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Routes einmalig laden
            if (cachedRoutes == null || cachedRoutes.isEmpty()) {
                cachedRoutes = controller.getAllRoutes();
                System.out.println("Loaded routes: " + cachedRoutes);
            }

            int created = 0;
            for (int i = 0; i < 50; i++) {
                SumoVehicle v = controller.spawnRandomVehicle(cachedRoutes);
                if (v != null) created++;

                // optional: mini “Entzerrung”, damit SUMO nicht wegen Kollision/Insert-Fails blockt
                controller.step();
            }

            statusLabel.setText("Stresstest: " + created + "/50 Fahrzeuge injiziert");
            updateFromSumo(); // direkt refresh

        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Stresstest failed: " + ex.getMessage());
        }
    }








    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {			//swing im event-thread damit GUI stabil läuft
            GUI viewer = new GUI();					//ERSTELLE GUI UND MACH SICHTBAR
            viewer.setVisible(true);
        });
    }
}



