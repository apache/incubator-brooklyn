package brooklyn.demo;

import java.util.List;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.entity.messaging.qpid.QpidBroker;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/** Qpid Broker Application */
public class StandaloneBrokerExample extends AbstractApplication {

    public static final String CUSTOM_CONFIG_PATH = "classpath://custom-config.xml";
    public static final String PASSWD_PATH = "classpath://passwd";
    public static final String QPID_BDBSTORE_JAR_PATH = "classpath://qpid-bdbstore-0.14.jar";
    public static final String BDBSTORE_JAR_PATH = "classpath://je-5.0.34.jar";

    public static final String DEFAULT_LOCATION = "localhost";
    
    @Override
    public void postConstruct() {
        // Configure the Qpid broker entity
    	QpidBroker broker = addChild(EntitySpecs.spec(QpidBroker.class)
    	        .configure("amqpPort", 5672)
    	        .configure("amqpVersion", AmqpServer.AMQP_0_10)
    	        .configure("runtimeFiles", ImmutableMap.builder()
    	                .put(QpidBroker.CONFIG_XML, CUSTOM_CONFIG_PATH)
    	                .put(QpidBroker.PASSWD, PASSWD_PATH)
    	                .put("lib/opt/qpid-bdbstore-0.14.jar", QPID_BDBSTORE_JAR_PATH)
    	                .put("lib/opt/je-5.0.34.jar", BDBSTORE_JAR_PATH)
    	                .build())
    	        .configure("queue", "testQueue"));
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpecs.appSpec(StandaloneBrokerExample.class).displayName("Qpid app"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
