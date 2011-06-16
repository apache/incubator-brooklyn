package brooklyn.event;


public class AttributeSensor<T> extends Sensor<T> {
    private static final long serialVersionUID = -7670909215973264600L;

    public AttributeSensor(String description, String name, Class<T> type) {
        super(description, name, type);
    }
    
    public AttributeSensor(String name, Class<T> type) {
        super(name, type);
    }
}
