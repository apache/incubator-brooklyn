package brooklyn.entity.chef;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(ChefEntityImpl.class)
public interface ChefEntity extends SoftwareProcess, ChefConfig {
}
