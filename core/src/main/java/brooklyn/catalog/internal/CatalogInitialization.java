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
package brooklyn.catalog.internal;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.management.ManagementContext;
import brooklyn.management.ManagementContextInjectable;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.FatalRuntimeException;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Urls;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

@Beta
public class CatalogInitialization implements ManagementContextInjectable {

    /*

    A1) if not persisting, go to B1
    A2) if --catalog-reset, delete persisted catalog items
    A3) read persisted catalog items (possibly deleted in A2), go to C1
    A4) go to B1

    B1) look for --catalog-initial, if so read it, then go to C1
    B2) look for BrooklynServerConfig.BROOKLYN_CATALOG_URL, if so, read it, supporting YAML or XML (warning if XML), then go to C1
    B3) look for ~/.brooklyn/catalog.bom, if exists, read it then go to C1
    B4) look for ~/.brooklyn/brooklyn.xml, if exists, warn, read it then go to C1
    B5) read all classpath://brooklyn/default.catalog.bom items, if they exist (and for now they will)
    B6) go to C1

    C1) if --catalog-add, read and add those items

    D1) if persisting, read the rest of the persisted items (entities etc)

     */

    private static final Logger log = LoggerFactory.getLogger(CatalogInitialization.class);
    
    String initialUri;
    boolean reset;
    String additionsUri;
    boolean force;

    boolean disallowLocal = false;
    List<Function<CatalogInitialization, Void>> callbacks = MutableList.of();
    boolean hasRunBestEffort = false, hasRunOfficial = false, isPopulating = false;
    
    ManagementContext managementContext;
    boolean isStartingUp = false;
    boolean failOnStartupErrors = false;
    
    Object mutex = new Object();
    
    public CatalogInitialization(String initialUri, boolean reset, String additionUri, boolean force) {
        this.initialUri = initialUri;
        this.reset = reset;
        this.additionsUri = additionUri;
        this.force = force;
    }
    
    public CatalogInitialization() {
        this(null, false, null, false);
    }

    public void injectManagementContext(ManagementContext managementContext) {
        if (this.managementContext!=null && managementContext!=null && !this.managementContext.equals(managementContext))
            throw new IllegalStateException("Cannot switch management context of "+this+"; from "+this.managementContext+" to "+managementContext);
        this.managementContext = managementContext;
    }
    
    public ManagementContext getManagementContext() {
        return Preconditions.checkNotNull(managementContext, "management context has not been injected into "+this);
    }

    public CatalogInitialization addPopulationCallback(Function<CatalogInitialization, Void> callback) {
        callbacks.add(callback);
        return this;
    }

    public boolean isInitialResetRequested() {
        return reset;
    }

    public boolean hasRunOfficial() { return hasRunOfficial; }
    public boolean hasRunIncludingBestEffort() { return hasRunOfficial || hasRunBestEffort; }

    /** makes or updates the mgmt catalog, based on the settings in this class */
    public void populateCatalog(boolean needsInitial, Collection<CatalogItem<?, ?>> optionalItemsForResettingCatalog) {
        try {
            isPopulating = true;
            synchronized (mutex) {
                BasicBrooklynCatalog catalog = (BasicBrooklynCatalog) managementContext.getCatalog();
                if (!catalog.getCatalog().isLoaded()) {
                    catalog.load();
                } else {
                    if (hasRunOfficial || hasRunBestEffort) {
                        // an indication that something caused it to load early; not severe, but unusual
                        log.warn("Catalog initialization has not properly run but management context has a catalog; re-populating, possibly overwriting items installed during earlier access (it may have been an early web request)");
                        catalog.reset(ImmutableList.<CatalogItem<?,?>>of());
                    }
                }
                hasRunOfficial = true;

                populateCatalog(catalog, needsInitial, true, optionalItemsForResettingCatalog);
            }
        } finally {
            hasRunOfficial = true;
            isPopulating = false;
        }
    }

    private void populateCatalog(BasicBrooklynCatalog catalog, boolean needsInitial, boolean runCallbacks, Collection<CatalogItem<?, ?>> optionalItemsForResettingCatalog) {
        applyCatalogLoadMode();
        
        if (optionalItemsForResettingCatalog!=null) {
            catalog.reset(optionalItemsForResettingCatalog);
        }
        
        if (needsInitial) {
            populateInitial(catalog);
        }
        
        populateAdditions(catalog);

        if (runCallbacks) {
            populateViaCallbacks(catalog);
        }
    }

    private enum PopulateMode { YAML, XML, AUTODETECT }
    
    protected void populateInitial(BasicBrooklynCatalog catalog) {
        if (disallowLocal) {
            if (!hasRunOfficial()) {
                log.debug("CLI initial catalog not being read with disallow-local mode set.");
            }
            return;
        }

//        B1) look for --catalog-initial, if so read it, then go to C1
//        B2) look for BrooklynServerConfig.BROOKLYN_CATALOG_URL, if so, read it, supporting YAML or XML (warning if XML), then go to C1
//        B3) look for ~/.brooklyn/catalog.bom, if exists, read it then go to C1
//        B4) look for ~/.brooklyn/brooklyn.xml, if exists, warn, read it then go to C1
//        B5) read all classpath://brooklyn/default.catalog.bom items, if they exist (and for now they will)
//        B6) go to C1

        if (initialUri!=null) {
            populateInitialFromUri(catalog, initialUri, PopulateMode.AUTODETECT);
            return;
        }
        
        String catalogUrl = managementContext.getConfig().getConfig(BrooklynServerConfig.BROOKLYN_CATALOG_URL);
        if (Strings.isNonBlank(catalogUrl)) {
            populateInitialFromUri(catalog, catalogUrl, PopulateMode.AUTODETECT);
            return;
        }
        
        catalogUrl = Urls.mergePaths(BrooklynServerConfig.getMgmtBaseDir( managementContext.getConfig() ), "catalog.bom");
        if (new File(catalogUrl).exists()) {
            populateInitialFromUri(catalog, "file:"+catalogUrl, PopulateMode.YAML);
            return;
        }
        
        catalogUrl = Urls.mergePaths(BrooklynServerConfig.getMgmtBaseDir( managementContext.getConfig() ), "catalog.xml");
        if (new File(catalogUrl).exists()) {
            populateInitialFromUri(catalog, "file:"+catalogUrl, PopulateMode.XML);
            return;
        }

        // otherwise look for for classpath:/brooklyn/default.catalog.bom --
        // there is one on the classpath which says to scan, and provides a few templates;
        // if one is supplied by user in the conf/ dir that will override the item from the classpath
        // (TBD - we might want to scan for all such bom's?)
        
        catalogUrl = "classpath:/brooklyn/default.catalog.bom";
        if (new ResourceUtils(this).doesUrlExist(catalogUrl)) {
            populateInitialFromUri(catalog, catalogUrl, PopulateMode.YAML);
            return;
        }
        
        log.info("No catalog found on classpath or specified; catalog will not be initialized.");
        return;
    }
    
    private void populateInitialFromUri(BasicBrooklynCatalog catalog, String catalogUrl, PopulateMode mode) {
        log.debug("Loading initial catalog from {}", catalogUrl);

        Exception problem = null;
        Object result = null;
        
        String contents = null;
        try {
            contents = new ResourceUtils(this).getResourceAsString(catalogUrl);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            if (problem==null) problem = e;
        }

        if (contents!=null && (mode==PopulateMode.YAML || mode==PopulateMode.AUTODETECT)) {
            // try YAML first
            try {
                catalog.reset(MutableList.<CatalogItem<?,?>>of());
                result = catalog.addItems(contents);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                if (problem==null) problem = e;
            }
        }
        
        if (result==null && contents!=null && (mode==PopulateMode.XML || mode==PopulateMode.AUTODETECT)) {
            // then try XML
            problem = populateInitialFromUriXml(catalog, catalogUrl, problem, contents);
        }
        
        if (result!=null) {
            log.debug("Loaded initial catalog from {}: {}", catalogUrl, result);
        }
        if (problem!=null) {
            log.warn("Error importing catalog from " + catalogUrl + ": " + problem, problem);
            // TODO inform mgmt of error
        }

    }

    // deprecated XML format
    @SuppressWarnings("deprecation")
    private Exception populateInitialFromUriXml(BasicBrooklynCatalog catalog, String catalogUrl, Exception problem, String contents) {
        CatalogDto dto = null;
        try {
            dto = CatalogDto.newDtoFromXmlContents(contents, catalogUrl);
            problem = null;
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            if (problem==null) problem = e;
        }
        if (dto!=null) {
            catalog.reset(dto);
        }
        return problem;
    }

    boolean hasRunAdditions = false;
    protected void populateAdditions(BasicBrooklynCatalog catalog) {
        if (Strings.isNonBlank(additionsUri)) {
            if (disallowLocal) {
                if (!hasRunAdditions) {
                    log.warn("CLI additions supplied but not supported in disallow-local mode; ignoring.");
                }
                return;
            }   
            if (!hasRunAdditions) {
                log.debug("Adding to catalog from CLI: "+additionsUri+" (force: "+force+")");
            }
            Iterable<? extends CatalogItem<?, ?>> items = catalog.addItems(
                new ResourceUtils(this).getResourceAsString(additionsUri), force);
            
            if (!hasRunAdditions)
                log.debug("Added to catalog from CLI: "+items);
            else
                log.debug("Added to catalog from CLI: count "+Iterables.size(items));
            
            hasRunAdditions = true;
        }
    }

    protected void populateViaCallbacks(BasicBrooklynCatalog catalog) {
        for (Function<CatalogInitialization, Void> callback: callbacks)
            callback.apply(this);
    }

    private boolean setFromCatalogLoadMode = false;

    /** @deprecated since introduced in 0.7.0, only for legacy compatibility with 
     * {@link CatalogLoadMode} {@link BrooklynServerConfig#CATALOG_LOAD_MODE},
     * allowing control of catalog loading from a brooklyn property */
    @Deprecated
    public void applyCatalogLoadMode() {
        if (setFromCatalogLoadMode) return;
        setFromCatalogLoadMode = true;
        Maybe<Object> clmm = ((ManagementContextInternal)managementContext).getConfig().getConfigRaw(BrooklynServerConfig.CATALOG_LOAD_MODE, false);
        if (clmm.isAbsent()) return;
        brooklyn.catalog.CatalogLoadMode clm = TypeCoercions.coerce(clmm.get(), brooklyn.catalog.CatalogLoadMode.class);
        log.warn("Legacy CatalogLoadMode "+clm+" set: applying, but this should be changed to use new CLI --catalogXxx commands");
        switch (clm) {
        case LOAD_BROOKLYN_CATALOG_URL:
            reset = true;
            break;
        case LOAD_BROOKLYN_CATALOG_URL_IF_NO_PERSISTED_STATE:
            // now the default
            break;
        case LOAD_PERSISTED_STATE:
            disallowLocal = true;
            break;
        }
    }

    /** makes the catalog, warning if persistence is on and hasn't run yet 
     * (as the catalog will be subsequently replaced) */
    public void populateBestEffort(BasicBrooklynCatalog catalog) {
        synchronized (mutex) {
            if (hasRunOfficial || hasRunBestEffort || isPopulating) return;
            // if a thread calls back in to this, ie calling to it from a getCatalog() call while populating,
            // it will own the mutex and observe isRunningBestEffort, returning quickly 
            isPopulating = true;
            try {
                if (isStartingUp) {
                    log.warn("Catalog access requested when not yet initialized; populating best effort rather than through recommended pathway. Catalog data may be replaced subsequently.");
                }
                populateCatalog(catalog, true, true, null);
            } finally {
                hasRunBestEffort = true;
                isPopulating = false;
            }
        }
    }

    public void setStartingUp(boolean isStartingUp) {
        this.isStartingUp = isStartingUp;
    }

    public void setFailOnStartupErrors(boolean startupFailOnCatalogErrors) {
        this.failOnStartupErrors = startupFailOnCatalogErrors;
    }

    public void handleException(Throwable throwable, Object details) {
        if (throwable instanceof InterruptedException)
            throw new RuntimeInterruptedException((InterruptedException) throwable);
        if (throwable instanceof RuntimeInterruptedException)
            throw (RuntimeInterruptedException) throwable;

        log.error("Error loading catalog item '"+details+"': "+throwable);
        log.debug("Trace for error loading catalog item '"+details+"': "+throwable, throwable);

        // TODO give more detail when adding
        ((ManagementContextInternal)getManagementContext()).errors().add(throwable);
        
        if (isStartingUp && failOnStartupErrors) {
            throw new FatalRuntimeException("Unable to load catalog item '"+details+"': "+throwable, throwable);
        }
    }
    
}
