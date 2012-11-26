package brooklyn.catalog.internal;

import brooklyn.catalog.CatalogItem;

public abstract class CatalogItemDtoAbstract<T> implements CatalogItem<T> {
    
    // attributes
    String id;
    String type;
    
    // fields
    String name;
    String description;
    String iconUrl;
    
    public String getId() {
        if (id!=null) return id;
        return type;
    }
    
    public String getJavaType() {
        return type;
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
    
    public static CatalogTemplateItemDto newTemplate(String type, String name) {
        return newTemplate(null, type, name, null);
    }
    public static CatalogTemplateItemDto newTemplate(String id, String type, String name, String description){
        return set(new CatalogTemplateItemDto(), id, type, name, description);
    }

    public static CatalogEntityItemDto newEntity(String type, String name) {
        return newEntity(null, type, name, null);
    }
    public static CatalogEntityItemDto newEntity(String id, String type, String name, String description){
        return set(new CatalogEntityItemDto(), id, type, name, description);
    }

    public static CatalogPolicyItemDto newPolicy(String type, String name) {
        return newPolicy(null, type, name, null);
    }
    public static CatalogPolicyItemDto newPolicy(String id, String type, String name, String description){
        return set(new CatalogPolicyItemDto(), id, type, name, description);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T extends CatalogItemDtoAbstract> T set(T target, String id, String type, String name, String description) {
        target.id = id;
        target.type = type;
        target.name = name;
        target.description = description;
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
    
}
