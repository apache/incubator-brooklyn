package brooklyn.catalog;

import com.google.common.base.Predicate;

public interface BrooklynCatalog {

    /** returns null if not found */
    CatalogItem<?> getCatalogItem(String id);
    
    /** returns null if not found; throws if wrong type */
    <T> CatalogItem<T> getCatalogItem(Class<T> type, String id);
    
    /** returns the classloader which should be used to load classes and entities;
     * this includes all the catalog's classloaders in the right order */
    public ClassLoader getRootClassLoader();
    
    /** throws exceptions if any problems */
    <T> Class<? extends T> loadClass(CatalogItem<T> item);
    <T> Class<? extends T> loadClassByType(String typeName, Class<T> typeClass);
    
    
    <T> Iterable<CatalogItem<T>> findMatching(Predicate<? super CatalogItem<T>> filter);

    /** adds an item to the 'manual' catalog;
     * callers of this method will often also need to {@link #addToClasspath(String)} or {@link #addToClasspath(ClassLoader)} */
    void addItem(CatalogItem<?> item);
    /** adds a classpath entry which will be used by the 'manual' catalog */
    void addToClasspath(ClassLoader loader);
    /** adds a classpath entry which will be used by the 'manual' catalog */
    void addToClasspath(String url);
    
    /** creates a catalog item and adds it to the 'manual' catalog.
     * the class will be available for this session only.
     * however the record of the item will appear in the catalog DTO,
     * so it is recommended to edit the 'manual' catalog DTO if using it to
     * generate a catalog (ie add the appropriate classpath URL). */
    CatalogItem<?> addItem(Class<?> clazz);

}
