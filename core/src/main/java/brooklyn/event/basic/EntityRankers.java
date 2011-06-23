package brooklyn.event.basic;

import java.util.Comparator;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;

public class EntityRankers {

    // FIXME Delete this class or use it?
    
    private EntityRankers() {}
    
    public static <T> Comparator<EntityLocal> sensorComparator(final AttributeSensor<T> sensor) {
        return new Comparator<EntityLocal>() {
            public int compare(EntityLocal a, EntityLocal b) {
            	//FIXME below is bogus;
            	//not all sensors are attributes, and might be nested map etc
            	//should have entity.getSensor or Sensor.getOnEntity
                T aMetric = a.getAttribute(sensor);
                T bMetric = b.getAttribute(sensor);
                
                // groovy "spaceship operator":
                // return aMetric <=> bMetric
                
                if (aMetric == null) {
                    return (bMetric != null) ? 1 : 0;
                } else if (bMetric == null) {
                    return -1;
                } else if (aMetric instanceof Comparable) {
                    return ((Comparable) aMetric).compareTo(bMetric);
                } else {
                    throw new IllegalArgumentException("Metric "+sensor+" not comparable; value is "+aMetric);
                }
            }
        };
    }
}
