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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.Executors;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public abstract class PersistenceStoreObjectAccessorWriterTestFixture {

    private static final Duration TIMEOUT = Duration.TEN_SECONDS;
    
    protected ListeningExecutorService executor;
    protected StoreObjectAccessorWithLock accessor;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        accessor = newPersistenceStoreObjectAccessor();
    }

    protected abstract StoreObjectAccessorWithLock newPersistenceStoreObjectAccessor() throws IOException;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (accessor != null) {
            accessor.delete();
            accessor.waitForCurrentWrites(Duration.TEN_SECONDS);
        }
        if (executor != null) executor.shutdownNow();
    }

    @Test
    public void testWritesFile() throws Exception {
        accessor.put("abc");
        accessor.waitForCurrentWrites(TIMEOUT);

        assertEquals(accessor.get(), "abc");
    }

    /** most storage systems support <= 1ms resolution; but some file systems -- esp FAT and OSX HFS+ are much much higher! */
    protected Duration getLastModifiedResolution() {
        return Duration.millis(1);
    }
    
    @Test
    public void testLastModifiedTime() throws Exception {
        accessor.delete();
        Assert.assertNull(accessor.getLastModifiedDate());
        accessor.put("abc");
        accessor.waitForCurrentWrites(TIMEOUT);
        Date write1 = accessor.getLastModifiedDate();
        Assert.assertNotNull(write1);
        
        Time.sleep(getLastModifiedResolution().times(2));
        accessor.put("abc");
        accessor.waitForCurrentWrites(TIMEOUT);
        Date write2 = accessor.getLastModifiedDate();
        Assert.assertNotNull(write2);
        Assert.assertTrue(write2.after(write1), "dates are "+write1+" and "+write2);
    }
    
    @Test
    public void testWriteBacklogThenDeleteWillLeaveFileDeleted() throws Exception {
        String big = makeBigString(biggishSize());
        
        accessor.put(big);
        accessor.put(big);
        accessor.delete();
        
        accessor.waitForCurrentWrites(TIMEOUT);
        assertFalse(accessor.exists());
    }

    protected int biggishSize() {
        return 100000;
    }
    
    protected String makeBigString(int size) {
        // prefer non-random so can't be compressed
        return Identifiers.makeRandomBase64Id(size);
//        return com.google.common.base.Strings.repeat("x", size);
    }
}
