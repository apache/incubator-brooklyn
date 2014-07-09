package io.brooklyn.camp.brooklyn;

import com.google.common.annotations.Beta;

/** convenience for launching YAML files directly */
@Beta
public class YamlLauncherNoServer extends YamlLauncherAbstract {

    @Override
    protected BrooklynCampPlatformLauncherAbstract newPlatformLauncher() {
        return new BrooklynCampPlatformLauncherNoServer();
    }

    public static void main(String[] args) {
        YamlLauncherNoServer l = new YamlLauncherNoServer();
        l.setShutdownAppsOnExit(true);
        
        l.launchAppYaml("java-web-app-and-db-with-function.yaml");
    }
    
}
