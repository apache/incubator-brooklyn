package brooklyn.event.basic

//@InheritConstructors
public class LogSensor<T> extends AbstractSensor<T> {
    private static final long serialVersionUID = 4713993465669948212L;
	public LogSensor(String description=name, String name, Class<T> type) { super(description, name, type) }
}