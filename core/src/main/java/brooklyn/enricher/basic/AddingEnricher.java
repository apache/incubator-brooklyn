package brooklyn.enricher.basic;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicSensorEvent;

/** enricher which adds multiple sensors on an entity to produce a new sensor */
public class AddingEnricher extends AbstractEnricher implements SensorEventListener {

    private Sensor[] sources;
    private Sensor<? extends Number> target;

    public AddingEnricher(Sensor sources[], Sensor<? extends Number> target) {
        this.sources = sources;
        this.target = target;
    }

    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        
        for (Sensor source: sources) {
            subscribe(entity, source, this);
            if (source instanceof AttributeSensor) {
                Object value = entity.getAttribute((AttributeSensor)source);
                if (value!=null)
                    onEvent(new BasicSensorEvent(source, entity, value));
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void onEvent(SensorEvent event) {
        Number value = recompute();
        Number typedValue = cast(value, (Class<? extends Number>)target.getType());
        if (target instanceof AttributeSensor) {
            entity.setAttribute((AttributeSensor)target, typedValue);
        } else if (typedValue!=null)
            entity.emit((Sensor)target, typedValue);
    }

    @SuppressWarnings("unchecked")
    public static <V> V cast(Number value, Class<V> type) {
        if (value==null) return null;
        if (type.isInstance(value)) return (V)value;
        
        if (type==Integer.class) return (V) (Integer) (int)Math.round(value.doubleValue());
        if (type==Long.class) return (V) (Long) Math.round(value.doubleValue());
        if (type==Double.class) return (V) (Double) value.doubleValue();
        if (type==Float.class) return (V) (Float) value.floatValue();
        if (type==Byte.class) return (V) (Byte) (byte)Math.round(value.doubleValue());
        if (type==Short.class) return (V) (Short) (short)Math.round(value.doubleValue());
        
        throw new UnsupportedOperationException("conversion of mathematical operation to "+type+" not supported");
    }

    protected Number recompute() {
        if (sources.length==0) return null;
        Double result = 0d;
        for (Sensor source: sources) {
            Object value = entity.getAttribute((AttributeSensor) source);
            if (value==null) return null;
            result += ((Number)value).doubleValue();
        }
        return result;
    }

}
