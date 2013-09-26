package brooklyn.entity.monitoring.monit;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

@Catalog(name="Monit Node", description="Monit is a free open source utility for managing and monitoring, processes, programs, files, directories and filesystems on a UNIX system")
@ImplementedBy(MonitNodeImpl.class)
public interface MonitNode extends SoftwareProcess, HasShortName {
    // e.g. https://mmonit.com/monit/dist/binary/5.6/monit-5.6-linux-x64.tar.gz
    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new StringAttributeSensorAndConfigKey(
            Attributes.DOWNLOAD_URL, "https://mmonit.com/monit/dist/binary/${version}/monit-${version}-${driver.osTag}.tar.gz");
    
    // NOTE MySQL changes the minor version number of their GA release frequently, check for latest version if install fails
    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "5.6");
    
    @SetFromFlag("controlFileUrl")
    public static final ConfigKey<String> CONTROL_FILE_URL = ConfigKeys.newStringConfigKey("monit.control.url", "URL where monit control (.monitrc) file can be found", "");

    public static final ConfigKey<Map<String, Object>> CONTROL_FILE_SUBSTITUTIONS = ConfigKeys.newConfigKey(new TypeToken<Map<String, Object>>(){}, "monit.control.substitutions", 
        "Additional substitutions to be used in the control file template", ImmutableMap.<String, Object>of());
    
    public static final AttributeSensor<String> MONIT_TARGET_PROCESS_NAME = Sensors.newStringSensor("monit.target.process.name");
    
    public static final AttributeSensor<String> MONIT_TARGET_PROCESS_STATUS = Sensors.newStringSensor("monit.target.process.status");
}
