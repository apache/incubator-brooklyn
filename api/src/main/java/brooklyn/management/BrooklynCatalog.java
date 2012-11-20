package brooklyn.management;

import brooklyn.entity.Entity;
import brooklyn.policy.Policy;

public interface BrooklynCatalog {

    boolean containsPolicy(String policyName);
    Class<? extends Policy> getPolicyClass(String policyTypeName);
    void addPolicyClass(Class<? extends Policy> clazz);

    boolean containsEntity(String entityName);
    Class<? extends Entity> getEntityClass(String entityTypeName);
    void addEntityClass(Class<? extends Entity> clazz);

    Iterable<String> listPolicies();

    Iterable<String> listEntitiesMatching(String name, boolean onlyApps);

}
