package brooklyn.entity.proxying;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.proxying.MyEntity.MyEntityImpl;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.Location;

@ImplementedBy(MyEntityImpl.class)
public interface MyEntity extends Entity, Startable {
    public static class Spec<T extends MyEntity> extends BasicEntitySpec<T> {
        public static Spec<MyEntity> newInstance() {
            return new Spec<MyEntity>(MyEntity.class);
        }
        protected Spec(Class<T> type) {
            super(type);
        }
    }
    
    Effector<String> MY_EFFECTOR = new MethodEffector<String>(MyEntity.class, "myeffector");
    Effector<Entity> CREATE_CHILD_EFFECTOR = new MethodEffector<Entity>(MyEntity.class, "createChild");

    public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;
    
    @Description("My description")
    public String myeffector(@NamedParameter("in") String in);
    
    @Description("My description2")
    public Entity createChild();

    public int getCount();
    
    public static class MyEntityImpl extends AbstractEntity implements MyEntity {
        private AtomicInteger counter = new AtomicInteger(0);

        public MyEntityImpl() {
            super();
        }
    
        @Override
        public String myeffector(String in) {
            return in+"-out";
        }
        
        @Override
        public Entity createChild() {
            MyEntity child = getManagementContext().getEntityManager().createEntity(MyEntity.Spec.newInstance().parent(this));
            getManagementContext().getEntityManager().manage(child);
            return child;
        }

        @Override
        public void start(Collection<? extends Location> locs) {
            LOG.trace("Starting {}", this);
            setAttribute(SERVICE_STATE, Lifecycle.STARTING);
            counter.incrementAndGet();
            // FIXME: Shouldn't need to clear() the locations, but for the dirty workaround implemented in DynamicFabric
            getLocations().clear(); ;
            getLocations().addAll(locs);
            setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
        }

        @Override
        public void stop() { 
            LOG.trace("Stopping {}", this);
            setAttribute(SERVICE_STATE, Lifecycle.STOPPING);
            counter.decrementAndGet();
            setAttribute(SERVICE_STATE, Lifecycle.STOPPED);
        }

        @Override
        public void restart() {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public int getCount() {
            return counter.get();
        }
    }
}
