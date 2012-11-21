package brooklyn.catalog.internal;

import brooklyn.policy.Policy;


public class CatalogPolicyItemDto extends AbstractCatalogItem<Policy> {
    
    @Override
    public CatalogItemType getCatalogItemType() {
        return CatalogItemType.POLICY;
    }

    @Override
    public Class<Policy> getCatalogItemJavaType() {
        return Policy.class;
    }

}
