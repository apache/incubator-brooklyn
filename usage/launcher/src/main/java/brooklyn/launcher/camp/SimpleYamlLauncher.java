package brooklyn.launcher.camp;

import io.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherAbstract;
import io.brooklyn.camp.brooklyn.YamlLauncherAbstract;

import com.google.common.annotations.Beta;

/** convenience for launching YAML files directly */
@Beta
public class SimpleYamlLauncher extends YamlLauncherAbstract {

    @Override
    protected BrooklynCampPlatformLauncherAbstract newPlatformLauncher() {
        return new BrooklynCampPlatformLauncher();
    }

}
