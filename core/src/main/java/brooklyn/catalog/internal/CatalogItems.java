package brooklyn.catalog.internal;

import io.brooklyn.camp.spi.pdp.DeploymentPlan;

/** Deliberately package-private. Only for internal use. */
class CatalogItems {

    public static CatalogTemplateItemDto newTemplateFromJava(String javaType, String name) {
        return newTemplateFromJava(null, javaType, name, null);
    }
    public static CatalogTemplateItemDto newTemplateFromJava(String id, String javaType, String name, String description) {
        return newTemplateFromJava(id, javaType, name, description, null);
    }
    public static CatalogTemplateItemDto newTemplateFromJava(String id, String javaType, String name, String description, CatalogLibrariesDto libraries) {
        return set(new CatalogTemplateItemDto(), id, javaType, javaType, name, description, libraries);
    }

    public static CatalogEntityItemDto newEntityFromPlan(String registeredTypeName, CatalogLibrariesDto libraries, DeploymentPlan plan, String underlyingPlanYaml) {
        CatalogEntityItemDto target = set(new CatalogEntityItemDto(), null, registeredTypeName, null, plan.getName(), plan.getDescription(), libraries);
        target.planYaml = underlyingPlanYaml;
        return target;
    }
    
    public static CatalogEntityItemDto newEntityFromJava(String javaType, String name) {
        return newEntityFromJava(null, javaType, name, null);
    }
    public static CatalogEntityItemDto newEntityFromJava(String id, String javaType, String name, String description) {
        return newEntityFromJava(id, javaType, name, description, null);
    }
    public static CatalogEntityItemDto newEntityFromJava(String id, String javaType, String name, String description, CatalogLibrariesDto libraries) {
        return set(new CatalogEntityItemDto(), id, javaType, javaType, name, description, libraries);
    }

    public static CatalogPolicyItemDto newPolicyFromJava(String javaType, String name) {
        return newPolicyFromJava(null, javaType, name, null);
    }
    public static CatalogPolicyItemDto newPolicyFromJava(String id, String javaType, String name, String description) {
        return newPolicyFromJava(id, javaType, name, description, null);
    }
    public static CatalogPolicyItemDto newPolicyFromJava(String id, String javaType, String name, String description, CatalogLibrariesDto libraries) {
        return set(new CatalogPolicyItemDto(), id, javaType, javaType, name, description, libraries);
    }
 
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T extends CatalogItemDtoAbstract> T set(T target, String id, String registeredType, String javaType, String name,
            String description, CatalogLibrariesDto libraries) {
        target.id = id;
        target.registeredType = registeredType;
        target.javaType = javaType;
        target.name = name;
        target.description = description;
        target.libraries = libraries != null ? libraries : new CatalogLibrariesDto();
        return target;
    }

}
