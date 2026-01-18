import java.awt.Color;

public class VehicleFilter {

    private boolean enabled = false;


    private Color color;
    private Double minSpeed;
    private Double maxSpeed;
    private String edge;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
    }


    public boolean matches(SumoVehicle v) {
        if (!enabled) return true;

        try {
            if (color != null && !color.equals(v.getColor()))
                return false;

            double speed = v.getSpeed();

            if (minSpeed != null && minSpeed > 0 && speed < minSpeed)
                return false;

            if (maxSpeed != null && maxSpeed > 0 && speed > maxSpeed)
                return false;

            if (edge != null && !edge.isBlank()) {
                String vehEdge = v.getCurrentEdge();
                if (vehEdge == null || !vehEdge.equals(edge))
                    return false;
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }
}

