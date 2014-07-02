package brooklyn.entity.basic;

import java.util.Collection;
import java.util.Map;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.location.Location;

import com.google.common.base.Functions;
import com.google.common.base.Supplier;

public class DataEntityImpl extends AbstractEntity implements DataEntity {

    private FunctionFeed feed;

    public DataEntityImpl() { }

    @Override
    public void start(Collection<? extends Location> locations) {
        connectSensors();
        setAttribute(SERVICE_UP, Boolean.TRUE);
    }

    @Override
    public void stop() {
        setAttribute(SERVICE_UP, Boolean.FALSE);
        disconnectSensors();
    }

    @Override
    public void restart() {
        stop();
        start(getLocations());
    }

    protected void connectSensors() {
        FunctionFeed.Builder builder = FunctionFeed.builder()
                .entity(this)
                .period(getConfig(POLL_PERIOD));

        Map<AttributeSensor<?>, Supplier<?>> map = getConfig(SENSOR_SUPPLIER_MAP);
        if (map != null && map.size() > 0) {
            for (Map.Entry<AttributeSensor<?>, Supplier<?>> entry : map.entrySet()) {
                final AttributeSensor sensor = entry.getKey();
                final Supplier supplier = entry.getValue();
                builder.poll(new FunctionPollConfig<Object, Object>(sensor)
                        .supplier(supplier)
                        .onFailureOrException(Functions.constant(null)));
            }
        }

        feed = builder.build();
    }

    protected void disconnectSensors() {
        if (feed != null) feed.stop();
    }
}
