package brooklyn.extras.whirr.core

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.Entity
import brooklyn.util.flags.SetFromFlag
import brooklyn.event.basic.BasicConfigKey

public class WhirrRoleImpl extends AbstractEntity implements WhirrRole {

    public WhirrRoleImpl(Map flags = [:], Entity parent = null) {
        super(flags, parent);
    }

    @Override
    public String getRole() {
        return getConfig(ROLE);
    }
}
