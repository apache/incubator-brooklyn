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
package brooklyn.qa.performance;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.persister.FileBasedStoreObjectAccessor;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.ssh.process.ProcessTool;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public class FilePersistencePerformanceTest extends AbstractPerformanceTest {

    File file;
    FileBasedStoreObjectAccessor fileAccessor;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();

        file = File.createTempFile("fileBasedStoreObject", ".txt");
        Files.write("initial", file, Charsets.UTF_8);
        fileAccessor = new FileBasedStoreObjectAccessor(file, "mytmpextension");
        
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (file != null) file.delete();
    }
    
    protected int numIterations() {
        return 100;
    }
    
     @Test(groups={"Integration", "Acceptance"})
     public void testFileBasedStoreObjectPuts() throws Exception {
         int numIterations = numIterations();
         double minRatePerSec = 100 * PERFORMANCE_EXPECTATION;
         final AtomicInteger i = new AtomicInteger();
         
         measureAndAssert("FileBasedStoreObjectAccessor.put", numIterations, minRatePerSec, new Runnable() {
             public void run() {
                 fileAccessor.put(""+i.incrementAndGet());
             }});
     }
 
     @Test(groups={"Integration", "Acceptance"})
     public void testFileBasedStoreObjectGet() throws Exception {
         // The file system will have done a lot of caching here - we are unlikely to touch the disk more than once.
         int numIterations = numIterations();
         double minRatePerSec = 100 * PERFORMANCE_EXPECTATION;

         measureAndAssert("FileBasedStoreObjectAccessor.get", numIterations, minRatePerSec, new Runnable() {
             public void run() {
                 fileAccessor.get();
             }});
     }
 
     @Test(groups={"Integration", "Acceptance"})
     public void testFileBasedStoreObjectDelete() throws Exception {
         int numIterations = numIterations();
         double minRatePerSec = 100 * PERFORMANCE_EXPECTATION;

         // Will do 10% warm up runs first
         final List<File> files = Lists.newArrayList();
         for (int i = 0; i < (numIterations * 1.1 + 1); i++) {
             file = File.createTempFile("fileBasedStoreObjectDelete-"+i, ".txt");
             Files.write(""+i, file, Charsets.UTF_8);
             files.add(file);
         }
         
         final AtomicInteger i = new AtomicInteger();

         try {
             measureAndAssert("FileBasedStoreObjectAccessor.delete", numIterations, minRatePerSec, new Runnable() {
                 public void run() {
                     File file = files.get(i.getAndIncrement());
                     FileBasedStoreObjectAccessor fileAccessor = new FileBasedStoreObjectAccessor(file, "mytmpextension");
                     fileAccessor.delete();
                 }});
         } finally {
             for (File file : files) {
                 if (file != null) file.delete();
             }
         }
     }
 
     // fileAccessor.put() is implemented with an execCommands("mv") so look at performance of just that piece
     @Test(groups={"Integration", "Acceptance"})
     public void testProcessToolExecCommand() {
         int numIterations = numIterations();
         double minRatePerSec = 10 * PERFORMANCE_EXPECTATION;
         
         measureAndAssert("ProcessTool.exec", numIterations, minRatePerSec, new Runnable() {
             public void run() {
                 String cmd = "true";
                 new ProcessTool().execCommands(MutableMap.<String,String>of(), MutableList.of(cmd), null);
             }});
     }
     
     @Test(groups={"Integration", "Acceptance"})
     public void testJavaUtilFileRenames() {
         int numIterations = numIterations();
         double minRatePerSec = 10 * PERFORMANCE_EXPECTATION;

         final File parentDir = file.getParentFile();
         final AtomicInteger i = new AtomicInteger();
         
         measureAndAssert("java.util.File.rename", numIterations, minRatePerSec, new Runnable() {
             public void run() {
                 File newFile = new File(parentDir, "fileRename-"+i.incrementAndGet()+".txt");
                 file.renameTo(newFile);
                 file = newFile;
             }});
     }
     
     @Test(groups={"Integration", "Acceptance"})
     public void testGuavaFileWrites() {
         int numIterations = numIterations();
         double minRatePerSec = 10 * PERFORMANCE_EXPECTATION;

         final AtomicInteger i = new AtomicInteger();
         
         measureAndAssert("guava.Files.write", numIterations, minRatePerSec, new Runnable() {
             public void run() {
                 try {
                     Files.write(""+i.incrementAndGet(), file, Charsets.UTF_8);
                 } catch (IOException e) {
                     throw Exceptions.propagate(e);
                 }
             }});
     }
     
     @Test(groups={"Integration", "Acceptance"})
     public void testGuavaFileMoves() {
         int numIterations = numIterations();
         double minRatePerSec = 10 * PERFORMANCE_EXPECTATION;

         final File parentDir = file.getParentFile();
         final AtomicInteger i = new AtomicInteger();
         
         measureAndAssert("guava.Files.move", numIterations, minRatePerSec, new Runnable() {
             public void run() {
                 File newFile = new File(parentDir, "fileRename-"+i.incrementAndGet()+".txt");
                 try {
                     Files.move(file, newFile);
                 } catch (IOException e) {
                     throw Exceptions.propagate(e);
                 }
                 file = newFile;
             }});
     }
}
