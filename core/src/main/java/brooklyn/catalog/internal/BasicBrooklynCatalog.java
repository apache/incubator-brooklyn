package brooklyn.catalog.internal;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.spi.pdp.DeploymentPlan;

import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.AggregateClassLoader;
import brooklyn.util.javalang.LoadedClassLoader;
import brooklyn.util.stream.Streams;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class BasicBrooklynCatalog implements BrooklynCatalog {

    private static final Logger log = LoggerFactory.getLogger(BasicBrooklynCatalog.class);
    
    private final ManagementContext mgmt;
    private final CatalogDo catalog;
    private volatile CatalogDo manualAdditionsCatalog;
    private volatile LoadedClassLoader manualAdditionsClasses;

    public BasicBrooklynCatalog(ManagementContext mgmt, String catalogUrl) {
        this(mgmt, CatalogDto.newDtoFromUrl(catalogUrl));
    }

    public BasicBrooklynCatalog(final ManagementContext mgmt, final CatalogDto dto) {
        this.mgmt = Preconditions.checkNotNull(mgmt, "managementContext");
        this.catalog = new CatalogDo(mgmt, dto);
    }

    public boolean blockIfNotLoaded(Duration timeout) {
        try {
            return getCatalog().blockIfNotLoaded(timeout);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public CatalogDo getCatalog() {
        return catalog;
    }

    protected CatalogItemDo<?> getCatalogItemDo(String id) {
        return catalog.getCache().get(id);
    }
    
    @Override
    public CatalogItem<?> getCatalogItem(String id) {
        if (id==null) return null;
        CatalogItemDo<?> itemDo = getCatalogItemDo(id);
        if (itemDo==null) return null;
        return itemDo.getDto();
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> CatalogItem<T> getCatalogItem(Class<T> type, String id) {
        if (id==null) return null;
        CatalogItem<?> result = getCatalogItem(id);
        if (type==null || type.isAssignableFrom(result.getCatalogItemJavaType())) 
            return (CatalogItem<T>)result;
        return null;
    }
    
    public ClassLoader getRootClassLoader() {
        return catalog.getRootClassLoader();
    }

    /**
     * Loads this catalog
     */
    public void load() {
        log.debug("Loading catalog for " + mgmt);
        getCatalog().load(mgmt, null);
        if (log.isDebugEnabled()) {
            log.debug("Loaded catalog for " + mgmt + ": " + catalog + "; search classpath is " + catalog.getRootClassLoader());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Class<? extends T> loadClass(CatalogItem<T> item) {
        if (log.isDebugEnabled())
            log.debug("Loading class for catalog item " + item);
        Preconditions.checkNotNull(item);
        CatalogItemDo<?> loadedItem = getCatalogItemDo(item.getId());
        if (loadedItem==null) throw new NoSuchElementException("Unable to load '"+item.getId()+"' to instantiate it");
        return (Class<? extends T>) loadedItem.getJavaClass();
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> Class<? extends T> loadClassByType(String typeName, Class<T> typeClass) {
        Iterable<CatalogItem<Object>> resultL = getCatalogItems(CatalogPredicates.javaType(Predicates.equalTo(typeName)));
        if (Iterables.isEmpty(resultL)) throw new NoSuchElementException("Unable to find catalog item for type "+typeName);
        CatalogItem<Object> resultI = resultL.iterator().next();
        if (log.isDebugEnabled() && Iterables.size(resultL)>1) {
            log.debug("Found "+Iterables.size(resultL)+" matches in catalog for type "+typeName+"; returning the first, "+resultI);
        }
        return (Class<? extends T>) loadClass(resultI);
    }

    @Deprecated
    private <T> CatalogItemDtoAbstract<T> getAbstractCatalogItem(CatalogItem<T> item) {
        while (item instanceof CatalogItemDo) item = ((CatalogItemDo<T>)item).itemDto;
        if (item==null) return null;
        if (item instanceof CatalogItemDtoAbstract) return (CatalogItemDtoAbstract<T>) item;
        throw new IllegalStateException("Cannot unwrap catalog item '"+item+"' (type "+item.getClass()+") to restore DTO");
    }

    private <T> CatalogItemDtoAbstract<T> getAbstractCatalogItem(String yaml) {
        CampPlatform camp = BrooklynServerConfig.getCampPlatform(mgmt).get();
        
        DeploymentPlan plan = camp.pdp().parseDeploymentPlan(Streams.newReaderWithContents(yaml));
        
        // TODO #2 parse brooklyn.catalog metadata, bundles etc.
        // for now take the name from the plan or from a single service type therein
        
        // TODO #3 version info
        
        // TODO #1 build the catalog item from the plan (as CatalogItem<Entity> ?)
//        plan.getName()
        // TODO #2 then support instantiating from the item, replacing 
        
        throw new UnsupportedOperationException();
    }

    @Override
    public CatalogItem<?> addItem(String yaml) {
        log.debug("Adding manual catalog item to "+mgmt+": "+yaml);
        Preconditions.checkNotNull(yaml, "yaml");
        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        CatalogItemDtoAbstract<Object> itemDto = getAbstractCatalogItem(yaml);
        manualAdditionsCatalog.addEntry(itemDto);
        return itemDto;
    }

    @Override @Deprecated
    public void addItem(CatalogItem<?> item) {
        log.debug("Adding manual catalog item to "+mgmt+": "+item);
        Preconditions.checkNotNull(item, "item");
        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        manualAdditionsCatalog.addEntry(getAbstractCatalogItem(item));
    }

    @Override @Deprecated
    public CatalogItem<?> addItem(Class<?> type) {
        log.debug("Adding manual catalog item to "+mgmt+": "+type);
        Preconditions.checkNotNull(type, "type");
        if (manualAdditionsCatalog==null) loadManualAdditionsCatalog();
        manualAdditionsClasses.addClass(type);
        return manualAdditionsCatalog.classpath.addCatalogEntry(type);
    }

    @Deprecated
    private synchronized void loadManualAdditionsCatalog() {
        if (manualAdditionsCatalog!=null) return;
        CatalogDto manualAdditionsCatalogDto = CatalogDto.newNamedInstance(
                "Manual Catalog Additions", "User-additions to the catalog while Brooklyn is running, " +
        		"created "+Time.makeDateString());
        CatalogDo manualAdditionsCatalog = catalog.addCatalog(manualAdditionsCatalogDto);
        if (manualAdditionsCatalog==null) {
            // not hard to support, but slightly messy -- probably have to use ID's to retrieve the loaded instance
            // for now block once, then retry
            log.warn("Blocking until catalog is loaded before changing it");
            boolean loaded = blockIfNotLoaded(Duration.TEN_SECONDS);
            if (!loaded)
                log.warn("Catalog still not loaded after delay; subsequent operations may fail");
            manualAdditionsCatalog = catalog.addCatalog(manualAdditionsCatalogDto);
            if (manualAdditionsCatalog==null) {
                throw new UnsupportedOperationException("Catalogs cannot be added until the base catalog is loaded, and catalog is taking a while to load!");
            }
        }
        
        log.debug("Creating manual additions catalog for "+mgmt+": "+manualAdditionsCatalog);
        manualAdditionsClasses = new LoadedClassLoader();
        ((AggregateClassLoader)manualAdditionsCatalog.classpath.getLocalClassLoader()).addFirst(manualAdditionsClasses);
        
        // expose when we're all done
        this.manualAdditionsCatalog = manualAdditionsCatalog;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T> Iterable<CatalogItem<T>> getCatalogItems() {
        return ImmutableList.copyOf((Iterable)catalog.getCache().values());
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public <T> Iterable<CatalogItem<T>> getCatalogItems(Predicate<? super CatalogItem<T>> filter) {
        Iterable<CatalogItemDo<T>> filtered = Iterables.filter((Iterable)catalog.getCache().values(), (Predicate<CatalogItem<T>>)(Predicate) filter);
        return Iterables.transform(filtered, BasicBrooklynCatalog.<T,T>itemDoToDto());
    }

    @SuppressWarnings({ "unchecked" })
    private static <T2,T> Function<CatalogItemDo<T2>, CatalogItem<T>> itemDoToDto() {
        return new Function<CatalogItemDo<T2>, CatalogItem<T>>() {
            @Override
            public CatalogItem<T> apply(@Nullable CatalogItemDo<T2> item) {
                return (CatalogItem<T>) item.getDto();
            }
        };
    }

    transient CatalogXmlSerializer serializer;
    
    public String toXmlString() {
        if (serializer==null) loadSerializer();
        return serializer.toString(catalog.dto);
    }
    
    private synchronized void loadSerializer() {
        if (serializer==null) 
            serializer = new CatalogXmlSerializer();
    }

}