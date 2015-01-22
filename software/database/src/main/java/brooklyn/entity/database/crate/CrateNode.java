package brooklyn.entity.database.crate;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJava;
import brooklyn.entity.java.UsesJavaMXBeans;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(CrateNodeImpl.class)
public interface CrateNode extends SoftwareProcess, UsesJava,UsesJmx, UsesJavaMXBeans {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION,
            "0.45.7");

    @SetFromFlag("downloadUrl")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey(
            Attributes.DOWNLOAD_URL,
            "https://cdn.crate.io/downloads/releases/crate-${version}.tar.gz");

    AttributeSensor<String> MANAGEMENT_URI = Sensors.newStringSensor(
            "crate.managementUri", "The address at which the Crate server listens");

    AttributeSensor<String> SERVER_NAME = Sensors.newStringSensor(
            "crate.serverName", "The name of the server");

    AttributeSensor<Integer> SERVER_STATUS = Sensors.newIntegerSensor(
            "create.serverStatus", "The status of the server");

    AttributeSensor<String> SERVER_BUILD_TIMESTAMP = Sensors.newStringSensor(
            "create.serverBuildTimestamp", "The Timestamp of the server build");
}
