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
package brooklyn.entity.messaging.jms;

import brooklyn.entity.basic.AbstractEntity;

import com.google.common.base.Preconditions;

public abstract class JMSDestinationImpl extends AbstractEntity implements JMSDestination {
    public JMSDestinationImpl() {
    }

    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        Preconditions.checkNotNull(getName(), "Name must be specified");
    }

    @Override
    public String getName() {
        return getDisplayName();
    }
    
    protected abstract void connectSensors();

    protected abstract void disconnectSensors();

    public abstract void delete();

    public void destroy() {
        disconnectSensors();
        delete();
        super.destroy();
    }
}
