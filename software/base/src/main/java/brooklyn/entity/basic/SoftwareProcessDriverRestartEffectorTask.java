package brooklyn.entity.basic;

import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.DynamicTasks;

public class SoftwareProcessDriverRestartEffectorTask extends EffectorBody<Void> {
    
    @Override
    public Void main(ConfigBag parameters) {
        if (((SoftwareProcessImpl)entity()).getDriver() == null) 
            throw new IllegalStateException("entity "+this+" not set up for operations (restart)");
        ((SoftwareProcessImpl)entity()).getDriver().restart();
        new DynamicTasks.AutoQueueVoid("post-restart") { protected void main() {
            ((SoftwareProcessImpl)entity()).postDriverRestart();
        }};
        return null;
    }
}

