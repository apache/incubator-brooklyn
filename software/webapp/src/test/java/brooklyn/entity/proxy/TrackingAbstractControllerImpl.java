package brooklyn.entity.proxy;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.driver.MockSshDriver;

import com.google.common.collect.Lists;

public class TrackingAbstractControllerImpl extends AbstractControllerImpl implements TrackingAbstractController {
    
    private static final Logger log = LoggerFactory.getLogger(TrackingAbstractControllerImpl.class);

    private final List<Collection<String>> updates = Lists.newCopyOnWriteArrayList();
    
    @Override
    public List<Collection<String>> getUpdates() {
        return updates;
    }
    
    @Override
    public void connectSensors() {
        super.connectSensors();
        setAttribute(SERVICE_UP, true);
    }
    
    @Override
    protected void reconfigureService() {
        log.info("test controller reconfigure, addresses "+serverPoolAddresses);
        if ((!serverPoolAddresses.isEmpty() && updates.isEmpty()) || (!updates.isEmpty() && serverPoolAddresses!=updates.get(updates.size()-1))) {
            updates.add(serverPoolAddresses);
        }
    }

    @Override
    public Class getDriverInterface() {
        return MockSshDriver.class;
    }
    public void reload() {
        // no-op
    }
}
