package brooklyn.entity.salt;

import java.util.List;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

@ImplementedBy(SaltStackMasterImpl.class)
@Catalog(name="SaltStack Master", description="The Salt master server")
public interface SaltStackMaster extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(BrooklynConfigKeys.SUGGESTED_VERSION, "stable");

    @SetFromFlag("bootstrapUrl")
    ConfigKey<String> BOOTSTRAP_URL = ConfigKeys.newStringConfigKey(
            "salt.bootstrap.url", "The URL that returns the Salt boostrap commands",
            "http://bootstrap.saltstack.org/");

    @SetFromFlag("masterUser")
    ConfigKey<String> MASTER_USER = ConfigKeys.newStringConfigKey(
            "salt.master.user", "The user that runs the Salt master daemon process",
            "root");

    @SetFromFlag("masterConfigTemplate")
    ConfigKey<String> MASTER_CONFIG_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "salt.master.config.templateUrl", "The template for the Salt master configuration (URL)",
            "classpath:///brooklyn/entity/salt/master");

    @SetFromFlag("saltPort")
    PortAttributeSensorAndConfigKey SALT_PORT = new PortAttributeSensorAndConfigKey(
            "salt.port", "Port used for communication between Salt master and minion processes", "4506+");

    @SetFromFlag("publishPort")
    PortAttributeSensorAndConfigKey PUBLISH_PORT = new PortAttributeSensorAndConfigKey(
            "salt.publish.port", "Port used by the Salt master publisher", "4505+");

    @SuppressWarnings("serial")
    AttributeSensor<List<String>> MINION_IDS = new BasicAttributeSensor<List<String>>(new TypeToken<List<String>>() {},
            "salt.minions", "List of Salt minion IDs");

}
