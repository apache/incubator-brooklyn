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
package brooklyn.entity.rebind.persister;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.rebind.persister.ListeningObjectStore.RecordingTransactionListener;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

/** uses recorder to ensure not too much data is written */
@Test
public class BrooklynMementoPersisterInMemorySizeIntegrationTest extends BrooklynMementoPersisterTestFixture {

    protected RecordingTransactionListener recorder;
    
    protected ManagementContext newPersistingManagementContext() {
        recorder = new RecordingTransactionListener("in-mem-test-"+Identifiers.makeRandomId(4));
        return RebindTestUtils.managementContextBuilder(classLoader, 
            new ListeningObjectStore(new InMemoryObjectStore(), recorder))
            .persistPeriod(Duration.millis(100)).buildStarted();
    }
    
    public void testPersistenceVolumeFast() throws IOException, TimeoutException, InterruptedException {
        doTestPersistenceVolume(50*1000, false);
    }
    @Test(groups="Integration",invocationCount=20)
    public void testPersistenceVolumeFastManyTimes() throws IOException, TimeoutException, InterruptedException {
        doTestPersistenceVolume(50*1000, false);
    }
    @Test(groups="Integration")
    public void testPersistenceVolumeWaiting() throws IOException, TimeoutException, InterruptedException {
        // by waiting we ensure there aren't extra writes going on
        doTestPersistenceVolume(50*1000, true);
    }
    
    protected void doTestPersistenceVolume(int bigBlockSize, boolean forceDelay) throws IOException, TimeoutException, InterruptedException {
        if (forceDelay) Time.sleep(Duration.FIVE_SECONDS);
        else recorder.blockUntilDataWrittenExceeds(512, Duration.FIVE_SECONDS);
        localManagementContext.getRebindManager().waitForPendingComplete(Duration.FIVE_SECONDS);
        
        long out1 = recorder.getBytesOut();
        int filesOut1 = recorder.getCountDataOut();
        Assert.assertTrue(out1>512, "should have written at least 0.5k, only wrote "+out1);
        Assert.assertTrue(out1<20*1000, "should have written less than 20k, wrote "+out1);
        Assert.assertTrue(filesOut1<20, "should have written fewer than 20 files, wrote "+out1);
        
        ((EntityInternal)app).setAttribute(TestEntity.NAME, "hello world");
        if (forceDelay) Time.sleep(Duration.FIVE_SECONDS);
        else recorder.blockUntilDataWrittenExceeds(out1+10, Duration.FIVE_SECONDS);
        localManagementContext.getRebindManager().waitForPendingComplete(Duration.FIVE_SECONDS);
        
        long out2 = recorder.getBytesOut();
        Assert.assertTrue(out2-out1>10, "should have written more data");
        int filesOut2 = recorder.getCountDataOut();
        Assert.assertTrue(filesOut2>filesOut1, "should have written more files");
        
        Assert.assertTrue(out2<50*1000, "should have written less than 50k, wrote "+out1);
        Assert.assertTrue(filesOut2<40, "should have written fewer than 40 files, wrote "+out1);
        
        ((EntityInternal)entity).setAttribute(TestEntity.NAME, Identifiers.makeRandomId(bigBlockSize));
        if (forceDelay) Time.sleep(Duration.FIVE_SECONDS);
        else recorder.blockUntilDataWrittenExceeds(out2+bigBlockSize, Duration.FIVE_SECONDS);
        localManagementContext.getRebindManager().waitForPendingComplete(Duration.FIVE_SECONDS);

        long out3 = recorder.getBytesOut();
        Assert.assertTrue(out3-out2 > bigBlockSize, "should have written 50k more data, only wrote "+out3+" compared with "+out2);
        int filesOut3 = recorder.getCountDataOut();
        Assert.assertTrue(filesOut3>filesOut2, "should have written more files");
        
        Assert.assertTrue(out2<100*1000+bigBlockSize, "should have written less than 100k+block, wrote "+out1);
        Assert.assertTrue(filesOut2<60, "should have written fewer than 60 files, wrote "+out1);
    }
    
}
