package brooklyn.rest.testing.mocks;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.policy.basic.AbstractPolicy;

public class CapitalizePolicy extends AbstractPolicy {

    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        // TODO subscribe to foo and emit an enriched sensor on different channel which is capitalized
    }
    
}
