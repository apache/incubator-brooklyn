package brooklyn.entity.webapp.nodejs;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.util.flags.SetFromFlag;

public interface NodeJsWebAppService extends WebAppService {

    @SetFromFlag("gitRepoUrl")
    ConfigKey<String> APP_GIT_REPOSITORY_URL = ConfigKeys.newStringConfigKey("nodejs.gitRepo.url", "The Git repository where the application is hosted");

    @SetFromFlag("appFileName")
    ConfigKey<String> APP_FILE = ConfigKeys.newStringConfigKey("nodejs.app.fileName", "The NodeJS application file to start", "app.js");

    @SetFromFlag("appName")
    ConfigKey<String> APP_NAME = ConfigKeys.newStringConfigKey("nodejs.app.name", "The name of the NodeJS application");

}
