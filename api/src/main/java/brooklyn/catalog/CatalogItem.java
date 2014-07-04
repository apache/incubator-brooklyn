package brooklyn.catalog;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.annotations.Beta;

@Beta
public interface CatalogItem<T,SpecT> {
    
    public static enum CatalogItemType {
        TEMPLATE, ENTITY, POLICY, CONFIGURATION
    }

    public static interface CatalogItemLibraries {
        List<String> getBundles();
    }

    public CatalogItemType getCatalogItemType();
    /** the high-level type of this entity, e.g. Entity (not a specific Entity class) */
    public Class<T> getCatalogItemJavaType();
    /** the type of the spec e.g. EntitySpec corresponding to {@link #getCatalogItemJavaType()} */
    public Class<SpecT> getSpecType();
    
    /** the explicit ID of this item, or the type if not supplied */
    public String getId();
    
    /** the type name registered in the catalog for this item */
    @Nonnull
    public String getRegisteredTypeName();
    
    /** the underlying java type of the item represented, or null if not known (e.g. if it comes from yaml) */
    // TODO references to this should probably query getRegisteredType
    @Nullable public String getJavaType();
    
    public String getName();
    public String getDescription();
    public String getIconUrl();
    public String getVersion();

    @Nonnull
    public CatalogItemLibraries getLibraries();

    public String toXmlString();
    
    /** return underlying YAML for this item, if known */ 
    @Nullable public String getPlanYaml();

}

