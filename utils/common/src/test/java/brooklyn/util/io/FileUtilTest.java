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
package brooklyn.util.io;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.File;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.os.Os;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

public class FileUtilTest {

    private File file;
    private File dir;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        file = File.createTempFile("fileUtilsTest", ".tmp");
        dir = Files.createTempDir();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (file != null) file.delete();
        if (dir != null) Os.deleteRecursively(dir);
    }
    
    @Test(groups="Integration")
    public void testSetFilePermission600() throws Exception {
        FileUtil.setFilePermissionsTo600(file);
        String permissions = FileUtil.getFilePermissions(file).get();
        assertEquals(permissions, "-rw-------");
    }
    
    @Test(groups="Integration")
    public void testSetFilePermission700() throws Exception {
        FileUtil.setFilePermissionsTo700(file);
        String permissions = FileUtil.getFilePermissions(file).get();
        assertEquals(permissions, "-rwx------");
    }

    @Test(groups="Integration")
    public void testSetDirPermission700() throws Exception {
        FileUtil.setFilePermissionsTo700(dir);
        String permissions = FileUtil.getFilePermissions(dir).get();
        assertEquals(permissions, "drwx------");
    }
    
    @Test(groups="Integration")
    public void testMoveDir() throws Exception {
        File destParent = Files.createTempDir();
        try {
            Files.write("abc", new File(dir, "afile"), Charsets.UTF_8);
            File destDir = new File(destParent, "dest");
            
            FileUtil.moveDir(dir, destDir);
            
            assertEquals(Files.readLines(new File(destDir, "afile"), Charsets.UTF_8), ImmutableList.of("abc"));
            assertFalse(dir.exists());
        } finally {
            if (destParent != null) Os.deleteRecursively(destParent);
        }
    }
    
    @Test(groups="Integration")
    public void testCopyDir() throws Exception {
        File destParent = Files.createTempDir();
        try {
            Files.write("abc", new File(dir, "afile"), Charsets.UTF_8);
            File destDir = new File(destParent, "dest");
            
            FileUtil.copyDir(dir, destDir);
            
            assertEquals(Files.readLines(new File(destDir, "afile"), Charsets.UTF_8), ImmutableList.of("abc"));
            assertEquals(Files.readLines(new File(dir, "afile"), Charsets.UTF_8), ImmutableList.of("abc"));
        } finally {
            if (destParent != null) Os.deleteRecursively(destParent);
        }
    }
    
    // Never run this as root! You really don't want to mess with permissions of these files!
    // Visual inspection test that we get the log message just once saying:
    //     WARN  Failed to set permissions to 600 for file /etc/hosts: setRead=false, setWrite=false, setExecutable=false; subsequent failures (on any file) will be logged at trace
    // Disabled because really don't want to mess up anyone's system, and also no automated assertions about log messages.
    @Test(groups="Integration", enabled=false)
    public void testLogsWarningOnceIfCannotSetPermission() throws Exception {
        File file = new File("/etc/hosts");
        FileUtil.setFilePermissionsTo600(file);
        FileUtil.setFilePermissionsTo600(file);
        FileUtil.setFilePermissionsTo700(file);
        FileUtil.setFilePermissionsTo700(file);
    }
}
