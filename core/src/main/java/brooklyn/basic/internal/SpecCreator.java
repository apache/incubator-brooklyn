package brooklyn.basic.internal;

import brooklyn.catalog.internal.CatalogItemDtoAbstract;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.policy.PolicySpec;

public interface SpecCreator {
    boolean accepts(String mime);
    EntitySpec<?> createEntitySpec(ManagementContext mgmt, String plan, BrooklynClassLoadingContext loader);
    PolicySpec<?> createPolicySpec(ManagementContext mgmt, String plan, BrooklynClassLoadingContext loader);
    CatalogItemDtoAbstract<?, ?> createCatalogItem(ManagementContext mgmt, String plan);
}
