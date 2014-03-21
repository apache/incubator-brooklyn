package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;

import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.location.Location;

public class SameServerEntityImpl extends AbstractEntity implements SameServerEntity {

    private static final MachineLifecycleEffectorTasks LIFECYCLE_TASKS = new SameServerDriverLifecycleEffectorTasks();

    @Override
    public void restart() {
        LIFECYCLE_TASKS.restart();
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        checkNotNull(locations, "locations");
        LIFECYCLE_TASKS.start(locations);
    }

    @Override
    public void stop() {
        LIFECYCLE_TASKS.stop();
    }

}
