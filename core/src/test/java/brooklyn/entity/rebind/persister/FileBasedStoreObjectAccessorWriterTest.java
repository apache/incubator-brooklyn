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

import java.io.File;
import java.io.IOException;

import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.PersistenceObjectStore.StoreObjectAccessorWithLock;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

@Test
public class FileBasedStoreObjectAccessorWriterTest extends PersistenceStoreObjectAccessorWriterTestFixture {

    private File file;
    
    protected StoreObjectAccessorWithLock newPersistenceStoreObjectAccessor() throws IOException {
        file = Os.newTempFile(getClass(), "txt");
        return new StoreObjectAccessorLocking(new FileBasedStoreObjectAccessor(file, ".tmp"));
    }
    
    @Override
    protected Duration getLastModifiedResolution() {
        // OSX is 1s, Windows FAT is 2s !
        return Duration.seconds(2);
    }
    
    @Test(groups="Integration")
    public void testLastModifiedTime() throws Exception {
        super.testLastModifiedTime();
    }
    
    @Test(groups="Integration")
    public void testFilePermissions600() throws Exception {
        accessor.put("abc");
        assertEquals(Files.readLines(file, Charsets.UTF_8), ImmutableList.of("abc"));
        
        FileBasedObjectStoreTest.assertFilePermission600(file);
    }
}
