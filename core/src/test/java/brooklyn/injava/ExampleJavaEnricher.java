package brooklyn.injava;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.util.flags.SetFromFlag;

/**
 * A toy enricher, for testing that a pure-java implementation works. 
 */
public class ExampleJavaEnricher extends AbstractEnricher {

    @SetFromFlag("myConfig1")
    String myConfig1;

    final List<SensorEvent<?>> eventsReceived = new CopyOnWriteArrayList<SensorEvent<?>>();
    
    public ExampleJavaEnricher() {
        super();
    }
    
    public ExampleJavaEnricher(Map<String,?> flags) {
        super(flags);
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        subscribe(entity, ExampleJavaEntity.MY_SENSOR1, new SensorEventListener<String>() {
            @Override public void onEvent(SensorEvent<String> event) {
                eventsReceived.add(event);
            }
        });
    }
}