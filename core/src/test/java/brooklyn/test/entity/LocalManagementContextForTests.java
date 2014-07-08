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

/** management context which forces an empty catalog to prevent scanning / interacting with local filesystem.
 * TODO this class could also force standard properties
 * TODO this should be more widely used in tests! */
public class LocalManagementContextForTests extends LocalManagementContext {

    public LocalManagementContextForTests(BrooklynProperties brooklynProperties) {
        super(disablePersistentStoreBackup(emptyIfNull(setEmptyCatalogAsDefault(emptyIfNull(brooklynProperties)))));
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
    
    public LocalManagementContextForTests() {
        this(null);
    }
    
}
