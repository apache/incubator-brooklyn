package brooklyn.catalog.internal;

import javax.annotation.Nullable;

import brooklyn.catalog.CatalogItem;

public abstract class CatalogItemDtoAbstract<T> implements CatalogItem<T> {
    
    String id;
    String type;
    String name;
    String description;
    String iconUrl;
    CatalogItemContext context;
    
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

    @Override
    public CatalogItemContext getContext() {
        return context;
    }

    public static CatalogTemplateItemDto newTemplate(String type, String name) {
        return newTemplate(null, type, name, null);
    }
    public static CatalogTemplateItemDto newTemplate(String id, String type, String name, String description){
        return newTemplate(id, type, name, description, null);
    }
    public static CatalogTemplateItemDto newTemplate(String id, String type, String name, String description, CatalogContextDto context){
        return set(new CatalogTemplateItemDto(), id, type, name, description, context);
    }

    public static CatalogEntityItemDto newEntity(String type, String name) {
        return newEntity(null, type, name, null);
    }
    public static CatalogEntityItemDto newEntity(String id, String type, String name, String description){
        return newEntity(id, type, name, description, null);
    }
    public static CatalogEntityItemDto newEntity(String id, String type, String name, String description, CatalogContextDto context){
        return set(new CatalogEntityItemDto(), id, type, name, description, context);
    }

    public static CatalogPolicyItemDto newPolicy(String type, String name) {
        return newPolicy(null, type, name, null);
    }
    public static CatalogPolicyItemDto newPolicy(String id, String type, String name, String description){
        return newPolicy(id, type, name, description, null);
    }
    public static CatalogPolicyItemDto newPolicy(String id, String type, String name, String description, CatalogContextDto context){
        return set(new CatalogPolicyItemDto(), id, type, name, description, context);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T extends CatalogItemDtoAbstract> T set(T target, String id, String type, String name,
            String description, CatalogContextDto context) {
        target.id = id;
        target.type = type;
        target.name = name;
        target.description = description;
        target.context = context;
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
