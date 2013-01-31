package brooklyn.demo;

import java.util.List;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.entity.messaging.qpid.QpidBroker;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.location.Location;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/** Qpid Broker Application */
public class StandaloneBrokerExample extends ApplicationBuilder {

    public static final String CUSTOM_CONFIG_PATH = "classpath://custom-config.xml";
    public static final String PASSWD_PATH = "classpath://passwd";
    public static final String QPID_BDBSTORE_JAR_PATH = "classpath://qpid-bdbstore-0.14.jar";
    public static final String BDBSTORE_JAR_PATH = "classpath://je-5.0.34.jar";

    public static final String DEFAULT_LOCATION = "localhost";
    
    protected void doBuild() {
        // Configure the Qpid broker entity
    	QpidBroker broker = createChild(BasicEntitySpec.newInstance(QpidBroker.class)
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

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(port)
                .launch();

        Location loc = server.getManagementContext().getLocationRegistry().resolve(location);

        StartableApplication app = new StandaloneBrokerExample()
                .appDisplayName("Qpid app")
                .manage(server.getManagementContext());
        
        app.start(ImmutableList.of(loc));
        
        Entities.dumpInfo(app);
    }
}
