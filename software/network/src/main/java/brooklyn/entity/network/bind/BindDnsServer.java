package brooklyn.entity.network.bind;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * This sets up a BIND DNS server.
 */
@Catalog(name="BIND", description="BIND is an Internet Domain Name Server.", iconUrl="classpath:///isc-logo.png")
@ImplementedBy(BindDnsServerImpl.class)
public interface BindDnsServer extends SoftwareProcess {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = new BasicConfigKey<String>(
            SoftwareProcess.SUGGESTED_VERSION, "2.3.0");

    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://apache.mirror.anlx.net/karaf/${version}/apache-karaf-${version}.tar.gz");

}
