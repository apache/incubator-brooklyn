package brooklyn.catalog.internal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import brooklyn.catalog.CatalogItem;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Preconditions;

public class CatalogItemDo<T,SpecT> implements CatalogItem<T,SpecT> {

    protected final CatalogDo catalog;
    protected final CatalogItemDtoAbstract<T,SpecT> itemDto;

    protected volatile Class<T> javaClass; 
    
    public CatalogItemDo(CatalogDo catalog, CatalogItem<T,SpecT> itemDto) {
        this.catalog = Preconditions.checkNotNull(catalog, "catalog");
        this.itemDto = (CatalogItemDtoAbstract<T, SpecT>) Preconditions.checkNotNull(itemDto, "itemDto");
    }

    public CatalogItem<T,SpecT> getDto() {
        return itemDto;
    }

    @Override
    public CatalogItemType getCatalogItemType() {
        return itemDto.getCatalogItemType();
    }

    @Override
    public Class<T> getCatalogItemJavaType() {
        return itemDto.getCatalogItemJavaType();
    }

    @Override
    public String getId() {
        return itemDto.getId();
    }

    @Override
    public String getRegisteredTypeName() {
        return itemDto.getRegisteredTypeName();
    }
    
    @Override
    public String getJavaType() {
        return itemDto.getJavaType();
    }

    @Override
    public String getName() {
        return itemDto.getName();
    }

    @Override
    public String getDescription() {
        return itemDto.getDescription();
    }

    @Override
    public String getIconUrl() {
        return itemDto.getIconUrl();
    }

    @Override
    public String getVersion() {
        return itemDto.getVersion();
    }

    @Nonnull
    @Override
    public CatalogItemLibraries getLibraries() {
        return itemDto.getLibraries();
    }

    /** @deprecated since 0.7.0 this is the legacy mechanism; still needed for policies and apps, but being phased out.
     * new items should use {@link #getYaml()} */
    @Deprecated
    public Class<T> getJavaClass() {
        if (javaClass==null) loadJavaClass();
        return javaClass;
    }
    
    @SuppressWarnings("unchecked")
    protected Class<? extends T> loadJavaClass() {
        try {
            if (javaClass!=null) return javaClass;
            
            // TODO use OSGi
            
            javaClass = (Class<T>) catalog.getRootClassLoader().loadClass(getJavaType());
            return javaClass;
        } catch (ClassNotFoundException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public String toString() {
        return getClass().getCanonicalName()+"["+itemDto+"]";
    }

    public String toXmlString() {
        return itemDto.toXmlString();
    }

    public Class<SpecT> getSpecType() {
        return itemDto.getSpecType();
    }

    @Nullable @Override
    public String getPlanYaml() {
        return itemDto.getPlanYaml();
    }
    
}
