package brooklyn.event.adapter

public class SingleValueResponseContext extends AbstractSensorEvaluationContext {

	Object value;
	
	@Override
	protected Object getDefaultValue() { value }
	
}
