package brooklyn.extras.whirr.core;

import brooklyn.entity.Entity;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface WhirrRole extends Entity {

    @SetFromFlag("role")
    public static final BasicConfigKey<String> ROLE = new BasicConfigKey<String>(
            String.class, "whirr.instance.role", "Apache Whirr instance role");

    public String getRole();
}
