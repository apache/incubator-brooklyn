package brooklyn.entity.messaging.rabbit;

import java.util.Map;

import brooklyn.entity.basic.ISoftwareProcessEntity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.Beta;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Rabbit MQ broker instance, using AMQP 0-9-1.
 */
public interface RabbitBroker extends ISoftwareProcessEntity, MessageBroker, AmqpServer {

    // FIXME Will not work in proxy-mode because RabbitDestination and RabbitQueue call into RabbitBroker directly
    // (for setting up RabbitQueue's SshFeed). What is the best pattern to use here? 
    
    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "2.8.7");

    @SetFromFlag("erlangVersion")
    public static final BasicConfigKey<String> ERLANG_VERSION = new BasicConfigKey<String>(String.class, "erlang.version", "Erlang runtime version", "R15B");
    
    @SetFromFlag("amqpPort")
    public static final PortAttributeSensorAndConfigKey AMQP_PORT = AmqpServer.AMQP_PORT;

    @SetFromFlag("virtualHost")
    public static final BasicAttributeSensorAndConfigKey<String> VIRTUAL_HOST_NAME = AmqpServer.VIRTUAL_HOST_NAME;

    @SetFromFlag("amqpVersion")
    public static final BasicAttributeSensorAndConfigKey<String> AMQP_VERSION = new BasicAttributeSensorAndConfigKey<String>(
            AmqpServer.AMQP_VERSION, AmqpServer.AMQP_0_9_1);
    
    RabbitQueue createQueue(Map properties);

    // TODO required by RabbitDestination due to close-coupling between that and RabbitBroker; how best to improve?
    @Beta
    Map<String, String> getShellEnvironment();
    
    @Beta
    String getRunDir();
}
