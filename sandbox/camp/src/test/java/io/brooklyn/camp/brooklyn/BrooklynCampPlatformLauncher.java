package io.brooklyn.camp.brooklyn;

import com.google.common.annotations.Beta;

/** variant of super who also starts a CampServer for convenience */
@Beta
public class BrooklynCampPlatformLauncher extends BrooklynCampPlatformLauncherAbstract {

    @Override
    public void launchServers() {
        new CampServer(getCampPlatform(), "").start();
    }
    
    public static void main(String[] args) {
        new BrooklynCampPlatformLauncher().launch();
    }

}
