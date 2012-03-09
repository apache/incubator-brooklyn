package brooklyn.extras.whirr.core

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.Entity
import brooklyn.util.flags.SetFromFlag
import brooklyn.event.basic.BasicConfigKey

class WhirrRole extends AbstractEntity {

    @SetFromFlag("role")
    public static final BasicConfigKey<String> ROLE =
        [String, "whirr.instance.role", "Apache Whirr instance role"]

    public WhirrRole(Map flags = [:], Entity owner = null) {
        super(flags, owner)
    }

    public String getRole() {
        return getConfig(ROLE)
    }
}
