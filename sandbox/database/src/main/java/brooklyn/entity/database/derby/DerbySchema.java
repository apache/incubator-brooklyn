package brooklyn.entity.database.derby;

import static java.lang.String.format;

import java.util.Map;

import javax.management.ObjectName;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.database.Schema;
import brooklyn.event.AttributeSensor;
import brooklyn.event.adapter.JmxHelper;
import brooklyn.event.adapter.JmxObjectNameAdapter;
import brooklyn.event.adapter.JmxSensorAdapter;
import brooklyn.event.adapter.SensorRegistry;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Objects.ToStringHelper;

public class DerbySchema extends AbstractEntity implements Schema {

    // FIXME Needs reviewed and implemented properly; while fixing compilation errors
    // I added enough for it to look mostly plausible but it's completely untested.
    // And I have not looked up the derby docs to check that the attributes etc are valid. 
    
    // TODO Somehow share jmx connection with DerbyDatabase instance
    
    // TODO Declare effectors
    
    public static AttributeSensor<Integer> SCHEMA_DEPTH = new BasicAttributeSensor<Integer>(
            Integer.class, "derby.schema.depth", "schema depth");
    
    public static AttributeSensor<Integer> MESSAGE_COUNT = new BasicAttributeSensor<Integer>(
            Integer.class, "derby.schema.messageCount", "message count");
    
    @SetFromFlag(defaultVal="localhost")
    String virtualHost;
    
    @SetFromFlag(nullable=false)
    String name;

    protected ObjectName virtualHostManager;
    protected ObjectName exchange;

    transient JmxSensorAdapter jmxAdapter;
    transient JmxHelper jmxHelper;
    transient SensorRegistry sensorRegistry;

    public DerbySchema() {
        super(MutableMap.of(), null);
    }
    public DerbySchema(Map properties) {
        super(properties, null);
    }
    public DerbySchema(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public DerbySchema(Map properties, Entity parent) {
        super(properties, parent);
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public DerbyDatabase getParent() {
        return (DerbyDatabase) super.getParent();
    }
    
    /**
     * Return the JDBC connection URL for the schema.
     */
    public String getConnectionUrl() { return String.format("jdbc:derby:%s", name); }

    public void init() {
        try {
            virtualHostManager = new ObjectName(format("org.apache.derby:type=VirtualHost.VirtualHostManager,VirtualHost=\"%s\"", virtualHost));
            exchange = new ObjectName(format("org.apache.derby:type=VirtualHost.Exchange,VirtualHost=\"%s\",name=\"amq.direct\",ExchangeType=direct", virtualHost));
            create();
            
            sensorRegistry = new SensorRegistry(this);
            jmxHelper = new JmxHelper(getParent());
            jmxAdapter = sensorRegistry.register(new JmxSensorAdapter(jmxHelper));
            
            ObjectName schema = new ObjectName(format("org.apache.derby:type=VirtualHost.Schema,VirtualHost=\"%s\",name=\"%s\"", virtualHost, name));
            JmxObjectNameAdapter virtualHostObjectNameAdapter = jmxAdapter.objectName(schema);
            virtualHostObjectNameAdapter.attribute("SchemaDepth").subscribe(SCHEMA_DEPTH);
            virtualHostObjectNameAdapter.attribute("MessageCount").subscribe(MESSAGE_COUNT);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public void create() {
        jmxHelper.operation(virtualHostManager, "createNewSchema", name, getParent().getAttribute(Attributes.JMX_USER), true);
        jmxHelper.operation(exchange, "createNewBinding", name, name);
    }

    public void remove() {
        jmxHelper.operation(exchange, "removeBinding", name, name);
        jmxHelper.operation(virtualHostManager, "deleteSchema", name);
    }

    @Override
    public void destroy() {
        sensorRegistry.close();
        super.destroy();
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("name", name);
    }
}
