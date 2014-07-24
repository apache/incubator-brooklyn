package brooklyn.entity.webapp.nodejs;

import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

@ImplementedBy(NodeJsWebAppServiceImpl.class)
public interface NodeJsWebAppService extends SoftwareProcess, WebAppService {

    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "stable");

    @SetFromFlag("gitRepoUrl")
    ConfigKey<String> APP_GIT_REPOSITORY_URL = ConfigKeys.newStringConfigKey("nodejs.gitRepo.url", "The Git repository where the application is hosted");

    @SetFromFlag("archiveUrl")
    ConfigKey<String> APP_ARCHIVE_URL = ConfigKeys.newStringConfigKey("nodejs.archive.url", "The URL where the application archive is hosted");

    @SetFromFlag("appFileName")
    ConfigKey<String> APP_FILE = ConfigKeys.newStringConfigKey("nodejs.app.fileName", "The NodeJS application file to start", "app.js");

    @SetFromFlag("appName")
    ConfigKey<String> APP_NAME = ConfigKeys.newStringConfigKey("nodejs.app.name", "The name of the NodeJS application");

    @SetFromFlag("appCommand")
    ConfigKey<String> APP_COMMAND = ConfigKeys.newStringConfigKey("nodejs.app.command", "Command to start the NodeJS application", "node");

    @SetFromFlag("nodePackages")
    ConfigKey<List<String>> NODE_PACKAGE_LIST = ConfigKeys.newConfigKey(new TypeToken<List<String>>() { },
            "nodejs.packages", "The NPM packages to install", ImmutableList.<String>of());

    ConfigKey<String> SERVICE_UP_PATH = ConfigKeys.newStringConfigKey("nodejs.serviceUp.path", "Path to use when checking the NodeJS application is running", "/");

    Integer getHttpPort();

}
