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
package brooklyn.entity.rebind.persister;

import brooklyn.management.ha.ManagementNodeSyncRecord;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;

/** Serializes the given object; it is often used with {@link BrooklynMemento} for persisting and restoring,
 * though it can be used for any object (and is also used for the {@link ManagementNodeSyncRecord} instances) */
public interface MementoSerializer<T> {
    
    public static final MementoSerializer<String> NOOP = new MementoSerializer<String>() {
        @Override
        public String toString(String memento) {
            return memento;
        }
        @Override
        public String fromString(String string) {
            return string;
        }
        @Override
        public void setLookupContext(LookupContext lookupContext) {
            // no-op
        }
        @Override
        public void unsetLookupContext() {
            // no-op
        }
    };
    
    String toString(T memento);
    T fromString(String string);
    void setLookupContext(LookupContext lookupContext);
    void unsetLookupContext();
}