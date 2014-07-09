package io.brooklyn.camp.brooklyn;

import com.google.common.annotations.Beta;


/** launcher for {@link BrooklynCampPlatform}, which does not start a server (and can live in this project) */
@Beta
public class BrooklynCampPlatformLauncherNoServer extends BrooklynCampPlatformLauncherAbstract {

    public void stopServers() {
        // nothing to do
    }
    
    public static void main(String[] args) {
        new BrooklynCampPlatformLauncherNoServer().launch();
    }
    
}
