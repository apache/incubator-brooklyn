package brooklyn.entity.proxy;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class StubAppServer extends AbstractEntity implements Startable {
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    public static final PortAttributeSensorAndConfigKey HTTP_PORT = Attributes.HTTP_PORT;
    public static AtomicInteger nextPort = new AtomicInteger(1234);

    public StubAppServer(Map flags) {
        super(flags);
    }
    
    public StubAppServer(Map flags, Entity parent) {
        super(flags, parent);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        Location location = Iterables.getOnlyElement(locations);
        if (location instanceof MachineProvisioningLocation) {
            startInLocation((MachineProvisioningLocation)location);
        } else {
            startInLocation((MachineLocation)location);
        }
    }

    private void startInLocation(MachineProvisioningLocation loc) {
        try {
            startInLocation(loc.obtain(MutableMap.of()));
        } catch (NoMachinesAvailableException e) {
            throw Throwables.propagate(e);
        }
    }
    
    private void startInLocation(MachineLocation loc) {
        addLocations(ImmutableList.of((Location)loc));
        setAttribute(HOSTNAME, loc.getAddress().getHostName());
        setAttribute(HTTP_PORT, nextPort.getAndIncrement());
        setAttribute(SERVICE_UP, true);
    }

    public void stop() {
        setAttribute(SERVICE_UP, false);
    }
    
    @Override
    public void restart() {
    }
}