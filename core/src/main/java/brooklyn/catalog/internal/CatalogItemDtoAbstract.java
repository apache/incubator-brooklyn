package brooklyn.catalog.internal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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

    @Nullable @Override
    public String getPlanYaml() {
        return planYaml;
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
