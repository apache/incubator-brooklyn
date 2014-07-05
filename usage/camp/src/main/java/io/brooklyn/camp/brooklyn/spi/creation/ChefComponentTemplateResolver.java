package io.brooklyn.camp.brooklyn.spi.creation;

import io.brooklyn.camp.spi.AbstractResource;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

public class ChefComponentTemplateResolver extends BrooklynComponentTemplateResolver {

    public ChefComponentTemplateResolver(BrooklynClassLoadingContext loader, ConfigBag attrs, AbstractResource optionalTemplate) {
        super(loader, attrs, optionalTemplate);
    }

    @Override
    protected String getCatalogIdOrJavaType() {
        return ChefEntity.class.getName();
    }

    // chef: items are not in catalog
    @Override
    public CatalogItem<Entity, EntitySpec<?>> getCatalogItem() {
        return null;
    }
    
    @Override
    protected <T extends Entity> void decorateSpec(EntitySpec<T> spec) {
        if (getDeclaredType().startsWith("chef:")) {
            spec.configure(ChefConfig.CHEF_COOKBOOK_PRIMARY_NAME, Strings.removeFromStart(getDeclaredType(), "chef:"));
        }
        
        super.decorateSpec(spec);
    }
    
}
