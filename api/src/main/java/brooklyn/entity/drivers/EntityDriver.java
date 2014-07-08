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
package brooklyn.entity.drivers;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.location.Location;

import com.google.common.annotations.Beta;

/**
 * The EntityDriver provides an abstraction between the Entity and the environment (the {@link Location} it is running
 * in, so that an entity is not tightly coupled to a specific Location. E.g. you could have a TomcatEntity that uses
 * a TomcatDriver (an interface) and you could have different driver implementations like the
 * TomcatSshDriver/TomcatWindowsDriver and if in the future support for Puppet needs to be added, a TomcatPuppetDriver
 * could be added.
 *
 * @author Peter Veentjer.
 * @see DriverDependentEntity
 * @see EntityDriverManager
 */
public interface EntityDriver {

    /**
     * The entity instance that this is a driver for.
     * 
     * FIXME The signature of this will change to return Entity instead of EntityLocal.
     * This is a temporary workaround for groovy not supporting covariant return types,
     * see http://jira.codehaus.org/browse/GROOVY-5418. It is fixed in groovy 2.0.4 so
     * we will need to upgrade from 1.8.6 first.
     */
    @Beta
    EntityLocal getEntity();

    /**
     * The location the entity is running in.
     */
    Location getLocation();
}
