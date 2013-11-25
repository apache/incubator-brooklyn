package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.brooklyn.spi.creation.BrooklynAssemblyTemplateInstantiator;

import com.google.common.annotations.Beta;

/** convenience for launching YAML files directly */
@Beta
public class YamlLauncherNoServer extends YamlLauncherAbstract {

    @Override
    protected BrooklynCampPlatformLauncherAbstract newPlatformLauncher() {
        return new BrooklynCampPlatformLauncherNoServer();
    }

    public static void main(String[] args) {
        BrooklynAssemblyTemplateInstantiator.TARGET_LOCATION = "localhost";
        
        YamlLauncherNoServer l = new YamlLauncherNoServer();
        
//        l.launchAppYaml("java-web-app-and-db-with-function.yaml");
//        l.launchAppYaml("java-web-app-and-memsql.yaml");
//        l.launchAppYaml("memsql.yaml");
        l.launchAppYaml("playing.yaml");
    }
    
}
