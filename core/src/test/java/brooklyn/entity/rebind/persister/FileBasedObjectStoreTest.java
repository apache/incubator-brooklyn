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
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.io.FileUtil;
import brooklyn.util.os.Os;

import com.google.common.io.Files;

public class FileBasedObjectStoreTest {

    private LocalManagementContextForTests mgmt;
    private File parentdir;
    private File basedir;
    private FileBasedObjectStore store;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mgmt = new LocalManagementContextForTests();
        parentdir = Files.createTempDir();
        basedir = new File(parentdir, "mystore");
        store = new FileBasedObjectStore(basedir);
        store.injectManagementContext(mgmt);
        store.prepareForSharedUse(PersistMode.AUTO, HighAvailabilityMode.DISABLED);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (store != null) store.close();
        if (parentdir != null) Os.deleteRecursively(basedir);
        if (mgmt != null) Entities.destroyAll(mgmt);
    }
    
    @Test(groups="Integration")
    public void testSubPathCreatedWithPermission700() throws Exception {
        store.createSubPath("mysubdir");
        File subdir = new File(basedir, "mysubdir");
        
        assertFilePermission700(basedir);
        assertFilePermission700(subdir);
    }
    
    @Test
    public void testIsMementoDirExistsButEmpty() throws Exception {
        basedir = new File(parentdir, "testIsMementoDirExistsButEmpty");
        assertFalse(FileBasedObjectStore.isMementoDirExistButEmpty(basedir));
        assertFalse(FileBasedObjectStore.isMementoDirExistButEmpty(basedir.getAbsolutePath()));
        
        basedir.mkdir();
        assertTrue(FileBasedObjectStore.isMementoDirExistButEmpty(basedir));
        assertTrue(FileBasedObjectStore.isMementoDirExistButEmpty(basedir.getAbsolutePath()));
        
        new File(basedir, "entities").mkdir();
        new File(basedir, "locations").mkdir();
        assertTrue(FileBasedObjectStore.isMementoDirExistButEmpty(basedir));
        assertTrue(FileBasedObjectStore.isMementoDirExistButEmpty(basedir.getAbsolutePath()));
        
        new File(new File(basedir, "locations"), "afile").createNewFile();
        assertFalse(FileBasedObjectStore.isMementoDirExistButEmpty(basedir));
        assertFalse(FileBasedObjectStore.isMementoDirExistButEmpty(basedir.getAbsolutePath()));
    }
    
    static void assertFilePermission700(File file) throws FileNotFoundException {
        assertEquals(FileUtil.getFilePermissions(file).get().substring(1), "rwx------");
    }
    
    static void assertFilePermission600(File file) throws Exception {
        assertEquals(FileUtil.getFilePermissions(file).get().substring(1), "rw-------");
    }
}
