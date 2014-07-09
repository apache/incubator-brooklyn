package brooklyn.catalog;

import com.google.common.base.Predicate;

public interface BrooklynCatalog {

    /** @return The item with the given ID, or null if not found */
    CatalogItem<?,?> getCatalogItem(String id);


    /** variant of {@link #getCatalogItem(String)} which checks (and casts) type for convenience
     * (returns null if type does not match) */
    <T,SpecT> CatalogItem<T,SpecT> getCatalogItem(Class<T> type, String id);

    /** @return All items in the catalog */
    <T,SpecT> Iterable<CatalogItem<T,SpecT>> getCatalogItems();

    /** convenience for filtering items in the catalog; see CatalogPredicates for useful filters */
    <T,SpecT> Iterable<CatalogItem<T,SpecT>> getCatalogItems(Predicate<? super CatalogItem<T,SpecT>> filter);

    /** @return The classloader which should be used to load classes and entities;
     * this includes all the catalog's classloaders in the right order */
    public ClassLoader getRootClassLoader();

    /** creates a spec for the given catalog item, throwing exceptions if any problems */
    <T,SpecT> SpecT createSpec(CatalogItem<T,SpecT> item);
    
    /** throws exceptions if any problems 
     * @deprecated since 0.7.0 use {@link #createSpec(CatalogItem)} */
    @Deprecated
    <T,SpecT> Class<? extends T> loadClass(CatalogItem<T,SpecT> item);
    /** @deprecated since 0.7.0 use {@link #createSpec(CatalogItem)} */
    @Deprecated
    <T> Class<? extends T> loadClassByType(String typeName, Class<T> typeClass);

    
    /**
     * Adds an item (represented in yaml) to the catalog.
     * 
     * @throws IllegalArgumentException if the yaml was invalid
     */
    CatalogItem<?,?> addItem(String yaml);
    
    /**
     * adds an item to the 'manual' catalog;
     * this does not update the classpath or have a record to the java Class
     *
     * @deprecated since 0.7.0 Construct catalogs with OSGi bundles instead
     */
    @Deprecated
    void addItem(CatalogItem<?,?> item);

    /** creates a catalog item and adds it to the 'manual' catalog,
     * with the corresponding Class definition (loaded by a classloader)
     * registered and available in the classloader.
     * <p>
     * note that the class will be available for this session only,
     * although the record of the item will appear in the catalog DTO if exported,
     * so it is recommended to edit the 'manual' catalog DTO if using it to
     * generate a catalog, either adding the appropriate classpath URL or removing this entry.
     *
     * @deprecated since 0.7.0 Construct catalogs with OSGi bundles instead
     */
    @Deprecated
    CatalogItem<?,?> addItem(Class<?> clazz);

}
