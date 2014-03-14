package brooklyn.entity.group;

import java.util.concurrent.ConcurrentMap;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

public class DynamicMultiGroupImpl extends AbstractEntity implements DynamicMultiGroup {

    /**
     * Convenience factory method for the common use-case of deriving the bucket directly from a sensor value.
     * @see DynamicMultiGroup#BUCKET_FUNCTION
     */
    public static Function<Entity, String> bucketFromAttribute(final AttributeSensor<String> sensor, final String defaultValue) {
        return new Function<Entity, String>() {
            public String apply(Entity e) {
                Object value = e.getAttribute(sensor);
                return (value == null) ? defaultValue : value.toString();
            };
        };
    }

    /**
     * Convenience factory method for the common use-case of deriving the bucket directly from a sensor value.
     * @see DynamicMultiGroup#BUCKET_FUNCTION
     */
    public static Function<Entity, String> bucketFromAttribute(final AttributeSensor<String> sensor) {
        return bucketFromAttribute(sensor, null);
    }


    private ConcurrentMap<String, Group> bucketsByName = Maps.newConcurrentMap();


    @Override
    public void init() {
        super.init();
        distributeEntities();
    }

    @Override
    public void distributeEntities() {
        Predicate<Entity> entityFilter = getConfig(ENTITY_FILTER);
        Function<Entity, String> bucketFunction = getConfig(BUCKET_FUNCTION);
        EntitySpec<? extends Group> groupSpec = getConfig(BUCKET_SPEC);
        if (entityFilter == null) return;
        if (bucketFunction == null) return;
        if (groupSpec == null) return;

        for (Entity e : getManagementContext().getEntityManager().getEntities()) {
            if (!entityFilter.apply(e))
                continue;

            for (Group g : bucketsByName.values())
                g.removeMember(e);

            String bucketName = bucketFunction.apply(e);
            if (bucketName == null)
                continue;

            Group bucket = bucketsByName.get(bucketName);
            if (bucket == null) {
                bucket = addChild(EntitySpec.create(groupSpec).displayName(bucketName));
                Entities.manage(bucket);
                bucketsByName.put(bucketName, bucket);
            }

            bucket.addMember(e);
        }

        // remove any now-empty buckets
        for (Group g : bucketsByName.values()) {
            if (g.getMembers().isEmpty())
                removeChild(g);
        }
    }

}
