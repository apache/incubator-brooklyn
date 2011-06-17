package brooklyn.event.basic;

import java.util.Comparator;

import brooklyn.entity.Entity;

public class EntityRankers {

    private EntityRankers() {}
    
    public static Comparator<Entity> sensorComparator(final String sensorName) {
        return new Comparator<Entity>() {
            public int compare(Entity a, Entity b) {
            	//FIXME below is bogus;
            	//not all sensors are attributes, and might be nested map etc
            	//should have entity.getSensor or Sensor.getOnEntity
                Object aMetric = a.getAttributes().get(sensorName);
                Object bMetric = b.getAttributes().get(sensorName);
                
                // groovy "spaceship operator":
                // return aMetric <=> bMetric
                
                if (aMetric == null) {
                    return (bMetric != null) ? 1 : 0;
                } else if (bMetric == null) {
                    return -1;
                } else if (aMetric instanceof Comparable) {
                    return ((Comparable) aMetric).compareTo(bMetric);
                } else {
                    throw new IllegalArgumentException("Metric "+sensorName+" not comparable; value is "+aMetric);
                }
            }
        };
    }
}
