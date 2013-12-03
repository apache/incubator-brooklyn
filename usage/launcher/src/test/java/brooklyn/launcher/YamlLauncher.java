package brooklyn.launcher;

import io.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherAbstract;
import io.brooklyn.camp.brooklyn.YamlLauncherAbstract;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynAssemblyTemplateInstantiator;

import com.google.common.annotations.Beta;

/** convenience for launching YAML files directly */
@Beta
public class YamlLauncher extends YamlLauncherAbstract {

    @Override
    protected BrooklynCampPlatformLauncherAbstract newPlatformLauncher() {
        return new BrooklynCampPlatformLauncher();
    }

    public static void main(String[] args) {
        BrooklynAssemblyTemplateInstantiator.TARGET_LOCATION = "localhost";
        
        YamlLauncher l = new YamlLauncher();
        
//        l.launchAppYaml("java-web-app-and-db-with-function.yaml");
//        l.launchAppYaml("java-web-app-and-memsql.yaml");
//        l.launchAppYaml("memsql.yaml");
        l.launchAppYaml("playing.yaml");
    }
    
}
