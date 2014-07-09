package brooklyn.entity.java;

import brooklyn.entity.basic.SoftwareProcessDriver;

/**
 * A {@link SoftwareProcessDriver} for Java processes.
 */
public interface JavaSoftwareProcessDriver extends SoftwareProcessDriver {

    public boolean isJmxEnabled();
        
}
