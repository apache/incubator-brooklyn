package brooklyn.catalog;

import java.util.List;
import javax.annotation.Nonnull;

public interface CatalogItem<T> {
    
    public static enum CatalogItemType {
        TEMPLATE, ENTITY, POLICY, CONFIGURATION
    }

    public static interface CatalogItemContext {
        List<String> getBundles();
    }

    public CatalogItemType getCatalogItemType();
    public Class<T> getCatalogItemJavaType();
    
    public String getId();
    public String getJavaType();
    public String getName();
    public String getDescription();
    public String getIconUrl();
    public String getVersion();

    @Nonnull
    public CatalogItemContext getContext();

    public String toXmlString();

}

