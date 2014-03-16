package brooklyn.entity.group;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.DynamicGroupImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;

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
    private FunctionFeed rescan;

    public void init() {
        super.init();

        Long interval = getConfig(RESCAN_INTERVAL);
        if (interval != null && interval > 0L) {
            rescan = FunctionFeed.builder()
                    .entity(this)
                    .poll(new FunctionPollConfig<Object, Void>(RESCAN)
                            .period(interval, TimeUnit.SECONDS)
                            .callable(new Callable<Void>() {
                                public Void call() throws Exception {
                                    rescanEntities();
                                    return null;
                                }
                            }))
                    .build();
        }
    }

    public void stop() {
        super.stop();
        if (rescan != null) rescan.stop();
    }

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
    protected void onEntityChanged(Entity item) {
        synchronized (memberChangeMutex) {
            super.onEntityChanged(item);
            distributeEntities();
        }
    }

    @Override
    public void rescanEntities() {
        synchronized (memberChangeMutex) {
            super.rescanEntities();
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
