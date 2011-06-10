package org.overpaas.activity;

import java.util.Arrays;
import java.util.Comparator;

import org.overpaas.policy.Entity;

public class EntityRankers {

    private EntityRankers() {}
    
    public static Comparator<Entity> newMetricRanker(final String[] keySegments) {
        return new Comparator<Entity>() {
            @Override public int compare(Entity a, Entity b) {
                Object aMetric = a.getMetrics().getRaw(keySegments);
                Object bMetric = b.getMetrics().getRaw(keySegments);
                
                if (aMetric == null) {
                    return (bMetric != null) ? 1 : 0;
                } else if (bMetric == null) {
                    return -1;
                } else if (aMetric instanceof Comparable) {
                    return ((Comparable)aMetric).compareTo(bMetric);
                } else {
                    throw new IllegalArgumentException("Metric "+Arrays.asList(keySegments)+" not comparable; value is "+aMetric);
                }
            }
        };
    }
}
