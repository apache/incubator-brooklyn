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
package brooklyn.util.os;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableSet;

@Test
public class OsTest {

    private static final Logger log = LoggerFactory.getLogger(OsTest.class);
    
    public void testTmp() {
        log.info("tmp dir is: "+Os.tmp());
        Assert.assertNotNull(Os.tmp());
    }
    
    public void testHome() {
        log.info("home dir is: "+Os.home());
        Assert.assertNotNull(Os.home());        
    }
    
    public void testUser() {
        log.info("user name is: "+Os.user());
        Assert.assertNotNull(Os.user());        
    }

    public void testTidyPathCanonicalize() throws Exception {
        for (String path : ImmutableSet.of("/a/b", "//a///b", "/a/b/", "/a/b/.", "/q/../a/b")) {
            assertEquals(Os.tidyPath(path), "/a/b");
        }
    }

    public void testTidyPathSimplify() throws Exception {
        assertEquals(Os.tidyPath("x/y/z"), "x/y/z");
        assertEquals(Os.tidyPath(""), ".");
        assertEquals(Os.tidyPath("."), ".");
        assertEquals(Os.tidyPath(".."), "..");
        assertEquals(Os.tidyPath("./x"), "x");
        assertEquals(Os.tidyPath("../x"), "../x");
        assertEquals(Os.tidyPath("/.."), "/");
        assertEquals(Os.tidyPath("x"), "x");
        assertEquals(Os.tidyPath("/"), "/");
        assertEquals(Os.tidyPath("///"), "/");
        assertEquals(Os.tidyPath("/x\\"), "/x\\");
        assertEquals(Os.tidyPath("/x\\y/.."), "/");
    }

    public void testTidyPathHome() throws Exception {
        String userhome = System.getProperty("user.home");
        assertEquals(Os.tidyPath("~/a/b"), userhome+"/a/b");
        assertEquals(Os.tidyPath("~"), userhome);
        assertEquals(Os.tidyPath("/a/~/b"), "/a/~/b");
    }
    
    public void testMergePaths() throws Exception {
        assertEquals(Os.mergePaths("a"), "a"); 
        assertEquals(Os.mergePaths("a", "b"), "a/b"); 
        assertEquals(Os.mergePaths("a/", "b"), "a/b");
        assertEquals(Os.mergePaths("a", "b/"), "a/b/");
        assertEquals(Os.mergePaths("/a", "b"), "/a/b");
    }
    
    @Test(groups="Integration")
    public void testNewTempFile() {
        int CREATE_CNT = 5000;
        Collection<File> folders = new ArrayList<File>(CREATE_CNT);
        
        try {
            for (int i = 0; i < CREATE_CNT; i++) {
                try {
                    folders.add(Os.newTempFile(OsTest.class, "test"));
                } catch (IllegalStateException e) {
                    log.warn("testNewTempFile failed at " + i + " iteration.");
                    Exceptions.propagate(e);
                }
            }
        } finally {
            //cleanup
            for (File folder : folders) {
                folder.delete();
            }
        }
    }
    
    @Test(groups="Integration")
    public void testNewTempDir() {
        int CREATE_CNT = 5000;
        Collection<File> folders = new ArrayList<File>(CREATE_CNT);
        
        try {
            for (int i = 0; i < CREATE_CNT; i++) {
                try {
                    folders.add(Os.newTempDir(OsTest.class));
                } catch (IllegalStateException e) {
                    log.warn("testNewTempDir failed at " + i + " iteration.");
                    Exceptions.propagate(e);
                }
            }
        } finally {
            //cleanup
            for (File folder : folders) {
                folder.delete();
            }
        }
    }

}
