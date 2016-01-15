/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.core.catalog.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Arrays;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.mgmt.BrooklynTags;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.javalang.ReflectionScanner;
import org.apache.brooklyn.util.core.javalang.UrlClassLoader;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.AggregateClassLoader;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Time;
import org.apache.commons.lang3.ClassUtils;
import org.reflections.util.ClasspathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
         * then editing the resulting XML (e.g. setting the classpath and removing the scan attribute) */
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
    // TODO this does a Java scan; we also need an OSGi scan which uses the OSGi classloaders when loading for scanning and resolving dependencies 
    synchronized void load() {
        if (classpath == null || isLoaded) return;

        if (classpath.getEntries() == null) {
            urls = new URL[0];
        } else {
            urls = new URL[classpath.getEntries().size()];
            for (int i=0; i<urls.length; i++) {
                try {
                    String u = classpath.getEntries().get(i);
                    if (u.startsWith("classpath:")) {
                        // special support for classpath: url's
                        // TODO put convenience in ResourceUtils for extracting to a normal url
                        // (or see below)
                        InputStream uin = ResourceUtils.create(this).getResourceFromUrl(u);
                        File f = Os.newTempFile("brooklyn-catalog-"+u, null);
                        FileOutputStream fout = new FileOutputStream(f);
                        try {
                            Streams.copy(uin, fout);
                        } finally {
                            Streams.closeQuietly(fout);
                            Streams.closeQuietly(uin);
                        }
                        u = f.toURI().toString();
                    }
                    urls[i] = new URL(u);
                    
                    // TODO potential disk leak above as we have no way to know when the temp file can be removed earlier than server shutdown;
                    // a better way to handle this is to supply a stream handler (but URLConnection is a little bit hard to work with):
//                    urls[i] = new URL(null, classpath.getEntries().get(i)   // (handy construtor for reparsing urls, without splitting into uri first)
//                        , new URLStreamHandler() {
//                            @Override
//                            protected URLConnection openConnection(URL u) throws IOException {
//                                new ResourceUtils(null). ???
//                            }
//                        });
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    log.error("Error loading URL "+classpath.getEntries().get(i)+" in definition of catalog "+catalog+"; skipping definition");
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
        
        Stopwatch timer = Stopwatch.createStarted();
        ReflectionScanner scanner = null;
        if (!catalog.isLocal()) {
            log.warn("Scanning not supported for remote catalogs; ignoring scan request in "+catalog);
        } else if (classpath.getEntries() == null || classpath.getEntries().isEmpty()) {
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
                    baseCP = sanitizeCP(((ManagementContextInternal) catalog.mgmt).getBaseClassPathForScanning());
                    scanner = new ReflectionScanner(baseCP, prefix, baseCL, catalog.getRootClassLoader());
                } catch (Exception e) {
                    log.info("Catalog scan is empty, and unable to use java.class.path (base classpath is "+baseCP+"): "+e);
                    Exceptions.propagateIfFatal(e);
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
                        CatalogItem<?,?> item = addCatalogEntry(c);
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
                
                Iterable<Class<? extends Location>> locations = this.excludeInvalidClasses(scanner.getSubTypesOf(Location.class));
                for (Class<?> c: locations) {
                    addCatalogEntry(new CatalogLocationItemDto(), c);
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

    private Iterable<URL> sanitizeCP(Iterable<URL> baseClassPathForScanning) {
        /*
        If Brooklyn is being run via apache daemon[1], and the classpath contains the contents of an empty folder,
        (e.g. xxx:lib/patch/*:xxx) the classpath will be incorrectly expanded to include a zero-length string
        (e.g. xxx::xxx), which is then interpreted by {@link org.reflections.Reflections#scan} as the root of the
        file system. See [2], line 90+. This needs to be removed, lest we attempt to scan the entire filesystem

        [1]: http://commons.apache.org/proper/commons-daemon/
        [2]: http://svn.apache.org/viewvc/commons/proper/daemon/trunk/src/native/unix/native/arguments.c?view=markup&pathrev=1196468
         */
        Iterables.removeIf(baseClassPathForScanning, new Predicate<URL>() {
            @Override
            public boolean apply(@Nullable URL url) {
                return Strings.isEmpty(url.getFile()) || "/".equals(url.getFile());
            }
        });
        return baseClassPathForScanning;
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

    /** augments the given item with annotations and class data for the given class, then adds to catalog
     * @deprecated since 0.7.0 the classpath DO is replaced by libraries */
    @Deprecated
    public CatalogItem<?,?> addCatalogEntry(Class<?> c) {
        if (Application.class.isAssignableFrom(c)) return addCatalogEntry(new CatalogTemplateItemDto(), c);
        if (ApplicationBuilder.class.isAssignableFrom(c)) return addCatalogEntry(new CatalogTemplateItemDto(), c);
        if (Entity.class.isAssignableFrom(c)) return addCatalogEntry(new CatalogEntityItemDto(), c);
        if (Policy.class.isAssignableFrom(c)) return addCatalogEntry(new CatalogPolicyItemDto(), c);
        if (Location.class.isAssignableFrom(c)) return addCatalogEntry(new CatalogLocationItemDto(), c);
        throw new IllegalStateException("Cannot add "+c+" to catalog: unsupported type "+c.getName());
    }
    
    /** augments the given item with annotations and class data for the given class, then adds to catalog 
     * @deprecated since 0.7.0 the classpath DO is replaced by libraries */
    @Deprecated
    public CatalogItem<?,?> addCatalogEntry(CatalogItemDtoAbstract<?,?> item, Class<?> c) {
        Catalog catalogAnnotation = c.getAnnotation(Catalog.class);
        item.setSymbolicName(c.getName());
        item.setJavaType(c.getName());
        item.setDisplayName(firstNonEmpty(c.getSimpleName(), c.getName()));
        if (catalogAnnotation!=null) {
            item.setDisplayName(firstNonEmpty(catalogAnnotation.name(), item.getDisplayName()));
            item.setDescription(firstNonEmpty(catalogAnnotation.description()));
            item.setIconUrl(firstNonEmpty(catalogAnnotation.iconUrl()));
        }
        if (item instanceof CatalogEntityItemDto || item instanceof CatalogTemplateItemDto) {
            item.tags().addTag(BrooklynTags.newTraitsTag(ClassUtils.getAllInterfaces(c)));
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
