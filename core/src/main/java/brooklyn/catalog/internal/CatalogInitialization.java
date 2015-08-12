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
import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.ha.ManagementNodeState;

import brooklyn.config.BrooklynServerConfig;
import brooklyn.management.ManagementContextInjectable;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.FatalRuntimeException;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
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
    A3) if there is a persisted catalog (and it wasn't not deleted by A2), read it and go to C1
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
    
    private String initialUri;
    private boolean reset;
    private String additionsUri;
    private boolean force;

    private boolean disallowLocal = false;
    private List<Function<CatalogInitialization, Void>> callbacks = MutableList.of();
    private boolean 
        /** has run an unofficial initialization (i.e. an early load, triggered by an early read of the catalog) */
        hasRunUnofficialInitialization = false, 
        /** has run an official initialization, but it is not a permanent one (e.g. during a hot standby mode, or a run failed) */
        hasRunTransientOfficialInitialization = false, 
        /** has run an official initialization which is permanent (node is master, and the new catalog is now set) */
        hasRunFinalInitialization = false;
    /** is running a populate method; used to prevent recursive loops */
    private boolean isPopulating = false;
    
    private ManagementContext managementContext;
    private boolean isStartingUp = false;
    private boolean failOnStartupErrors = false;
    
    private Object populatingCatalogMutex = new Object();
    
    public CatalogInitialization(String initialUri, boolean reset, String additionUri, boolean force) {
        this.initialUri = initialUri;
        this.reset = reset;
        this.additionsUri = additionUri;
        this.force = force;
    }
    
    public CatalogInitialization() {
        this(null, false, null, false);
    }

    @Override
    public void injectManagementContext(ManagementContext managementContext) {
        Preconditions.checkNotNull(managementContext, "management context");
        if (this.managementContext!=null && managementContext!=this.managementContext)
            throw new IllegalStateException("Cannot switch management context, from "+this.managementContext+" to "+managementContext);
        this.managementContext = managementContext;
    }
    
    /** Called by the framework to set true while starting up, and false afterwards,
     * in order to assist in appropriate logging and error handling. */
    public void setStartingUp(boolean isStartingUp) {
        this.isStartingUp = isStartingUp;
    }

    public void setFailOnStartupErrors(boolean startupFailOnCatalogErrors) {
        this.failOnStartupErrors = startupFailOnCatalogErrors;
    }

    public CatalogInitialization addPopulationCallback(Function<CatalogInitialization, Void> callback) {
        callbacks.add(callback);
        return this;
    }
    
    public ManagementContext getManagementContext() {
        return Preconditions.checkNotNull(managementContext, "management context has not been injected into "+this);
    }

    public boolean isInitialResetRequested() {
        return reset;
    }

    /** Returns true if the canonical initialization has completed, 
     * that is, an initialization which is done when a node is rebinded as master
     * (or an initialization done by the startup routines when not running persistence);
     * see also {@link #hasRunAnyInitialization()}. */
    public boolean hasRunFinalInitialization() { return hasRunFinalInitialization; }
    /** Returns true if an official initialization has run,
     * even if it was a transient run, e.g. so that the launch sequence can tell whether rebind has triggered initialization */
    public boolean hasRunOfficialInitialization() { return hasRunFinalInitialization || hasRunTransientOfficialInitialization; }
    /** Returns true if the initializer has run at all,
     * including transient initializations which might be needed before a canonical becoming-master rebind,
     * for instance because the catalog is being accessed before loading rebind information
     * (done by {@link #populateUnofficial(BasicBrooklynCatalog)}) */
    public boolean hasRunAnyInitialization() { return hasRunFinalInitialization || hasRunTransientOfficialInitialization || hasRunUnofficialInitialization; }

    /** makes or updates the mgmt catalog, based on the settings in this class 
     * @param nodeState the management node for which this is being read; if master, then we expect this run to be the last one,
     *   and so subsequent applications should ignore any initialization data (e.g. on a subsequent promotion to master, 
     *   after a master -> standby -> master cycle)
     * @param needsInitialItemsLoaded whether the catalog needs the initial items loaded
     * @param needsAdditionalItemsLoaded whether the catalog needs the additions loaded
     * @param optionalExcplicitItemsForResettingCatalog
     *   if supplied, the catalog is reset to contain only these items, before calling any other initialization
     *   for use primarily when rebinding
     */
    public void populateCatalog(ManagementNodeState nodeState, boolean needsInitialItemsLoaded, boolean needsAdditionsLoaded, Collection<CatalogItem<?, ?>> optionalExcplicitItemsForResettingCatalog) {
        if (log.isDebugEnabled()) {
            String message = "Populating catalog for "+nodeState+", needsInitial="+needsInitialItemsLoaded+", needsAdditional="+needsAdditionsLoaded+", explicitItems="+(optionalExcplicitItemsForResettingCatalog==null ? "null" : optionalExcplicitItemsForResettingCatalog.size())+"; from "+JavaClassNames.callerNiceClassAndMethod(1);
            if (!ManagementNodeState.isHotProxy(nodeState)) {
                log.debug(message);
            } else {
                // in hot modes, make this message trace so we don't get too much output then
                log.trace(message);
            }
        }
        synchronized (populatingCatalogMutex) {
            try {
                if (hasRunFinalInitialization() && (needsInitialItemsLoaded || needsAdditionsLoaded)) {
                    // if we have already run "final" then we should only ever be used to reset the catalog, 
                    // not to initialize or add; e.g. we are being given a fixed list on a subsequent master rebind after the initial master rebind 
                    log.warn("Catalog initialization called to populate initial, even though it has already run the final official initialization");
                }
                isPopulating = true;
                BasicBrooklynCatalog catalog = (BasicBrooklynCatalog) managementContext.getCatalog();
                if (!catalog.getCatalog().isLoaded()) {
                    catalog.load();
                } else {
                    if (needsInitialItemsLoaded && hasRunAnyInitialization()) {
                        // an indication that something caused it to load early; not severe, but unusual
                        if (hasRunTransientOfficialInitialization) {
                            log.debug("Catalog initialization now populating, but has noted a previous official run which was not final (probalby loaded while in a standby mode, or a previous run failed); overwriting any items installed earlier");
                        } else {
                            log.warn("Catalog initialization now populating, but has noted a previous unofficial run (it may have been an early web request); overwriting any items installed earlier");
                        }
                        catalog.reset(ImmutableList.<CatalogItem<?,?>>of());
                    }
                }

                populateCatalogImpl(catalog, needsInitialItemsLoaded, needsAdditionsLoaded, optionalExcplicitItemsForResettingCatalog);
                if (nodeState == ManagementNodeState.MASTER) {
                    // TODO ideally this would remain false until it has *persisted* the changed catalog;
                    // if there is a subsequent startup failure the forced additions will not be persisted,
                    // but nor will they be loaded on a subsequent run.
                    // callers will have to restart a brooklyn, or reach into this class to change this field,
                    // or (recommended) manually adjust the catalog.
                    // TODO also, if a node comes up in standby, the addition might not take effector for a while
                    //
                    // however since these options are mainly for use on the very first brooklyn run, it's not such a big deal; 
                    // once up and running the typical way to add items is via the REST API
                    hasRunFinalInitialization = true;
                }
            } finally {
                if (!hasRunFinalInitialization) {
                    hasRunTransientOfficialInitialization = true;
                }
                isPopulating = false;
            }
        }
    }

    private void populateCatalogImpl(BasicBrooklynCatalog catalog, boolean needsInitialItemsLoaded, boolean needsAdditionsLoaded, Collection<CatalogItem<?, ?>> optionalItemsForResettingCatalog) {
        applyCatalogLoadMode();
        
        if (optionalItemsForResettingCatalog!=null) {
            catalog.reset(optionalItemsForResettingCatalog);
        }
        
        if (needsInitialItemsLoaded) {
            populateInitial(catalog);
        }

        if (needsAdditionsLoaded) {
            populateAdditions(catalog);
            populateViaCallbacks(catalog);
        }
    }

    private enum PopulateMode { YAML, XML, AUTODETECT }
    
    protected void populateInitial(BasicBrooklynCatalog catalog) {
        if (disallowLocal) {
            if (!hasRunFinalInitialization()) {
                log.debug("CLI initial catalog not being read when local catalog load mode is disallowed.");
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
        
        catalogUrl = Os.mergePaths(BrooklynServerConfig.getMgmtBaseDir( managementContext.getConfig() ), "catalog.bom");
        if (new File(catalogUrl).exists()) {
            populateInitialFromUri(catalog, new File(catalogUrl).toURI().toString(), PopulateMode.YAML);
            return;
        }
        
        catalogUrl = Os.mergePaths(BrooklynServerConfig.getMgmtBaseDir( managementContext.getConfig() ), "catalog.xml");
        if (new File(catalogUrl).exists()) {
            populateInitialFromUri(catalog, new File(catalogUrl).toURI().toString(), PopulateMode.XML);
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
            try {
                populateInitialFromUriXml(catalog, catalogUrl, contents);
                // clear YAML problem
                problem = null;
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                if (problem==null) problem = e;
            }
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
    private void populateInitialFromUriXml(BasicBrooklynCatalog catalog, String catalogUrl, String contents) {
        CatalogDto dto = CatalogDto.newDtoFromXmlContents(contents, catalogUrl);
        if (dto!=null) {
            catalog.reset(dto);
        }
    }

    boolean hasRunAdditions = false;
    protected void populateAdditions(BasicBrooklynCatalog catalog) {
        if (Strings.isNonBlank(additionsUri)) {
            if (disallowLocal) {
                if (!hasRunAdditions) {
                    log.warn("CLI additions supplied but not supported when catalog load mode disallows local loads; ignoring.");
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

    private Object setFromCLMMutex = new Object();
    private boolean setFromCatalogLoadMode = false;

    /** @deprecated since introduced in 0.7.0, only for legacy compatibility with 
     * {@link CatalogLoadMode} {@link BrooklynServerConfig#CATALOG_LOAD_MODE},
     * allowing control of catalog loading from a brooklyn property */
    @Deprecated
    public void applyCatalogLoadMode() {
        synchronized (setFromCLMMutex) {
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
    }

    /** Creates the catalog based on parameters set here, if not yet loaded,
     * but ignoring persisted state and warning if persistence is on and we are starting up
     * (because the official persistence is preferred and the catalog will be subsequently replaced);
     * for use when the catalog is accessed before persistence is completed. 
     * <p>
     * This method is primarily used during testing, which in many cases does not enforce the full startup order
     * and which wants a local catalog in any case. It may also be invoked if a client requests the catalog
     * while the server is starting up. */
    public void populateUnofficial(BasicBrooklynCatalog catalog) {
        synchronized (populatingCatalogMutex) {
            // check isPopulating in case this method gets called from inside another populate call
            if (hasRunAnyInitialization() || isPopulating) return;
            log.debug("Populating catalog unofficially ("+catalog+")");
            isPopulating = true;
            try {
                if (isStartingUp) {
                    log.warn("Catalog access requested when not yet initialized; populating best effort rather than through recommended pathway. Catalog data may be replaced subsequently.");
                }
                populateCatalogImpl(catalog, true, true, null);
            } finally {
                hasRunUnofficialInitialization = true;
                isPopulating = false;
            }
        }
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
