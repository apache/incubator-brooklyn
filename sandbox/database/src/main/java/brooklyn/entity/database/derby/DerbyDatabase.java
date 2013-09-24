package brooklyn.entity.database.derby;

import java.util.Collection;
import java.util.Map;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.database.Database;
import brooklyn.entity.database.Schema;
import brooklyn.entity.java.UsesJava;
import brooklyn.entity.java.UsesJmx;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * An {@link Entity} that represents a single Derby SQL database server instance.
 *
 * TODO work in progress
 */
public class DerbyDatabase extends SoftwareProcessImpl implements Database, UsesJava, UsesJmx {
    private static final Logger log = LoggerFactory.getLogger(DerbyDatabase.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "10.8.1.2");

    public static final PortAttributeSensorAndConfigKey JDBC_PORT = new PortAttributeSensorAndConfigKey(
            "derby.jdbcPort", "Suggested JDBC port");
    
    public static final ConfigKey<String> VIRTUAL_HOST_NAME = new BasicConfigKey<String>(
            String.class, "derby.virtualHost", "Derby virtual host name", "localhost");

    public static final BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(
            Attributes.JMX_USER, "admin");
    
    public static final BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(
            Attributes.JMX_PASSWORD, "admin");

    @SetFromFlag
    protected Collection<String> schemaNames;
    
    @SetFromFlag
    protected Map<String, DerbySchema> schemas;

    protected transient JmxHelper jmxHelper;
    
    public DerbyDatabase() {
        this(MutableMap.of(), null);
    }
    public DerbyDatabase(Map properties) {
        this(properties, null);
    }
    public DerbyDatabase(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public DerbyDatabase(Map properties, Entity parent) {
        super(properties, parent);

        if (schemaNames == null) schemaNames = Lists.newArrayList();
        if (schemas == null) schemas = Maps.newLinkedHashMap();
    }

    @Override
    public Class<? extends DerbyDatabaseDriver> getDriverInterface() {
        return DerbyDatabaseDriver.class;
    }

    @Override
    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }

    @Override
    public void postStart() {
        super.postStart();
        for (String name : schemaNames) {
            createSchema(name);
        }
    }

    @Override
    public void preStop() {
    	super.preStop();
        for (DerbySchema schema : schemas.values()) {
            schema.destroy();
        }
        if (jmxHelper != null) jmxHelper.disconnect();
    }

    public void createSchema(String name) {
        createSchema(name, ImmutableMap.of());
    }
    
    public void createSchema(String name, Map properties) {
        Map allprops = MutableMap.builder().putAll(properties).put("name", name).build();
        DerbySchema schema = new DerbySchema(allprops);
        schema.init();
        schema.create();
        schemas.put(name, schema);
    }

    public Collection<Schema> getSchemas() {
        return ImmutableList.<Schema>copyOf(schemas.values());
    }
    
    public void addSchema(Schema schema) {
        schemas.put(schema.getName(), (DerbySchema) schema);
	}
    
    public void removeSchema(String schemaName) {
        schemas.remove(schemaName);
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("jdbcPort", getAttribute(JDBC_PORT));
    }

    protected boolean computeNodeUp() {
        // FIXME Use the JmxAdapter.reachable() stuff instead of getAttribute
        try {
            ObjectName serverInfoObjectName = ObjectName.getInstance("org.apache.derby:type=ServerInformation,name=ServerInformation");
            String productVersion = (String) jmxHelper.getAttribute(serverInfoObjectName, "ProductVersion");
            return (productVersion != null && productVersion.equals(getAttribute(Attributes.VERSION)));
        } catch (Exception e) {
            return false;
        }
    }
}
