package brooklyn.entity.nosql.infinispan;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.java.UsesJmx;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents an Infinispan service
 */
public class Infinispan5Server extends SoftwareProcessImpl implements UsesJmx {
    private static final Logger log = LoggerFactory.getLogger(Infinispan5Server.class);
    
    public static final BasicAttributeSensorAndConfigKey<String> PROTOCOL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "infinispan.server.protocol", 
            "Infinispan protocol (e.g. memcached, hotrod, or websocket)", "memcached");
    
    public static final PortAttributeSensorAndConfigKey PORT = new PortAttributeSensorAndConfigKey(
            "infinispan.server.port", "TCP port number to listen on");

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "5.0.0.CR8");

    // Default filename is "infinispan-${version}-all.zip"
    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://sourceforge.net/projects/infinispan/files/infinispan/${version}/infinispan-${version}-all.zip/download");

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
		super.connectServiceUpIsRunning();
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectServiceUpIsRunning();
        super.disconnectSensors();
    }
}
