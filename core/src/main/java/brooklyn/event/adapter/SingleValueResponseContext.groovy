package brooklyn.event.adapter

/**
 * @deprecated See brooklyn.event.feed.*
 */
@Deprecated
public class SingleValueResponseContext extends AbstractSensorEvaluationContext {

	Object value;
	
	@Override
	protected Object getDefaultValue() { value }
	
    @Override
    public String toString() {
        return "value=$value"
    }
}
