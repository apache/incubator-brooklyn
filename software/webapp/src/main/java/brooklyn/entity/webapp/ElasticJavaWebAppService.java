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
package brooklyn.entity.webapp;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractConfigurableEntityFactory;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.EntityFactoryForLocation;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;

public interface ElasticJavaWebAppService extends JavaWebAppService, Startable {

    public interface ElasticJavaWebAppServiceAwareLocation {
        ConfigurableEntityFactory<ElasticJavaWebAppService> newWebClusterFactory();
    }

    /** @deprecated since 0.7.0 use {@link EntitySpec} */
    @Deprecated
    public static class Factory extends AbstractConfigurableEntityFactory<ElasticJavaWebAppService>
    implements EntityFactoryForLocation<ElasticJavaWebAppService> {

        private static final long serialVersionUID = 6654647949712073832L;

        public ElasticJavaWebAppService newEntity2(@SuppressWarnings("rawtypes") Map flags, Entity parent) {
            return new ControlledDynamicWebAppClusterImpl(flags, parent);
        }

        public ConfigurableEntityFactory<ElasticJavaWebAppService> newFactoryForLocation(Location l) {
            if (l instanceof ElasticJavaWebAppServiceAwareLocation) {
                return ((ElasticJavaWebAppServiceAwareLocation)l).newWebClusterFactory().configure(config);
            }
            //optional, fail fast if location not supported
            if (!(l instanceof MachineProvisioningLocation))
                throw new UnsupportedOperationException("cannot create this entity in location "+l);
            return this;
        }
    }
    
}
