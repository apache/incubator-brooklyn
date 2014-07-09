package brooklyn.extras.whirr.core;

import brooklyn.entity.basic.AbstractEntity;

public class WhirrRoleImpl extends AbstractEntity implements WhirrRole {

    @Override
    public String getRole() {
        return getConfig(ROLE);
    }
}
