package brooklyn.entity.webapp.nodejs;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.util.flags.SetFromFlag;

public interface NodeJsWebAppSoftwareProcess extends SoftwareProcess, NodeJsWebAppService {

    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "stable");

    @SetFromFlag("appUser")
    ConfigKey<String> APP_USER = ConfigKeys.newStringConfigKey("nodejs.app.user", "The user to run the NodeJS application as", "webapp");

}
