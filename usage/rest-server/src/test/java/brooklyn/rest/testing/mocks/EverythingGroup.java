package brooklyn.rest.testing.mocks;

import brooklyn.entity.Group;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(EverythingGroupImpl.class)
public interface EverythingGroup extends Group {

}
