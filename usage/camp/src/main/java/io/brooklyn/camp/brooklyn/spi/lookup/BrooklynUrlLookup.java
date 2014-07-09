package io.brooklyn.camp.brooklyn.spi.lookup;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.management.ManagementContext;
import brooklyn.util.net.Urls;

public class BrooklynUrlLookup {

    public static ConfigKey<String> BROOKLYN_ROOT_URL = ConfigKeys.newStringConfigKey("brooklyn.root.url");
    
    public static String getUrl(ManagementContext bmc, Entity entity) {
        String root = bmc.getConfig().getConfig(BROOKLYN_ROOT_URL);
        if (root==null) return null;
        return Urls.mergePaths(root, "#/", 
                "/v1/applications/"+entity.getApplicationId()+"/entities/"+entity.getId());
    }

}
