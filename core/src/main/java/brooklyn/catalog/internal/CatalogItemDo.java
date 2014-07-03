package brooklyn.catalog.internal;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import brooklyn.catalog.CatalogItem;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.OsgiManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;

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

    @Nonnull  // but it is still null sometimes, see in CatalogDo.loadJavaClass
    @Override
    public CatalogItemLibraries getLibraries() {
        return itemDto.getLibraries();
    }

    /** @deprecated since 0.7.0 this is the legacy mechanism; still needed for policies and apps, but being phased out.
     * new items should use {@link #getYaml()} */
    @Deprecated
    public Class<T> getJavaClass() {
        if (javaClass==null) loadJavaClass(null);
        return javaClass;
    }
    
    @SuppressWarnings("unchecked")
    protected Class<? extends T> loadJavaClass(ManagementContext mgmt) {
        Maybe<Class<Object>> clazz = null;
        try {
            if (javaClass!=null) return javaClass;

            if (mgmt!=null) {
                Maybe<OsgiManager> osgi = ((ManagementContextInternal)mgmt).getOsgiManager();
                if (osgi.isPresent() && getLibraries()!=null) {
                    // TODO getLibraries() should never be null but sometimes it is still
                    // e.g. run CatalogResourceTest without the above check
                    List<String> bundles = getLibraries().getBundles();
                    if (bundles!=null && !bundles.isEmpty()) {
                        clazz = osgi.get().tryResolveClass(getJavaType(), bundles);
                        if (clazz.isPresent()) {
                            return (Class<? extends T>) clazz.get();
                        }
                    }
                }
            }
            
            javaClass = (Class<T>) catalog.getRootClassLoader().loadClass(getJavaType());
            return javaClass;
        } catch (Throwable e) {
            Exceptions.propagateIfFatal(e);
            if (clazz!=null) {
                // if OSGi bundles were defined and failed, then prefer to throw its error message
                clazz.get();
            }
            // else throw the java error
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
