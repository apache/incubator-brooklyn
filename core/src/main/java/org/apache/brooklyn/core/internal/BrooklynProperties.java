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
package org.apache.brooklyn.core.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.config.StringConfigMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.os.Os;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;

/** 
 * Utils for accessing command-line and system-env properties;
 * doesn't resolve anything (unless an execution context is supplied)
 * and treats ConfigKeys as of type object when in doubt,
 * or string when that is likely wanted (e.g. {@link #getFirst(Map, String...)}
 * <p>
 * Intention for normal use is that they are set during startup and not modified 
 * thereafter.
 */
@SuppressWarnings("rawtypes")
public interface BrooklynProperties extends Map, StringConfigMap {

    public static class Factory {
        private static final Logger LOG = LoggerFactory.getLogger(BrooklynProperties.Factory.class);
        
        /** creates a new empty {@link BrooklynProperties} */
        public static BrooklynProperties newEmpty() {
            return new BrooklynPropertiesImpl();
        }

        /** creates a new {@link BrooklynProperties} with contents loaded 
         * from the usual places, including *.properties files and environment variables */
        public static BrooklynProperties newDefault() {
            return new Builder(true).build();
        }

        public static Builder builderDefault() {
            return new Builder(true);
        }

        public static Builder builderEmpty() {
            return new Builder(false);
        }

        public static class Builder {
            private String defaultLocationMetadataUrl;
            private String globalLocationMetadataFile = null;
            private String globalPropertiesFile = null;
            private String localPropertiesFile = null;
            private BrooklynProperties originalProperties = null;
            
            /** @deprecated since 0.7.0 use static methods in {@link Factory} to create */
            public Builder() {
                this(true);
            }
            
            private Builder(boolean setGlobalFileDefaults) {
                resetDefaultLocationMetadataUrl();
                if (setGlobalFileDefaults) {
                    resetGlobalFiles();
                }
            }
            
            public Builder resetDefaultLocationMetadataUrl() {
                defaultLocationMetadataUrl = "classpath://brooklyn/location-metadata.properties";
                return this;
            }
            public Builder resetGlobalFiles() {
                defaultLocationMetadataUrl = "classpath://brooklyn/location-metadata.properties";
                globalLocationMetadataFile = Os.mergePaths(Os.home(), ".brooklyn", "location-metadata.properties");
                globalPropertiesFile = Os.mergePaths(Os.home(), ".brooklyn", "brooklyn.properties");
                return this;
            }
            
            /**
             * Creates a Builder that when built, will return the BrooklynProperties passed to this constructor
             */
            private Builder(BrooklynProperties originalProperties) {
                this.originalProperties = new BrooklynPropertiesImpl().addFromMap(originalProperties);
            }
            
            /**
             * The URL of a default location-metadata.properties (for meta-data about different locations, such as iso3166 and global lat/lon). 
             * Defaults to classpath://brooklyn/location-metadata.properties
             */
            public Builder defaultLocationMetadataUrl(String val) {
                defaultLocationMetadataUrl = checkNotNull(val, "file");
                return this;
            }
            
            /**
             * The URL of a location-metadata.properties file that appends to and overwrites values in the locationMetadataUrl. 
             * Defaults to ~/.brooklyn/location-metadata.properties
             */
            public Builder globalLocationMetadataFile(String val) {
                globalLocationMetadataFile = checkNotNull(val, "file");
                return this;
            }
            
            /**
             * The URL of a shared brooklyn.properties file. Defaults to ~/.brooklyn/brooklyn.properties.
             * Can be null to disable.
             */
            public Builder globalPropertiesFile(String val) {
                globalPropertiesFile = val;
                return this;
            }
            
            @Beta
            public boolean hasDelegateOriginalProperties() {
                return this.originalProperties==null;
            }
            
            /**
             * The URL of a brooklyn.properties file specific to this launch. Appends to and overwrites values in globalPropertiesFile.
             */
            public Builder localPropertiesFile(String val) {
                localPropertiesFile = val;
                return this;
            }
            
            public BrooklynProperties build() {
                if (originalProperties != null) 
                    return new BrooklynPropertiesImpl().addFromMap(originalProperties);
                
                BrooklynProperties properties = new BrooklynPropertiesImpl();

                // TODO Could also read from http://brooklyn.io, for up-to-date values?
                // But might that make unit tests run very badly when developer is offline?
                addPropertiesFromUrl(properties, defaultLocationMetadataUrl, false);
                
                addPropertiesFromFile(properties, globalLocationMetadataFile);
                addPropertiesFromFile(properties, globalPropertiesFile);
                addPropertiesFromFile(properties, localPropertiesFile);
                
                properties.addEnvironmentVars();
                properties.addSystemProperties();

                return properties;
            }

            public static Builder fromProperties(BrooklynProperties brooklynProperties) {
                return new Builder(brooklynProperties);
            }

            @Override
            public String toString() {
                return Objects.toStringHelper(this)
                        .omitNullValues()
                        .add("originalProperties", originalProperties)
                        .add("defaultLocationMetadataUrl", defaultLocationMetadataUrl)
                        .add("globalLocationMetadataUrl", globalLocationMetadataFile)
                        .add("globalPropertiesFile", globalPropertiesFile)
                        .add("localPropertiesFile", localPropertiesFile)
                        .toString();
            }
        }
        
        private static void addPropertiesFromUrl(BrooklynProperties p, String url, boolean warnIfNotFound) {
            if (url==null) return;
            
            try {
                p.addFrom(ResourceUtils.create(BrooklynProperties.class).getResourceFromUrl(url));
            } catch (Exception e) {
                if (warnIfNotFound)
                    LOG.warn("Could not load {}; continuing", url);
                if (LOG.isTraceEnabled()) LOG.trace("Could not load "+url+"; continuing", e);
            }
        }
        
        private static void addPropertiesFromFile(BrooklynProperties p, String file) {
            if (file==null) return;
            
            String fileTidied = Os.tidyPath(file);
            File f = new File(fileTidied);

            if (f.exists()) {
                p.addFrom(f);
            }
        }
    }

    public BrooklynProperties addEnvironmentVars();

    public BrooklynProperties addSystemProperties();

    public BrooklynProperties addFrom(ConfigBag cfg);

    public BrooklynProperties addFrom(Map map);

    public BrooklynProperties addFrom(InputStream i);
    
    public BrooklynProperties addFrom(File f);

    public BrooklynProperties addFrom(URL u);

    /**
     * @see ResourceUtils#getResourceFromUrl(String)
     *
     * of the form form file:///home/... or http:// or classpath://xx ;
     * for convenience if not starting with xxx: it is treated as a classpath reference or a file;
     * throws if not found (but does nothing if argument is null)
     */
    public BrooklynProperties addFromUrl(String url);

    /** expects a property already set in scope, whose value is acceptable to {@link #addFromUrl(String)};
     * if property not set, does nothing */
    public BrooklynProperties addFromUrlProperty(String urlProperty);

    /**
    * adds the indicated properties
    */
    public BrooklynProperties addFromMap(Map properties);

    /** inserts the value under the given key, if it was not present */
    public boolean putIfAbsent(String key, Object value);

   /** @deprecated attempts to call get with this syntax are probably mistakes; get(key, defaultValue) is fine but
    * Map is unlikely the key, much more likely they meant getFirst(flags, key).
    */
   @Deprecated
   public String get(Map flags, String key);

    /** returns the value of the first key which is defined
     * <p>
     * takes the following flags:
     * 'warnIfNone', 'failIfNone' (both taking a boolean (to use default message) or a string (which is the message));
     * and 'defaultIfNone' (a default value to return if there is no such property); defaults to no warning and null response */
    @Override
    public String getFirst(String ...keys);

    @Override
    public String getFirst(Map flags, String ...keys);

    /** like normal map.put, except config keys are dereferenced on the way in */
    public Object put(Object key, Object value);

    /** like normal map.putAll, except config keys are dereferenced on the way in */
    @Override
    public void putAll(Map vals);
    
    public <T> Object put(HasConfigKey<T> key, T value);

    public <T> Object put(ConfigKey<T> key, T value);
    
    public <T> boolean putIfAbsent(ConfigKey<T> key, T value);
    
    @Override
    public <T> T getConfig(ConfigKey<T> key);

    @Override
    public <T> T getConfig(HasConfigKey<T> key);

    @Override
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue);

    @Override
    public <T> T getConfig(ConfigKey<T> key, T defaultValue);

    @Override
    public Object getRawConfig(ConfigKey<?> key);
    
    @Override
    public Maybe<Object> getConfigRaw(ConfigKey<?> key, boolean includeInherited);

    @Override
    public Map<ConfigKey<?>, Object> getAllConfig();

    @Override
    public BrooklynProperties submap(Predicate<ConfigKey<?>> filter);

    @Override
    public Map<String, Object> asMapWithStringKeys();
}
