import java.awt.Color;

/**
 * Filter für Fahrzeuganzeige auf der Karte.
 * Bei Fehlern wird das Fahrzeug ANGEZEIGT (nicht versteckt).
 */
public class VehicleFilter {

    private boolean enabled = false;
    private Color color;      // Nicht mehr für Filterung verwendet
    private Double minSpeed;
    private Double maxSpeed;
    private String edge;
    
    // Debug-Modus (einmalig ausgeben)
    private boolean debugPrinted = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.debugPrinted = false; // Reset debug bei Änderung
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Double getMinSpeed() {
        return minSpeed;
    }

    public void setMinSpeed(Double minSpeed) {
        this.minSpeed = minSpeed;
    }

    public Double getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(Double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public String getEdge() {
        return edge;
    }

    public void setEdge(String edge) {
        this.edge = edge;
        this.debugPrinted = false; // Reset debug bei Änderung
    }

    /**
     * Prüft ob ein Fahrzeug den Filterkriterien entspricht.
     * @return true wenn das Fahrzeug angezeigt werden soll
     */
    public boolean matches(SumoVehicle v) {
        // Wenn Filter deaktiviert, zeige alles
        if (!enabled) {
            return true;
        }

        // Wenn kein Filterkriterium gesetzt, zeige alles
        if (minSpeed == null && maxSpeed == null && (edge == null || edge.isEmpty())) {
            return true;
        }

        // Geschwindigkeits-Filter
        if (minSpeed != null || maxSpeed != null) {
            try {
                double speed = v.getSpeed();
                
                if (minSpeed != null && speed < minSpeed) {
                    return false;
                }
                
                if (maxSpeed != null && speed > maxSpeed) {
                    return false;
                }
            } catch (Exception e) {
                // Bei Fehler: Fahrzeug trotzdem anzeigen (nicht filtern)
            }
        }

        // Edge-Filter
        if (edge != null && !edge.isEmpty()) {
            try {
                String vehEdge = v.getCurrentEdge();
                
                if (vehEdge == null || vehEdge.isEmpty()) {
                    // Fahrzeug hat keine Edge-Info - trotzdem anzeigen
                    return true;
                }
                
                // Vergleiche Edge-IDs (trim für Sicherheit)
                String filterEdge = edge.trim();
                String vehicleEdge = vehEdge.trim();
                
                // Debug-Ausgabe (nur einmal)
                if (!debugPrinted) {
                    System.out.println("[FILTER DEBUG] Filter-Edge: '" + filterEdge + "'");
                    System.out.println("[FILTER DEBUG] Beispiel Vehicle-Edge: '" + vehicleEdge + "'");
                    System.out.println("[FILTER DEBUG] Equals: " + vehicleEdge.equals(filterEdge));
                    debugPrinted = true;
                }
                
                // EXAKTER Vergleich - das ist was wir eigentlich wollen
                if (vehicleEdge.equals(filterEdge)) {
                    return true;
                }
                
                // Nicht gefiltert = nicht anzeigen
                return false;
                
            } catch (Exception e) {
                // Bei Fehler: Fahrzeug trotzdem anzeigen
                return true;
            }
        }

        return true;
    }

    /**
     * Setzt alle Filter zurück
     */
    public void reset() {
        enabled = false;
        color = null;
        minSpeed = null;
        maxSpeed = null;
        edge = null;
        debugPrinted = false;
    }

    @Override
    public String toString() {
        return "VehicleFilter{" +
                "enabled=" + enabled +
                ", minSpeed=" + minSpeed +
                ", maxSpeed=" + maxSpeed +
                ", edge='" + edge + '\'' +
                '}';
    }
}
