package brooklyn.entity.messaging.rabbit;

import java.util.Map;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface RabbitDriver extends SoftwareProcessDriver {
    
    public void configure();
    
    public Map<String, String> getShellEnvironment();
}
