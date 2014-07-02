package brooklyn.catalog.internal;

import javax.annotation.Nonnull;

import com.google.common.base.Preconditions;

import brooklyn.catalog.CatalogItem;
import brooklyn.util.exceptions.Exceptions;

public class CatalogItemDo<T,SpecT> implements CatalogItem<T,SpecT> {

    protected final CatalogDo catalog;
    protected final CatalogItem<T,SpecT> itemDto;

    protected volatile Class<T> javaClass; 
    
    public CatalogItemDo(CatalogDo catalog, CatalogItem<T,SpecT> itemDto) {
        this.catalog = Preconditions.checkNotNull(catalog, "catalog");
        this.itemDto = Preconditions.checkNotNull(itemDto, "itemDto");
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

    public Class<T> getJavaClass() {
        if (javaClass==null) loadJavaClass();
        return javaClass;
    }
    
    @SuppressWarnings("unchecked")
    protected Class<? extends T> loadJavaClass() {
        try {
            if (javaClass!=null) return javaClass;
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
    
}
