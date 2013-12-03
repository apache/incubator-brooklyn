package brooklyn.launcher;

import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatform;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherAbstract;
import io.brooklyn.camp.spi.PlatformRootSummary;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.annotations.Beta;

/** variant of super who also starts a CampServer for convenience */
@Beta
public class BrooklynCampPlatformLauncher extends BrooklynCampPlatformLauncherAbstract {

    @Override
    public void launch() {
        assert platform == null;

        mgmt = new LocalManagementContext();        
        platform = new BrooklynCampPlatform(
                PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
                mgmt);
        
        new CampServer(getCampPlatform(), "").start();
    }
    
    public static void main(String[] args) {
        new BrooklynCampPlatformLauncher().launch();
    }

}
