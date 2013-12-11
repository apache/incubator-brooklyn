package brooklyn.launcher.camp;

import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatform;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherAbstract;
import io.brooklyn.camp.spi.PlatformRootSummary;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.annotations.Beta;

/** variant of super who also starts a CampServer for convenience */
@Beta
public class BrooklynCampPlatformLauncher extends BrooklynCampPlatformLauncherAbstract {

    @Override
    public BrooklynCampPlatformLauncher launch() {
        assert platform == null;

        mgmt = new LocalManagementContext();        
        BrooklynLauncher.newInstance().managementContext(mgmt).start();
        platform = new BrooklynCampPlatform(
                PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
                mgmt).setConfigKeyAtManagmentContext();
        
        new CampServer(getCampPlatform(), "").start();
        
        return this;
    }
    
    public static void main(String[] args) {
        new BrooklynCampPlatformLauncher().launch();
    }

}
