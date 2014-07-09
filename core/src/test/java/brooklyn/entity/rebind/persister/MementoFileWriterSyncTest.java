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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class MementoFileWriterSyncTest {

    private File file;
    private File dir;
    private MementoSerializer<String> serializer;
    private MementoFileWriterSync<String> writer;

    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        dir = Os.newTempDir(JavaClassNames.cleanSimpleClassName(this));
        file = Os.newTempFile(getClass(), "txt");
        serializer = new MementoSerializer<String>() {
            @Override public String toString(String memento) {
                return memento;
            }
            @Override public String fromString(String string) {
                return string;
            }
            @Override public void setLookupContext(LookupContext lookupContext) {
            }
            @Override public void unsetLookupContext() {
            }
        };
        writer = new MementoFileWriterSync<String>(file, serializer);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (file != null) file.delete();
        dir = Os.deleteRecursively(dir).asNullOrThrowing();
    }

    @Test
    public void testWritesFile() throws Exception {
        writer.write("abc");
        
        String fromfile = Files.asCharSource(file, Charsets.UTF_8).read();
        assertEquals(fromfile, "abc");
    }
    
    @Test
    public void testWritesFileEvenIfDoesNotExist() throws Exception {
        File newFile = new File(dir, "mementoFileWriterSyncTest-"+Identifiers.makeRandomId(4)+".txt");
        writer = new MementoFileWriterSync<String>(newFile, serializer);
        writer.write("abc");
        
        String fromfile = Files.asCharSource(newFile, Charsets.UTF_8).read();
        assertEquals(fromfile, "abc");
    }
    
    @Test
    public void testExists() throws Exception {
        File newFile = new File(dir, "mementoFileWriterSyncTest-"+Identifiers.makeRandomId(4)+".txt");
        writer = new MementoFileWriterSync<String>(newFile, serializer);
        assertFalse(writer.exists());
        
        writer.write("abc");
        assertTrue(writer.exists());
    }
    
    @Test
    public void testDeletesFile() throws Exception {
        writer.delete();
        assertFalse(file.exists());
    }

    @Test
    public void testAppendsFile() throws Exception {
        writer.append("abc\n");
        writer.append("def\n");
        
        String fromfile = Files.asCharSource(file, Charsets.UTF_8).read();
        assertEquals(fromfile, "abc\ndef\n");
    }
    
    @Test
    public void testWriteBacklogThenDeleteWillLeaveFileDeleted() throws Exception {
        String big = makeBigString(100000);
        
        writer.write(big);
        writer.write(big);
        writer.delete();
        assertFalse(file.exists());
    }
    
    private String makeBigString(int size) {
        return com.google.common.base.Strings.repeat("x", size);
    }
}
