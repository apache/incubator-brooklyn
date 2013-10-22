package brooklyn.entity.salt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.ConfigToAttributes;

public class SaltStackMasterImpl extends SoftwareProcessImpl implements SaltStackMaster {

    private static final Logger log = LoggerFactory.getLogger(SaltStackMasterImpl.class);
    
    public SaltStackMasterImpl() {
        super();
    }
    
    @Override
    public Class getDriverInterface() {
        return SaltStackMasterDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        
        // TODO what sensors should we poll?
        ConfigToAttributes.apply(this);

        connectServiceUpIsRunning();
    }
    
    @Override
    protected void disconnectSensors() {
        disconnectServiceUpIsRunning();

        super.disconnectSensors();
    }
}
