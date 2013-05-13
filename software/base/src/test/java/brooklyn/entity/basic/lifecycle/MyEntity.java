package brooklyn.entity.basic.lifecycle;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(MyEntityImpl.class)
public interface MyEntity extends SoftwareProcess {

}
