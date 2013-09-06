package brooklyn.catalog.internal;

import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Set;

import javax.annotation.Nullable;

import org.reflections.util.ClasspathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.policy.Policy;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.AggregateClassLoader;
import brooklyn.util.javalang.ReflectionScanner;
import brooklyn.util.javalang.UrlClassLoader;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Time;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;

public class CatalogClasspathDo {

    public static enum CatalogScanningModes {
        /** the classpath is not scanned; 
         * for any catalog which is presented over the internet this is recommended (to prevent loading) and is the default; 
         * (you should explicitly list the items to include; it may be useful to autogenerate it by using a local catalog
         * scanning with ANNOTATIONS, viwing that by running mgmt.getCatalog().toXmlString(),
         * then editting the resulting XML (e.g. setting the classpath and removing the scan attribute) */
        NONE, 
        
        /** types in the classpath are scanned for annotations indicating inclusion in the catalog ({@link Catalog});
         * this is the default if no catalog is supplied, scanning the local classpath */
        ANNOTATIONS,
        
        @Beta
        /** all catalog-friendly types are included, 
         * even if not annotated for inclusion in the catalog; useful for quick hacking,
         * or a classpath (and possibly in future a regex, if added) which is known to have only good things in it;
         * however the precise semantics of what is included is subject to change,
         * and it is strongly recommended to use the {@link Catalog} annotation and scan for annotations 
         * <p>
         * a catalog-friendly type is currently defined as:
         * any concrete non-anonymous (and not a non-static inner) class implementing Entity or Policy;
         * and additionally for entities and applications, an interface with the {@link ImplementedBy} annotation;
         * note that this means classes done "properly" with both an interface and an implementation
         * will be included twice, once as interface and once as implementation;
         * this guarantees inclusion of anything previously included (implementations; 
         * and this will be removed from catalog in future likely),
         * plus things now done properly (which will become the only way in the future)
         **/
        TYPES
    }
    

    private static final Logger log = LoggerFactory.getLogger(CatalogClasspathDo.class);
    
    private final CatalogDo catalog;
    private final CatalogClasspathDto classpath;
    private final CatalogScanningModes scanMode;
    
    boolean isLoaded = false;
    private URL[] urls;
    
    private final AggregateClassLoader classloader = AggregateClassLoader.newInstanceWithNoLoaders();
    private volatile boolean classloaderLoaded = false;

    public CatalogClasspathDo(CatalogDo catalog) {
        this.catalog = Preconditions.checkNotNull(catalog, "catalog");
        this.classpath = catalog.dto.classpath;
        this.scanMode = (classpath != null) ? classpath.scan : null;
    }
    
    /** causes all scanning-based classpaths to scan the classpaths
    * (but does _not_ load all JARs) */
    synchronized void load() {
        if (classpath==null) return;

        if (classpath.getEntries()==null) urls = new URL[0];
        else {
            urls = new URL[classpath.getEntries().size()];
            for (int i=0; i<urls.length; i++) {
                try {
                    urls[i] = new URL(classpath.getEntries().get(i));
                } catch (MalformedURLException e) {
                    log.error("Invalid URL "+classpath.getEntries().get(i)+" in definition of catalog "+catalog+"; skipping catalog");
                    throw Exceptions.propagate(e);
                }
            }
        }
        
        // prefix is supported (but not really used yet) --
        // seems to have _better_ URL-discovery with prefixes 
        // (might also offer regex ? but that is post-load filter as opposed to native optimisation)
        String prefix = null;

        if (scanMode==null || scanMode==CatalogScanningModes.NONE)
            return;
        
        Stopwatch timer = new Stopwatch().start();
        ReflectionScanner scanner = null;
        if (!catalog.isLocal()) {
            log.warn("Scanning not supported for remote catalogs; ignoring scan request in "+catalog);
        } else if (classpath.getEntries()==null || classpath.getEntries().isEmpty()) {
            // no entries; check if we are local, and if so scan the default classpath
            if (!catalog.isLocal()) {
                log.warn("Scanning not supported for remote catalogs; ignoring scan request in "+catalog);
            } else {                
                // scan default classpath:
                ClassLoader baseCL = null;
                Iterable<URL> baseCP = null;
                if (catalog.mgmt instanceof ManagementContextInternal) {
                    baseCL = ((ManagementContextInternal)catalog.mgmt).getBaseClassLoader();
                    baseCP = ((ManagementContextInternal)catalog.mgmt).getBaseClassPathForScanning();
                }
                scanner = new ReflectionScanner(baseCP, prefix, baseCL, catalog.getRootClassLoader());
                if (scanner.getSubTypesOf(Entity.class).isEmpty()) {
                    try {
                        ((ManagementContextInternal)catalog.mgmt).setBaseClassPathForScanning(ClasspathHelper.forJavaClassPath());
                        log.debug("Catalog scan of default classloader returned nothing; reverting to java.class.path");
                        baseCP = ((ManagementContextInternal)catalog.mgmt).getBaseClassPathForScanning();
                        scanner = new ReflectionScanner(baseCP, prefix, baseCL, catalog.getRootClassLoader());
                    } catch (Exception e) {
                        log.info("Catalog scan is empty, and unable to use java.class.path (base classpath is "+baseCP+")");
                        Exceptions.propagateIfFatal(e);
                    }
                }
            }
        } else {
            // scan specified jars:
            scanner = new ReflectionScanner(urls==null || urls.length==0 ? null : Arrays.asList(urls), prefix, getLocalClassLoader()); 
        }
        
        if (scanner!=null) {
            int count = 0, countApps = 0;
            if (scanMode==CatalogScanningModes.ANNOTATIONS) {
                Set<Class<?>> catalogClasses = scanner.getTypesAnnotatedWith(Catalog.class);
                for (Class<?> c: catalogClasses) {
                    try {
                        CatalogItem<?> item = addCatalogEntry(c);
                        count++;
                        if (CatalogTemplateItemDto.class.isInstance(item)) countApps++;
                    } catch (Exception e) {
                        log.warn("Failed to add catalog entry for "+c+"; continuing scan...", e);
                    }
                }
            } else if (scanMode==CatalogScanningModes.TYPES) {
                Iterable<Class<?>> entities = this.excludeInvalidClasses(
                        Iterables.concat(scanner.getSubTypesOf(Entity.class),
                                // not sure why we have to look for sub-types of Application, 
                                // they should be picked up as sub-types of Entity, but in maven builds (only!)
                                // they are not -- i presume a bug in scanner
                                scanner.getSubTypesOf(Application.class), 
                                scanner.getSubTypesOf(ApplicationBuilder.class)));
                for (Class<?> c: entities) {
                    if (Application.class.isAssignableFrom(c) || ApplicationBuilder.class.isAssignableFrom(c)) {
                        addCatalogEntry(new CatalogTemplateItemDto(), c);
                        countApps++;
                    } else {
                        addCatalogEntry(new CatalogEntityItemDto(), c);
                    }
                    count++;
                }
                Iterable<Class<? extends Policy>> policies = this.excludeInvalidClasses(scanner.getSubTypesOf(Policy.class));
                for (Class<?> c: policies) {
                    addCatalogEntry(new CatalogPolicyItemDto(), c);
                    count++;
                }
            } else {
                throw new IllegalStateException("Unsupported catalog scan mode "+scanMode+" for "+this);
            }
            log.debug("Catalog '"+catalog.dto.name+"' classpath scan completed: loaded "+
                    count+" item"+Strings.s(count)+" ("+countApps+" app"+Strings.s(countApps)+") in "+Time.makeTimeStringRounded(timer));
        }
        
        isLoaded = true;
    }

    /** removes inner classes (non-static nesteds) and others; 
     * bear in mind named ones will be hard to instantiate without the outer class instance) */
    private <T> Iterable<Class<? extends T>> excludeInvalidClasses(Iterable<Class<? extends T>> input) {
        Predicate<Class<? extends T>> f = new Predicate<Class<? extends T>>() {
            @Override
            public boolean apply(@Nullable Class<? extends T> input) {
                if (input==null) return false;
                if (input.isLocalClass() || input.isAnonymousClass()) return false;
                if (Modifier.isAbstract(input.getModifiers())) {
                    if (input.getAnnotation(ImplementedBy.class)==null)
                        return false;
                }
                // non-abstract top-level classes are okay
                if (!input.isMemberClass()) return true;
                if (!Modifier.isStatic(input.getModifiers())) return false;
                // nested classes only okay if static
                return true;
            }
        };
        return Iterables.filter(input, f);
    }

    /** augments the given item with annotations and class data for the given class, then adds to catalog */
    public CatalogItem<?> addCatalogEntry(Class<?> c) {
        if (Application.class.isAssignableFrom(c)) return addCatalogEntry(new CatalogTemplateItemDto(), c);
        if (ApplicationBuilder.class.isAssignableFrom(c)) return addCatalogEntry(new CatalogTemplateItemDto(), c);
        if (Entity.class.isAssignableFrom(c)) return addCatalogEntry(new CatalogEntityItemDto(), c);
        if (Policy.class.isAssignableFrom(c)) return addCatalogEntry(new CatalogPolicyItemDto(), c);
        throw new IllegalStateException("Cannot add "+c+" to catalog: unsupported type "+c.getName());
    }
    
    /** augments the given item with annotations and class data for the given class, then adds to catalog 
     */
    public CatalogItem<?> addCatalogEntry(CatalogItemDtoAbstract<?> item, Class<?> c) {
        Catalog annotations = c.getAnnotation(Catalog.class);
        item.type = c.getName();
        item.name = firstNonEmpty(c.getSimpleName(), c.getName());
        if (annotations!=null) {
            item.name = firstNonEmpty(annotations.name(), item.name);
            item.description = firstNonEmpty(annotations.description());
            item.iconUrl = firstNonEmpty(annotations.iconUrl());
        }
        if (log.isTraceEnabled())
            log.trace("adding to catalog: "+c+" (from catalog "+catalog+")");
        catalog.addEntry(item);
        return item;
    }

    private static String firstNonEmpty(String ...candidates) {
        for (String c: candidates)
            if (c!=null && !c.isEmpty()) return c;
        return null;
    }

    /** returns classloader for the entries specified here */
    public ClassLoader getLocalClassLoader() {
        if (!classloaderLoaded) loadLocalClassLoader();
        return classloader;
    }

    protected synchronized void loadLocalClassLoader() {
        if (classloaderLoaded) return;
        if (urls==null) return;
        classloader.addFirst(new UrlClassLoader(urls));
        classloaderLoaded = true;
        return;
    }

    /** adds the given URL as something this classloader will load
     * (however no scanning is done) */
    public void addToClasspath(URL u, boolean updateDto) {
        if (updateDto) classpath.getEntries().add(u.toExternalForm());
        addToClasspath(new UrlClassLoader(u));
    }

    /** adds the given URL as something this classloader will load
     * (however no scanning is done).
     * <p>
     * the DTO will _not_ be updated. */
    public void addToClasspath(ClassLoader loader) {
        classloader.addFirst(loader);
    }

}
