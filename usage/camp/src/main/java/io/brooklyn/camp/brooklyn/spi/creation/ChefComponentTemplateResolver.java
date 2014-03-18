package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.spi.AbstractResource;
import brooklyn.entity.Entity;
import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

public class ChefComponentTemplateResolver extends BrooklynComponentTemplateResolver {

    public ChefComponentTemplateResolver(ManagementContext mgmt, ConfigBag attrs, AbstractResource optionalTemplate) {
        super(mgmt, attrs, optionalTemplate);
    }

    @Override
    protected String getJavaType() {
        return ChefEntity.class.getName();
    }
    
    @Override
    protected <T extends Entity> void decorateSpec(EntitySpec<T> spec) {
        if (getDeclaredType().startsWith("chef:")) {
            spec.configure(ChefConfig.CHEF_COOKBOOK_PRIMARY_NAME, Strings.removeFromStart(getDeclaredType(), "chef:"));
        }
        
        super.decorateSpec(spec);
    }
    
}
