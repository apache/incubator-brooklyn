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

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.util.os.Os;

@Test
public class HighAvailabilityManagerFileBasedTest extends HighAvailabilityManagerTestFixture {

    private File dir;

    protected FileBasedObjectStore newPersistenceObjectStore() {
        if (dir!=null)
            throw new IllegalStateException("Test does not support multiple object stores");
        dir = Os.newTempDir(getClass());
        return new FileBasedObjectStore(dir);
    }

    @Override
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        super.tearDown();
        dir = Os.deleteRecursively(dir).asNullOrThrowing();
    }
}
