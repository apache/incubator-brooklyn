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
package org.apache.brooklyn.entity.webapp;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.entity.software.base.SoftwareProcessDriver;
import org.apache.brooklyn.location.cloudfoundry.CloudFoundryPaasLocation;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An abstract implementation of the {@link SoftwareProcessDriver} focus on PaaS.
 * This class is equivalent to @{link AbstractSoftwareProcessSshDriver} and @{link AbstractSoftwareProcessDriver}.
 */
public abstract class AbstractSoftwareProcessCloudFoundryDriver implements SoftwareProcessDriver {


    public static final Logger log = LoggerFactory
            .getLogger(AbstractSoftwareProcessCloudFoundryDriver.class);

    private final CloudFoundryPaasLocation location;
    EntityLocal entity;
    CloudFoundryClient client;

    public AbstractSoftwareProcessCloudFoundryDriver(EntityLocal entity,
                                                     CloudFoundryPaasLocation location) {
        this.entity = checkNotNull(entity, "entity");
        this.location = checkNotNull(location, "location");
        init();
    }

    protected void init() {
    }

    @Override
    public EntityLocal getEntity() {
        return entity;
    }

    @Override
    public CloudFoundryPaasLocation getLocation() {
        return location;
    }

    protected CloudFoundryClient getClient() {
        return client;
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void rebind() {

    }

    @Override
    public void start() {
        setUpClient();
    }

    protected void setUpClient() {
        if (client == null) {
            location.setUpClient();
            client = location.getCloudFoundryClient();
            checkNotNull(client, "CloudFoundry client");
        }
    }

    @Override
    public void restart() {
        //TODO: complete
    }

    @Override
    public void stop() {
        //TODO: complete
    }

    @Override
    public void kill() {
        stop();
    }


}
