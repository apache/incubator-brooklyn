package brooklyn.entity.messaging.storm;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface StormDriver extends JavaSoftwareProcessDriver {

    String getJvmOptsLine();
    
}
