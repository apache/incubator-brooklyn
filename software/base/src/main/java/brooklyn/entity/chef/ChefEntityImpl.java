package brooklyn.entity.chef;

import brooklyn.entity.basic.EffectorStartableImpl;
import brooklyn.util.text.Strings;

public class ChefEntityImpl extends EffectorStartableImpl implements ChefEntity {

    public void init() {
        String primaryName = getConfig(CHEF_COOKBOOK_PRIMARY_NAME);
        if (!Strings.isBlank(primaryName)) setDefaultDisplayName(primaryName+" (chef)");
        
        super.init();
        new ChefLifecycleEffectorTasks().attachLifecycleEffectors(this);
    }
    
    
    
}
