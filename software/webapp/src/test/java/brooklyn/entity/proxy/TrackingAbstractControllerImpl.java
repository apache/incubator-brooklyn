package brooklyn.entity.proxy;

import java.util.Collection;
import java.util.List;
import java.util.Set;

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
        Set<String> addresses = getServerPoolAddresses();
        log.info("test controller reconfigure, targets "+addresses);
        if ((!addresses.isEmpty() && updates.isEmpty()) || (!updates.isEmpty() && addresses != updates.get(updates.size()-1))) {
            updates.add(addresses);
        }
    }

    @Override
    public Class getDriverInterface() {
        return MockSshDriver.class;
    }
    
    @Override
    public void reload() {
        // no-op
    }
}
