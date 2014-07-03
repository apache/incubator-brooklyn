package brooklyn.catalog.internal;

import io.brooklyn.camp.spi.pdp.DeploymentPlan;

import javax.annotation.Nonnull;

import brooklyn.catalog.CatalogItem;

public abstract class CatalogItemDtoAbstract<T,SpecT> implements CatalogItem<T,SpecT> {

    // TODO are ID and registeredType the same?
    String id;
    String registeredType;
    
    String javaType;
    String name;
    String description;
    String iconUrl;
    String version;
    CatalogLibrariesDto libraries;
    
    String planYaml;
    
    /** @deprecated since 0.7.0.
     * used for backwards compatibility when deserializing.
     * when catalogs are converted to new yaml format, this can be removed. */
    @Deprecated
    String type;
    
    public String getId() {
        if (id!=null) return id;
        return getRegisteredTypeName();
    }
    
    @Override
    public String getRegisteredTypeName() {
        if (registeredType!=null) return registeredType;
        return getJavaType();
    }
    
    public String getJavaType() {
        if (javaType!=null) return javaType;
        if (type!=null) return type;
        return null;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getIconUrl() {
        return iconUrl;
    }

    public String getVersion() {
        return version;
    }

    @Nonnull
    @Override
    public CatalogItemLibraries getLibraries() {
        return getLibrariesDto();
    }

    public CatalogLibrariesDto getLibrariesDto() {
        return libraries;
    }

    @Override
    public String getPlanYaml() {
        return planYaml;
    }

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

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+getId()+"/"+getName()+"]";
    }

    transient CatalogXmlSerializer serializer;
    
    public String toXmlString() {
        if (serializer==null) loadSerializer();
        return serializer.toString(this);
    }
    
    private synchronized void loadSerializer() {
        if (serializer==null) 
            serializer = new CatalogXmlSerializer();
    }

    public abstract Class<SpecT> getSpecType();
    
}
