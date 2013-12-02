package io.brooklyn.camp.brooklyn;

import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;

public class BrooklynCampConstants {

    /* These are only advertised as ConfigKeys currently, as they are not automatically set as sensors. 
     * To fix if EntitySpec allows us to specify sensor values, or there is an automatic way they get converted from config. */
    
    public static final HasConfigKey<String> PLAN_ID = new BasicAttributeSensorAndConfigKey<String>(String.class, "camp.plan.id", 
        "ID supplied in the deployment plan for component to which this entity corresponds (e.g. corresponding to the component template from which this entity was created)");

    public static final HasConfigKey<String> TEMPLATE_ID = new BasicAttributeSensorAndConfigKey<String>(String.class, "camp.template.id", 
        "ID of the component in the CAMP template from which this entity was created");

}
