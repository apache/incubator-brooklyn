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
package org.apache.brooklyn.core.location.internal;

import java.util.Map;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.mementos.LocationMemento;
import org.apache.brooklyn.config.ConfigInheritance;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal;
import org.apache.brooklyn.util.core.config.ConfigBag;

import com.google.common.annotations.Beta;

/**
 * Information about locations private to Brooklyn.
 */
public interface LocationInternal extends BrooklynObjectInternal, Location {

    @Beta
    public static final ConfigKey<String> ORIGINAL_SPEC = ConfigKeys.newStringConfigKey("spec.original", "The original spec used to instantiate a location");
    @Beta
    public static final ConfigKey<String> FINAL_SPEC = ConfigKeys.newStringConfigKey("spec.final", "The actual spec (in a chain) which instantiates a location");
    @Beta
    public static final ConfigKey<String> NAMED_SPEC_NAME = ConfigKeys.newStringConfigKey("spec.named.name", "The name on the (first) named spec in a chain");
    
    /**
     * Registers the given extension for the given type. If an extension already existed for
     * this type, then this will override it.
     * 
     * @throws NullPointerException if extensionType or extension are null
     * @throws IllegalArgumentException if extension does not implement extensionType
     */
    <T> void addExtension(Class<T> extensionType, T extension);

    /**
     * Get a record of the metadata of this location.
     * <p/>
     * <p>Metadata records are used to record an audit trail of events relating to location usage
     * (for billing purposes, for example). Implementations (and subclasses) should override this
     * method to return information useful for this purpose.</p>
     *
     * @return
     */
    public Map<String, String> toMetadataRecord();

    /**
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code ((LocationInternal)location).config().getLocalBag()}
     */
    @Deprecated
    ConfigBag getLocalConfigBag();

    /**
     * Returns all config, including that inherited from parents.
     * 
     * This method does not respect {@link ConfigInheritance} and so usage is discouraged.
     * 
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code ((LocationInternal)location).config().getBag()}
     */
    @Deprecated
    ConfigBag getAllConfigBag();

    /**
     * Users are strongly discouraged from calling or overriding this method.
     * It is for internal calls only, relating to persisting/rebinding entities.
     * This method may change (or be removed) in a future release without notice.
     */
    @Override
    @Beta
    RebindSupport<LocationMemento> getRebindSupport();
    
    @Override
    RelationSupportInternal<Location> relations();
    
    ManagementContext getManagementContext();
}
