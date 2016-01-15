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
package org.apache.brooklyn.entity.nosql.mongodb;

import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;

public class MongoDBClientImpl extends SoftwareProcessImpl implements MongoDBClient {
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        sensors().set(Startable.SERVICE_UP, true);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class getDriverInterface() {
        return MongoDBClientDriver.class;
    }

    @Override
    public void runScript(String preStart, String scriptName) {
        ((MongoDBClientDriver)getDriver()).runScript(preStart, scriptName);
    }

}
