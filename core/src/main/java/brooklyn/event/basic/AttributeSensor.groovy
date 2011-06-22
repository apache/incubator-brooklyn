package brooklyn.event.basic

//@InheritConstructors
public class AttributeSensor<T> extends AbstractSensor<T> {
	private static final long serialVersionUID = -7670909215973264600L;
	public AttributeSensor(String description=name, String name, Class<T> type) { super(description, name, type) }
}
