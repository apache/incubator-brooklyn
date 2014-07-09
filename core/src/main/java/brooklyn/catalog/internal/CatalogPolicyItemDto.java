package brooklyn.catalog.internal;

import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;


public class CatalogPolicyItemDto extends CatalogItemDtoAbstract<Policy,PolicySpec<?>> {
    
    @Override
    public CatalogItemType getCatalogItemType() {
        return CatalogItemType.POLICY;
    }

    @Override
    public Class<Policy> getCatalogItemJavaType() {
        return Policy.class;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Class<PolicySpec<?>> getSpecType() {
        return (Class)PolicySpec.class;
    }

}
