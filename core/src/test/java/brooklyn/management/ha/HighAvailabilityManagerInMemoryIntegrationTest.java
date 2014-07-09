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
package brooklyn.management.ha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.InMemoryObjectStore;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Ticker;

@Test(groups="Integration")
public class HighAvailabilityManagerInMemoryIntegrationTest extends HighAvailabilityManagerTestFixture {

    private static final Logger log = LoggerFactory.getLogger(HighAvailabilityManagerInMemoryIntegrationTest.class);
    
    @Override
    protected PersistenceObjectStore newPersistenceObjectStore() {
        return new InMemoryObjectStore();
    }

    @Override
    protected Duration getPollPeriod() {
        return Duration.millis(100);
    }
    
    @Override
    protected long tickerAdvance(Duration duration) {
        log.info("sleeping for "+duration);
        // actually sleep, in order to advance the local time ticker
        Time.sleep(duration);
        return super.tickerAdvance(duration);
    }

    @Override
    protected Ticker getRemoteTicker() {
        // use real times
        return null;
    }

    @Override
    @Test(groups="Integration", enabled=false, invocationCount=50) 
    public void testGetManagementPlaneStatusManyTimes() throws Exception {
    }

    @Test(groups="Integration")
    @Override
    public void testGetManagementPlaneStatus() throws Exception {
        super.testGetManagementPlaneStatus();
    }
    
    @Test(groups="Integration")
    @Override
    public void testDoesNotPromoteIfMasterTimeoutNotExpired() throws Exception {
        super.testDoesNotPromoteIfMasterTimeoutNotExpired();
    }
    
    @Test(groups="Integration")
    @Override
    public void testGetManagementPlaneSyncStateDoesNotThrowNpeBeforePersisterSet() throws Exception {
        super.testGetManagementPlaneSyncStateDoesNotThrowNpeBeforePersisterSet();
    }
    
    @Test(groups="Integration")
    @Override
    public void testGetManagementPlaneSyncStateInfersTimedOutNodeAsFailed() throws Exception {
        super.testGetManagementPlaneSyncStateInfersTimedOutNodeAsFailed();
    }
    
    @Test(groups="Integration")
    @Override
    public void testPromotes() throws Exception {
        super.testPromotes();
    }
    
}
