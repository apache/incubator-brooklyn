package brooklyn.entity.chef;

import brooklyn.entity.basic.EffectorStartableImpl;

public class ChefEntityImpl extends EffectorStartableImpl implements ChefEntity {

    public void init() {
        super.init();
        new ChefLifecycleEffectorTasks().attachLifecycleEffectors(this);
    }
    
}
