package brooklyn.catalog;

import com.google.common.base.Predicate;

public interface BrooklynCatalog {

    /** @return The item with the given ID, or null if not found */
    CatalogItem<?> getCatalogItem(String id);


    /** variant of {@link #getCatalogItem(String)} which checks (and casts) type for convenience
     * (returns null if type does not match) */
    <T> CatalogItem<T> getCatalogItem(Class<T> type, String id);

    /** @return All items in the catalog */
    <T> Iterable<CatalogItem<T>> getCatalogItems();

    /** convenience for filtering items in the catalog; see CatalogPredicates for useful filters */
    <T> Iterable<CatalogItem<T>> getCatalogItems(Predicate<? super CatalogItem<T>> filter);

    /** @return The classloader which should be used to load classes and entities;
     * this includes all the catalog's classloaders in the right order */
    public ClassLoader getRootClassLoader();

    /** throws exceptions if any problems */
    <T> Class<? extends T> loadClass(CatalogItem<T> item);
    <T> Class<? extends T> loadClassByType(String typeName, Class<T> typeClass);

    /**
     * adds an item to the 'manual' catalog;
     * this does not update the classpath or have a record to the java Class
     *
     * @deprecated since 0.7.0 Construct catalogs with OSGi bundles instead
     */
    @Deprecated
    void addItem(CatalogItem<?> item);

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
    CatalogItem<?> addItem(Class<?> clazz);

}
