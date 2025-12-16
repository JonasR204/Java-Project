import java.awt.Color;

// Repräsentiert den Zustand eines Fahrzeugs in "Weltkoordinaten".
 
 //  x und y sind im selben Koordinatensystem wie die SUMO-Map (net.xml)
 //  MapPanel kümmert sich nur darum, diese Koordinaten in Pixel umzuwandeln.
 //  Die GUI (oder später der SimulationController) entscheidet,welche Fahrzeuge es gibt und wo sie stehen.
 //
public class VehicleState {

    // Eindeutige ID des Fahrzeugs (z.B. "veh1") 
    public final String id;

    // Aktuelle x-Position im SUMO-Koordinatensystem (Meter) 
    public double x;

    /// Aktuelle y-Position im SUMO-Koordinatensystem (Meter) 
    public double y;

    // Farbe, unter der dieses Fahrzeug gezeichnet wird 
    public Color color;

    
     // Einfacher Konstruktor, um ein Fahrzeug mit Position und Farbe anzulegen.
     
    public VehicleState(String id, double x, double y, Color color) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.color = color;
    }
}
