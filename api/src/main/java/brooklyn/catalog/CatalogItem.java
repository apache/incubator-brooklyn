package brooklyn.catalog;

public interface CatalogItem<T> {
    
    public static enum CatalogItemType {
        TEMPLATE, ENTITY, POLICY, CONFIGURATION
    }
    
    public CatalogItemType getCatalogItemType();
    public Class<T> getCatalogItemJavaType();
    
    public String getId();
    public String getJavaType();
    public String getName();
    public String getDescription();
    public String getIconUrl();
    
    public String toXmlString();

}

