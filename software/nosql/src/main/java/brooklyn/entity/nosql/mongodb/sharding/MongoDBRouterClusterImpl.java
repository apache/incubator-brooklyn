package brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;

public class MongoDBRouterClusterImpl extends DynamicClusterImpl implements MongoDBRouterCluster {

    @Override
    public void init() {
        super.init();
        subscribeToChildren(this, MongoDBRouter.RUNNING, new SensorEventListener<Boolean>() {
            public void onEvent(SensorEvent<Boolean> event) {
                setAnyRouter();
            }
        });
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
        AbstractMembershipTrackingPolicy policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Router cluster membership tracker")) {
            @Override
            protected void onEntityAdded(Entity member) {
                setAnyRouter();
            }
            protected void onEntityRemoved(Entity member) {
                setAnyRouter();
            }
            @Override
            protected void onEntityChange(Entity member) {
                setAnyRouter();
            }
        };
        addPolicy(policy);
        policy.setGroup(this);
    }
    
    protected void setAnyRouter() {
        setAttribute(MongoDBRouterCluster.ANY_ROUTER, Iterables.tryFind(getRouters(), new Predicate<MongoDBRouter>() {
            @Override
            public boolean apply(MongoDBRouter input) {
                return input.getAttribute(Startable.SERVICE_UP);
            }}).orNull());
        
        setAttribute(MongoDBRouterCluster.ANY_RUNNING_ROUTER, Iterables.tryFind(getRouters(), new Predicate<MongoDBRouter>() {
            @Override
            public boolean apply(MongoDBRouter input) {
                return input.getAttribute(MongoDBRouter.RUNNING);
            }}).orNull());
    }
    
    @Override
    public Collection<MongoDBRouter> getRouters() {
        return FluentIterable.from(getMembers())
                .transform(new Function<Entity, MongoDBRouter>() {
                    @Override
                    public MongoDBRouter apply(Entity input) {
                        return (MongoDBRouter)input;
                    }
                })
                .toSet();
    }
    
    @Override
    protected EntitySpec<?> getMemberSpec() {
        if (super.getMemberSpec() != null)
            return super.getMemberSpec();
        return EntitySpec.create(MongoDBRouter.class);
    }

    @Override
    public MongoDBRouter getAnyRouter() {
        return getAttribute(MongoDBRouterCluster.ANY_ROUTER);
    }
    
    @Override
    public MongoDBRouter getAnyRunningRouter() {
        return getAttribute(MongoDBRouterCluster.ANY_RUNNING_ROUTER);
    }
 
}
