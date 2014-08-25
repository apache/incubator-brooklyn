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
package brooklyn.test.entity;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.management.internal.LocalManagementContext;

/** management context which allows disabling common time-consuming tasks.
 * most instances have:
 * <li> empty properties
 * <li> no catalog
 * <li> persistence off
 * <li> osgi off
 * <p>
 * the constructor, {@link #newInstance()}, and {@link #builder(boolean)} (with true) return the above;
 * the constructor and the builder allow custom properties to be set,
 * and the builder allows individual items to be turned back on.
 */
public class LocalManagementContextForTests extends LocalManagementContext {

    protected LocalManagementContextForTests(BrooklynProperties brooklynProperties, boolean minimal) {
        super(builder(minimal).useProperties(brooklynProperties).buildProperties());
    }
    
    public LocalManagementContextForTests() {
        this(null);
    }
    
    public LocalManagementContextForTests(BrooklynProperties brooklynProperties) {
        this(brooklynProperties, true);
    }
    
    private static BrooklynProperties emptyIfNull(BrooklynProperties bp) {
        if (bp!=null) return bp;
        return BrooklynProperties.Factory.newEmpty();
    }

    public static BrooklynProperties setEmptyCatalogAsDefault(BrooklynProperties brooklynProperties) {
        if (brooklynProperties==null) return null;
        brooklynProperties.putIfAbsent(BrooklynServerConfig.BROOKLYN_CATALOG_URL, "classpath://brooklyn-catalog-empty.xml");
        return brooklynProperties;
    }
    
    public static BrooklynProperties disableOsgi(BrooklynProperties brooklynProperties) {
        if (brooklynProperties==null) return null;
        brooklynProperties.putIfAbsent(BrooklynServerConfig.USE_OSGI, false);
        return brooklynProperties;
    }
    
    public static BrooklynProperties disablePersistentStoreBackup(BrooklynProperties brooklynProperties) {
        if (brooklynProperties==null) return null;
        brooklynProperties.putIfAbsent(BrooklynServerConfig.PERSISTENCE_BACKUPS_REQUIRED, false);
        return brooklynProperties;
    }
    
    public static class Builder {
        boolean disablePersistence = false;
        boolean disableOsgi = false;
        boolean emptyCatalog = false;
        BrooklynProperties properties = null;
        
        public Builder disablePersistence() { return disablePersistence(true); }
        public Builder disableOsgi() { return disableOsgi(true); }
        public Builder emptyCatalog() { return emptyCatalog(true); }

        public Builder disablePersistence(boolean disablePersistence) { this.disablePersistence = disablePersistence; return this; }
        public Builder disableOsgi(boolean disableOsgi) { this.disableOsgi = disableOsgi; return this; }
        public Builder emptyCatalog(boolean emptyCatalog) { this.emptyCatalog = emptyCatalog; return this; }

        // for use in the outer class's constructor
        private Builder minimal(boolean really) {
            if (really) minimal();
            return this;
        }
        
        public Builder minimal() {
            disablePersistence();
            disableOsgi();
            emptyCatalog();
            properties = null;
            return this;
        }
        
        public Builder useProperties(BrooklynProperties properties) {
            if (this.properties!=null && properties!=null)
                throw new IllegalStateException("Cannot set multiple properties");
            this.properties = properties; 
            return this; 
        }
        
        public BrooklynProperties buildProperties() {
            BrooklynProperties result = emptyIfNull(properties);
            if (disablePersistence) LocalManagementContextForTests.disablePersistentStoreBackup(result);
            if (disableOsgi) LocalManagementContextForTests.disableOsgi(result);
            if (emptyCatalog) LocalManagementContextForTests.setEmptyCatalogAsDefault(result);
            return result;
        }
        
        public LocalManagementContext build() {
            return new LocalManagementContextForTests(buildProperties(), false);
        }
        public Builder useDefaultProperties() {
            properties = BrooklynProperties.Factory.newDefault();
            return this;
        }
    }
    
    /** create a new builder, defaulting to empty properties, and with the parameter determining whether 
     * by default to disable common things disabled in tests (and the caller can re-enable selected ones individually)
     * or (if false) leaving everything enabled (so the caller turns things off) */
    public static Builder builder(boolean minimal) { return new Builder().minimal(minimal); }
    
    public static LocalManagementContext newInstance() {
        return builder(true).build();
    }

    public static LocalManagementContext newInstance(BrooklynProperties properties) {
        return builder(true).useProperties(properties).build();
    }

    public static LocalManagementContext newInstanceWithOsgi() {
        return builder(true).disableOsgi(false).build();
    }

}
