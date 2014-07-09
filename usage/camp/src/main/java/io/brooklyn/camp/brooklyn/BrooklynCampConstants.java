package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.CampPlatform;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;

public class BrooklynCampConstants {

    /* These are only advertised as ConfigKeys currently, as they are not automatically set as sensors. 
     * To fix if EntitySpec allows us to specify sensor values, or there is an automatic way they get converted from config. */
    
    public static final String PLAN_ID_FLAG = "planId";
    public static final HasConfigKey<String> PLAN_ID = new BasicAttributeSensorAndConfigKey<String>(String.class, "camp.plan.id", 
        "Identifier supplied in the deployment plan for component to which this entity corresponds "
        + "(human-readable, for correlating across plan, template, and instance)");

    public static final HasConfigKey<String> TEMPLATE_ID = new BasicAttributeSensorAndConfigKey<String>(String.class, "camp.template.id", 
        "UID of the component in the CAMP template from which this entity was created");

    public static final ConfigKey<CampPlatform> CAMP_PLATFORM = BrooklynServerConfig.CAMP_PLATFORM;

}
