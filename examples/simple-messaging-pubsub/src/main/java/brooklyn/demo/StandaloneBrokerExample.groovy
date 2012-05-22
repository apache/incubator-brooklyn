package brooklyn.demo

import java.util.List

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.messaging.amqp.AmqpServer
import brooklyn.entity.messaging.qpid.QpidBroker
import brooklyn.location.Location

/** Qpid Broker Application */
public class StandaloneBrokerExample extends AbstractApplication {

    public static final String CUSTOM_CONFIG_PATH = "classpath://custom-config.xml"
    public static final String PASSWD_PATH = "classpath://passwd"
    public static final String QPID_BDBSTORE_JAR_PATH = "classpath://qpid-bdbstore-0.14.jar"
    public static final String BDBSTORE_JAR_PATH = "classpath://je-5.0.34.jar"

    // Configure the Qpid broker entity
	QpidBroker broker = new QpidBroker(this,
	        amqpPort:5672,
	        amqpVersion:AmqpServer.AMQP_0_10,
	        runtimeFiles:[ (QpidBroker.CONFIG_XML):CUSTOM_CONFIG_PATH,
                           (QpidBroker.PASSWD):PASSWD_PATH,
                           ("lib/opt/qpid-bdbstore-0.14.jar"):QPID_BDBSTORE_JAR_PATH,
                           ("lib/opt/je-5.0.34.jar"):BDBSTORE_JAR_PATH ],
	        queue:"testQueue")
}
