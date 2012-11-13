package brooklyn.rest.api;

import brooklyn.policy.basic.AbstractPolicy;

public class CatalogPolicySummary {

    private final String name;
    private final String description;
    
    // TODO config? what else?
    
    public CatalogPolicySummary(String name, String description) {
        this.name = name;
        this.description = description;
    }
 
    public static CatalogPolicySummary fromType(Class<? extends AbstractPolicy> policyClass) {
        // TODO description. as annotation?
        return new CatalogPolicySummary(policyClass.getCanonicalName(), null);
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }

}
