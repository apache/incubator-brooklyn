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
package brooklyn.entity.nosql.cassandra;

import org.testng.annotations.BeforeMethod;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.location.Location;

/**
 * Cassandra test framework for integration and live tests.
 */
public class AbstractCassandraNodeTest extends BrooklynAppLiveTestSupport {

    protected Location testLocation;
    protected CassandraNode cassandra;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        testLocation = app.newLocalhostProvisioningLocation();
    }

}
