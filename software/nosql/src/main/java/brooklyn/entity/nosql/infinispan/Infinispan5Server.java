package brooklyn.entity.nosql.infinispan;

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.java.UsesJmx;
import brooklyn.event.adapter.FunctionSensorAdapter;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents an Infinispan service
 */
public class Infinispan5Server extends SoftwareProcessEntity implements UsesJmx {
    private static final Logger log = LoggerFactory.getLogger(Infinispan5Server.class);
    
    public static final BasicAttributeSensorAndConfigKey<String> PROTOCOL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "infinispan.server.protocol", 
            "Infinispan protocol (e.g. memcached, hotrod, or websocket)", "memcached");
    
    public static final PortAttributeSensorAndConfigKey PORT = new PortAttributeSensorAndConfigKey(
            "infinispan.server.port", "TCP port number to listen on");

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "5.0.0.CR8");

    public Infinispan5Server() {
        this(MutableMap.of(), null);
    }
    public Infinispan5Server(Map properties) {
        this(properties, null);
    }
    public Infinispan5Server(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public Infinispan5Server(Map properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public Class getDriverInterface() {
        return Infinispan5Driver.class;
    }

    @Override
    protected void connectSensors() {
		super.connectSensors();
		
        FunctionSensorAdapter serviceUpAdapter = sensorRegistry.register(new FunctionSensorAdapter(
                MutableMap.of("period", 10*1000),
                new Callable<Boolean>() {
                        @Override public Boolean call() {
                            return getDriver().isRunning();
                        }}));
    }
}
