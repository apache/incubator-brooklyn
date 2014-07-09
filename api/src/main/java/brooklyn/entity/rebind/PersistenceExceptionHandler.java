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
package brooklyn.entity.rebind;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.mementos.Memento;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;

import com.google.common.annotations.Beta;

/**
 * Handler called on all exceptions to do with persistence.
 * 
 * @author aled
 */
@Beta
public interface PersistenceExceptionHandler {

    void stop();

    void onGenerateLocationMementoFailed(Location location, Exception e);

    void onGenerateEntityMementoFailed(Entity entity, Exception e);
    
    void onGeneratePolicyMementoFailed(Policy policy, Exception e);
    
    void onGenerateEnricherMementoFailed(Enricher enricher, Exception e);

    void onPersistMementoFailed(Memento memento, Exception e);
    
    void onDeleteMementoFailed(String id, Exception e);
}
