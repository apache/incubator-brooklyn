package brooklyn.entity.monit;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.trait.HasShortName;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface MonitNode extends HasShortName {
    // e.g. https://mmonit.com/monit/dist/binary/5.6/monit-5.6-linux-x64.tar.gz
    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new StringAttributeSensorAndConfigKey(
            Attributes.DOWNLOAD_URL, "https://mmonit.com/monit/dist/binary/${version}/monit-${version}-${driver.osTag}.tar.gz");
    
    // NOTE MySQL changes the minor version number of their GA release frequently, check for latest version if install fails
    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "5.6");
    
    @SetFromFlag("controlFileUrl")
    public static final ConfigKey<String> CONTROL_FILE_URL = ConfigKeys.newStringConfigKey("monit.control.url", "URL where monit control (.monitrc) file can be found", "");
    
    @SetFromFlag("daemonIntervalSeconds")
    public static final ConfigKey<Integer> DAEMON_INTERVAL_SECONDS = ConfigKeys.newIntegerConfigKey("monit.daemon.interval", "Interval in seconds on which the daemon will run", 30);
}
