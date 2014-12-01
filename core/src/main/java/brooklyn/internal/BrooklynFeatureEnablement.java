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
package brooklyn.internal;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.management.ha.HighAvailabilityMode;

import com.google.common.annotations.Beta;
import com.google.common.collect.Maps;

/**
 * For enabling/disabling experimental features.
 * They can be enabled via java system properties, or by explicitly calling {@link #setEnablement(String, boolean)}.
 * <p>
 * For example, start brooklyn with {@code -Dbrooklyn.experimental.feature.policyPersistence=true}
 * 
 * @author aled
 */
@Beta
public class BrooklynFeatureEnablement {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynFeatureEnablement.class);

    public static final String FEATURE_POLICY_PERSISTENCE_PROPERTY = "brooklyn.experimental.feature.policyPersistence";
    
    public static final String FEATURE_ENRICHER_PERSISTENCE_PROPERTY = "brooklyn.experimental.feature.enricherPersistence";

    public static final String FEATURE_FEED_PERSISTENCE_PROPERTY = "brooklyn.experimental.feature.feedPersistence";
    
    /** whether feeds are automatically registered when set on entities, so that they are persisted */
    public static final String FEATURE_FEED_REGISTRATION_PROPERTY = "brooklyn.experimental.feature.feedRegistration";

    public static final String FEATURE_CATALOG_PERSISTENCE_PROPERTY = "brooklyn.experimental.feature.catalogPersistence";
    
    /** whether the default standby mode is {@link HighAvailabilityMode#HOT_STANDBY} or falling back to the traditional
     * {@link HighAvailabilityMode#STANDBY} */
    public static final String FEATURE_DEFAULT_STANDBY_IS_HOT_PROPERTY = "brooklyn.experimental.feature.defaultStandbyIsHot";
    
    /** whether to attempt to use {@link BrooklynStorage} (datagrid) as a backing store for data;
     * note this is <b>not</b> compatible with {@link #FEATURE_DEFAULT_STANDBY_IS_HOT_PROPERTY} 
     * which uses a blob/file store and a larger-granularity rebind process than was intended with the datagrid */
    /* not sure if we still even need this? now the rebind/read-only feature reloads on demand from the persistence store;
     * the data-grid backing  */
    public static final String FEATURE_USE_BROOKLYN_LIVE_OBJECTS_DATAGRID_STORAGE = "brooklyn.experimental.feature.useBrooklynLiveObjectsDatagridStorage";

    /**
     * Renaming threads can really helps with debugging etc; however it's a massive performance hit (2x)
     * <p>
     * We get 55000 tasks per sec with this off, 28k/s with this on.
     * <p>
     * Defaults to false if system property is not set.
     */
    public static final String FEATURE_RENAME_THREADS = "brooklyn.executionManager.renameThreads";

    /**
     * When rebinding to store created from a previous version the catalogItemId properties will be missing which
     * results in errors when OSGi bundles are used. When enabled the code tries to infer the catalogItemId from
     * <ul>
     *   <li> parent entities
     *   <li> catalog items matching the type that needs to be deserialized
     *   <li> iterating through all catalog items and checking if they can provide the needed type
     * </ul>
     */
    public static final String FEATURE_INFER_CATALOG_ITEM_ON_REBIND = "brooklyn.backwardCompatibility.feature.inferCatalogItemOnRebind";
    
    public static final String FEATURE_SSH_ASYNC_EXEC = "brooklyn.experimental.feature.ssh.asyncExec";

    private static final Map<String, Boolean> FEATURE_ENABLEMENTS = Maps.newLinkedHashMap();

    private static final Object MUTEX = new Object();
    
    static void setDefaults() {
        // Idea is here one can put experimental features that are *enabled* by default, but 
        // that can be turned off via system properties, or vice versa.
        // Typically this is useful where a feature is deemed risky!
        
        setDefault(FEATURE_POLICY_PERSISTENCE_PROPERTY, true);
        setDefault(FEATURE_ENRICHER_PERSISTENCE_PROPERTY, true);
        setDefault(FEATURE_FEED_PERSISTENCE_PROPERTY, true);
        setDefault(FEATURE_FEED_REGISTRATION_PROPERTY, false);
        setDefault(FEATURE_CATALOG_PERSISTENCE_PROPERTY, true);
        setDefault(FEATURE_DEFAULT_STANDBY_IS_HOT_PROPERTY, false);
        setDefault(FEATURE_USE_BROOKLYN_LIVE_OBJECTS_DATAGRID_STORAGE, false);
        setDefault(FEATURE_RENAME_THREADS, false);
        setDefault(FEATURE_INFER_CATALOG_ITEM_ON_REBIND, true);
        setDefault(FEATURE_SSH_ASYNC_EXEC, true);
    }
    
    static {
        setDefaults();
    }
    
    public static boolean isEnabled(String property) {
        synchronized (MUTEX) {
            if (!FEATURE_ENABLEMENTS.containsKey(property)) {
                String rawVal = System.getProperty(property);
                boolean val = Boolean.parseBoolean(rawVal);
                FEATURE_ENABLEMENTS.put(property, val);
            }
            return FEATURE_ENABLEMENTS.get(property);
        }
    }

    public static boolean enable(String property) {
        return setEnablement(property, true);
    }
    
    public static boolean disable(String property) {
        return setEnablement(property, false);
    }
    
    public static boolean setEnablement(String property, boolean val) {
        synchronized (MUTEX) {
            boolean oldVal = isEnabled(property);
            FEATURE_ENABLEMENTS.put(property, val);
            return oldVal;
        }
    }
    
    static void setDefault(String property, boolean val) {
        synchronized (MUTEX) {
            if (!FEATURE_ENABLEMENTS.containsKey(property)) {
                String rawVal = System.getProperty(property);
                if (rawVal == null) {
                    FEATURE_ENABLEMENTS.put(property, val);
                    LOG.debug("Default enablement of "+property+" set to "+val);
                } else {
                    LOG.debug("Not setting default enablement of "+property+" to "+val+", because system property is "+rawVal);
                }
            }
        }
    }
    
    static void clearCache() {
        synchronized (MUTEX) {
            FEATURE_ENABLEMENTS.clear();
            setDefaults();
        }
    }
}
