package brooklyn.entity.webapp.jboss;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(JBoss7ServerImpl.class)
public interface JBoss7Server extends JavaWebAppSoftwareProcess, JavaWebAppService {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "7.1.1.Final");
    // note: 7.1.2.Final fixes many bugs but is not available for download,
    // see https://community.jboss.org/thread/197780
    // 7.2.0.Final should be out during Q3 2012

    @SetFromFlag("bindAddress")
    public static final BasicAttributeSensorAndConfigKey<String> BIND_ADDRESS =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "jboss.bind.address", 
                "Address of interface JBoss should listen on, defaulting 0.0.0.0 (but could set e.g. to attributeWhenReady(HOSTNAME)", 
                "0.0.0.0");

    @SetFromFlag("managementHttpPort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_HTTP_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementHttpPort", "Management port", "9990+");

    /**
     * @deprecated since 1.5; use MANAGEMENT_HTTP_PORT instead
     */
    @SetFromFlag("managementPort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_PORT = MANAGEMENT_HTTP_PORT;

    @SetFromFlag("managementHttpsPort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_HTTPS_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementHttpPort", "Management port", "9443+");

    @SetFromFlag("managementNativePort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_NATIVE_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementNativePort", "Management native port", "10999+");

    @SetFromFlag("portIncrement")
    public static final BasicAttributeSensorAndConfigKey<Integer> PORT_INCREMENT =
            new BasicAttributeSensorAndConfigKey<Integer>(Integer.class, "webapp.jboss.portIncrement", "Port increment, for all ports in config file", 0);

    @SetFromFlag("deploymentTimeout")
    public static final BasicAttributeSensorAndConfigKey<Integer> DEPLOYMENT_TIMEOUT =
            new BasicAttributeSensorAndConfigKey<Integer>(Integer.class, "webapp.jboss.deploymentTimeout", "Deployment timeout, in seconds", 600);
    
    public static final BasicAttributeSensorAndConfigKey<String> TEMPLATE_CONFIGURATION_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "webapp.jboss.templateConfigurationUrl", "Template file (in freemarker format) for the standalone.xml file", 
            "classpath://brooklyn/entity/webapp/jboss/jboss7-standalone.xml");

    public static final BasicAttributeSensor<Integer> MANAGEMENT_STATUS =
            new BasicAttributeSensor<Integer>(Integer.class, "webapp.jboss.managementStatus", "HTTP response code for the management server");
}
