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
package org.apache.brooklyn.policy;

import java.util.Map;

import org.apache.brooklyn.mementos.EnricherMemento;

import brooklyn.config.ConfigKey;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.entity.trait.Configurable;

import com.google.common.annotations.Beta;

/**
 * Publishes metrics for an entity, e.g. aggregating information from other sensors/entities.
 *
 * Has some similarities to {@link Policy}. However, enrichers specifically do not invoke
 * effectors and should only function to publish new metrics.
 */
public interface Enricher extends EntityAdjunct, Rebindable, Configurable {
    /**
     * A unique id for this enricher.
     */
    @Override
    String getId();

    /**
     * Information about the type of this entity; analogous to Java's object.getClass.
     */
    @Beta
    EnricherType getEnricherType();

    /**
     * Convenience method for {@code config().get(key)}
     */
    <T> T getConfig(ConfigKey<T> key);
    
    /**
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code policy.config().setConfig(key, val)}
     */
    @Deprecated
    <T> T setConfig(ConfigKey<T> key, T val);

    /**
     * Users are strongly discouraged from calling or overriding this method.
     * It is for internal calls only, relating to persisting/rebinding entities.
     * This method may change (or be removed) in a future release without notice.
     */
    @Override
    @Beta
    RebindSupport<EnricherMemento> getRebindSupport();

}
