// GUI KLASSE DIE SUMO STARTET; DIE LÄD DIE MAP;zeigt(MapPANEL) wird angeanzeigt;
//start/pausieren der SUMO Simulation (über simulationscontroller)
//und aktualisiert Vahrzeugpositionen import javax.swing.*;
import java.awt.*;        						//LAYOUT,FARBEN etc
import java.awt.event.ActionEvent;				//Button-Klicks ergebnisse
import java.awt.geom.Point2D;					//Koordinaten für positionen
import java.util.ArrayList;						//Listen für Lanes und fahrzeuge
import java.util.List;
import javax.swing.*;							//ALLES FÜR FENSTER,BUTTONS,LABELS, TIMER


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

   
    private List<String> cachedRoutes = new ArrayList<>();					//ROUTEN WERDEN EINMAL GELADEN, dann nie wieder
   
    private final JLabel statusLabel = new JLabel("Ready");					//STATUSANZEIGE im fenster

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
      
      
        String netPath = "/Users/alikandas/Downloads/finalsumo.net.xml";		//straßengeometrie aus net.xml in LAne shape objekte
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

    private void onStartClicked(ActionEvent e) {					//start BUTTON
        try {
            String sumoExe = sumoExeField.getText().trim();
            String cfgPath = cfgField.getText().trim();
            													//EIngabeüberprüfung:ohne phad kann sumo nicht starten
            if (sumoExe.isEmpty() || cfgPath.isEmpty() || cfgPath.equalsIgnoreCase("hier config pfad eingeben")) {
                JOptionPane.showMessageDialog(this,
                        "Bitte Pfad zu sumo-gui und zur .sumocfg angeben.",
                        "Fehlende Eingaben",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            										// Falls controller noch nicht existiert: erstellen
            if (controller == null) {
                controller = new SimulationController(sumoExe, cfgPath);
            }

            											// Falls SUMO noch nicht läuft: starten
            if (!controller.isRunning()) {
                controller.start();
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
    }

    private void updateFromSumo() throws Exception {
        if (controller == null || !controller.isRunning()) return;

        controller.step();										//schritt in SUMO

        List<String> ids = controller.getVehicleIds(); 			//FAHRZEUG ids holen
        System.out.println("vehicles=" + ids.size());

        List<VehicleState> vehStates = new ArrayList<>(ids.size());		//POSITION UM ZU FÄRBEN	
        for (String id : ids) {
            Point2D.Double pos = controller.getVehiclePosition(id);
            vehStates.add(new VehicleState(id, pos.x, pos.y, Color.BLUE));
        }

        mapPanel.setVehicles(vehStates); 			//MapPanel bekommt neue Liste und zeichet neu
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {			//swing im event-thread damit GUI stabil läuft
            GUI viewer = new GUI();					//ERSTELLE GUI UND MACH SICHTBAR
            viewer.setVisible(true);
        });
    }
}



