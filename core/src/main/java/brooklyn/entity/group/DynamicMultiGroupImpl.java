package brooklyn.entity.group;

import java.util.concurrent.ConcurrentMap;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.DynamicGroupImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class DynamicMultiGroupImpl extends DynamicGroupImpl implements DynamicMultiGroup {

    /**
     * Convenience factory method for the common use-case of deriving the bucket directly from a sensor value.
     * @see DynamicMultiGroup#BUCKET_FUNCTION
     */
    public static Function<Entity, String> bucketFromAttribute(final AttributeSensor<?> sensor, final String defaultValue) {
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
    protected void onEntityAdded(Entity item) {
        synchronized (memberChangeMutex) {
            super.onEntityAdded(item);
            distributeEntities();
        }
    }

    @Override
    protected void onEntityRemoved(Entity item) {
        synchronized (memberChangeMutex) {
            super.onEntityRemoved(item);
            distributeEntities();
        }
    }

    @Override
    public void distributeEntities() {
        synchronized (memberChangeMutex) {
            Function<Entity, String> bucketFunction = getConfig(BUCKET_FUNCTION);
            EntitySpec<? extends Group> groupSpec = getConfig(BUCKET_SPEC);
            if (bucketFunction == null) return;
            if (groupSpec == null) return;
    
            for (Entity e : getMembers()) {
                if (!acceptsEntity(e))
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
            for (Group g : ImmutableSet.copyOf(bucketsByName.values())) {
                if (g.getMembers().isEmpty()) {
                    bucketsByName.remove(g.getDisplayName());
                    removeChild(g);
                    Entities.unmanage(g);
                }
            }
        }
    }

}
