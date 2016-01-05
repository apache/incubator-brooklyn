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
package org.apache.brooklyn.core.mgmt.rebind;

import java.io.File;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindExceptionHandler;
import org.apache.brooklyn.api.mgmt.rebind.mementos.BrooklynMementoPersister;
import org.apache.brooklyn.core.mgmt.persist.PersistenceObjectStore;

import com.google.common.base.Function;

/**
 * See {@link RebindTestFixture#rebind(RebindOptions)} and {@link RebindTestUtils#rebind(RebindOptions)}.
 */
public class RebindOptions {
    public boolean checkSerializable;
    public boolean terminateOrigManagementContext;
    public RebindExceptionHandler exceptionHandler;
    public ManagementContext origManagementContext;
    public ManagementContext newManagementContext;
    public File mementoDir;
    public File mementoDirBackup;
    public Function<BrooklynMementoPersister, Void> stateTransformer;
    public ClassLoader classLoader;
    public PersistenceObjectStore objectStore;
    
    public static RebindOptions create() {
        return new RebindOptions();
    }
    public static RebindOptions create(RebindOptions options) {
        RebindOptions result = create();
        result.checkSerializable(options.checkSerializable);
        result.terminateOrigManagementContext(options.terminateOrigManagementContext);
        result.exceptionHandler(options.exceptionHandler);
        result.origManagementContext(options.origManagementContext);
        result.newManagementContext(options.newManagementContext);
        result.mementoDir(options.mementoDir);
        result.mementoDirBackup(options.mementoDirBackup);
        result.stateTransformer(options.stateTransformer);
        result.classLoader(options.classLoader);
        result.objectStore(options.objectStore);
        return result;
    }
    public RebindOptions checkSerializable(boolean val) {
        this.checkSerializable = val;
        return this;
    }
    public RebindOptions terminateOrigManagementContext(boolean val) {
        this.terminateOrigManagementContext = val;
        return this;
    }
    public RebindOptions exceptionHandler(RebindExceptionHandler val) {
        this.exceptionHandler = val;
        return this;
    }
    public RebindOptions origManagementContext(ManagementContext val) {
        this.origManagementContext = val;
        return this;
    }
    public RebindOptions newManagementContext(ManagementContext val) {
        this.newManagementContext = val;
        return this;
    }
    public RebindOptions mementoDir(File val) {
        this.mementoDir = val;
        return this;
    }
    public RebindOptions mementoDirBackup(File val) {
        this.mementoDirBackup = val;
        return this;
    }
    public RebindOptions stateTransformer(Function<BrooklynMementoPersister, Void> val) {
        this.stateTransformer = val;
        return this;
    }
    public RebindOptions classLoader(ClassLoader val) {
        this.classLoader = val;
        return this;
    }
    public RebindOptions objectStore(PersistenceObjectStore val) {
        this.objectStore = val;
        return this;
    }
}
