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
package brooklyn.util.file;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableSet;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

/**
 * Test the operation of the {@link ArchiveBuilder} class.
 */
@Test
public class ArchiveBuilderTest {

    private File parentDir, tmpDir, tmpDir2;
    private Predicate<ZipEntry> isDirectory = new Predicate<ZipEntry>() {
                @Override
                public boolean apply(@Nullable ZipEntry input) {
                    return input.isDirectory();
                }
            };

    @BeforeClass
    public void createTmpDirAndFiles() throws IOException {
        parentDir = Os.newTempDir(getClass().getSimpleName());
        Os.deleteOnExitRecursively(parentDir);
        tmpDir = new File(parentDir, Identifiers.makeRandomId(4));
        Os.mkdirs(tmpDir);
        Files.write("abcdef", new File(tmpDir, "data01.txt"), Charsets.US_ASCII);
        Files.write("123456", new File(tmpDir, "data02.txt"), Charsets.US_ASCII);
        Files.write("qqqqqq", new File(tmpDir, "data03.txt"), Charsets.US_ASCII);
        
        tmpDir2 = new File(parentDir, Identifiers.makeRandomId(4));
        Os.mkdirs(tmpDir2);
        Files.write("zzzzzz", new File(tmpDir2, "data04.txt"), Charsets.US_ASCII);
    }
    
    @Test
    public void testCreateZipFromDir() throws Exception {
        File archive = ArchiveBuilder.zip().addDirContentsAt(tmpDir, ".").create();
        archive.deleteOnExit();

        List<ZipEntry> entries = Lists.newArrayList();
        ZipInputStream input = new ZipInputStream(new FileInputStream(archive));
        ZipEntry entry = input.getNextEntry();
        while (entry != null) {
            entries.add(entry);
            entry = input.getNextEntry();
        }
        assertEquals(entries.size(), 4);
        Iterable<ZipEntry> directories = Iterables.filter(entries, isDirectory);
        Iterable<ZipEntry> files = Iterables.filter(entries, Predicates.not(isDirectory));
        assertEquals(Iterables.size(directories), 1);
        assertEquals(Iterables.size(files), 3);
        String dirName = Iterables.getOnlyElement(directories).getName();
        assertEquals(dirName, "./");
        
        Set<String> names = MutableSet.of();
        for (ZipEntry file : files) {
            assertTrue(file.getName().startsWith(dirName));
            names.add(file.getName());
        }
        assertTrue(names.contains("./data01.txt"));
        assertFalse(names.contains("./data04.txt"));
        input.close();
    }

    @Test
    public void testCreateZipFromTwoDirs() throws Exception {
        File archive = ArchiveBuilder.zip().addDirContentsAt(tmpDir, ".").addDirContentsAt(tmpDir2, ".").create();
        archive.deleteOnExit();

        List<ZipEntry> entries = Lists.newArrayList();
        ZipInputStream input = new ZipInputStream(new FileInputStream(archive));
        ZipEntry entry = input.getNextEntry();
        while (entry != null) {
            entries.add(entry);
            entry = input.getNextEntry();
        }
        assertEquals(entries.size(), 5);
        Iterable<ZipEntry> directories = Iterables.filter(entries, isDirectory);
        Iterable<ZipEntry> files = Iterables.filter(entries, Predicates.not(isDirectory));
        assertEquals(Iterables.size(directories), 1);
        assertEquals(Iterables.size(files), 4);
        String dirName = Iterables.getOnlyElement(directories).getName();
        assertEquals(dirName, "./");
        
        Set<String> names = MutableSet.of();
        for (ZipEntry file : files) {
            assertTrue(file.getName().startsWith(dirName));
            names.add(file.getName());
        }
        assertTrue(names.contains("./data01.txt"));
        assertTrue(names.contains("./data04.txt"));
        input.close();
    }
    @Test
    public void testCreateZipFromFiles() throws Exception {
        ArchiveBuilder builder = ArchiveBuilder.zip();
        for (String fileName : Arrays.asList("data01.txt", "data02.txt", "data03.txt")) {
            builder.addAt(new File(tmpDir, fileName), ".");
        }
        File archive = builder.create();
        archive.deleteOnExit();

        List<ZipEntry> entries = Lists.newArrayList();
        ZipInputStream input = new ZipInputStream(new FileInputStream(archive));
        ZipEntry entry = input.getNextEntry();
        while (entry != null) {
            entries.add(entry);
            entry = input.getNextEntry();
        }
        assertEquals(entries.size(), 3);
        Iterable<ZipEntry> directories = Iterables.filter(entries, isDirectory);
        Iterable<ZipEntry> files = Iterables.filter(entries, Predicates.not(isDirectory));
        assertTrue(Iterables.isEmpty(directories));
        assertEquals(Iterables.size(files), 3);
        for (ZipEntry file : files) {
            assertTrue(file.getName().startsWith(Os.mergePathsUnix(".", "data")));
        }
        input.close();
    }

    @Test
    public void testCreateZipFromFilesWithBaseDir() throws Exception {
        ArchiveBuilder builder = ArchiveBuilder.zip();
        String baseDir = tmpDir.getName();
        for (String fileName : Arrays.asList("data01.txt", "data02.txt", "data03.txt")) {
            builder.addFromLocalBaseDir(parentDir, Os.mergePaths(baseDir, fileName));
        }
        File archive = builder.create();
        archive.deleteOnExit();

        List<ZipEntry> entries = Lists.newArrayList();
        ZipInputStream input = new ZipInputStream(new FileInputStream(archive));
        ZipEntry entry = input.getNextEntry();
        while (entry != null) {
            entries.add(entry);
            entry = input.getNextEntry();
        }
        assertEquals(entries.size(), 3);
        Iterable<ZipEntry> directories = Iterables.filter(entries, isDirectory);
        Iterable<ZipEntry> files = Iterables.filter(entries, Predicates.not(isDirectory));
        assertTrue(Iterables.isEmpty(directories));
        assertEquals(Iterables.size(files), 3);
        for (ZipEntry file : files) {
            assertTrue(file.getName().startsWith(Os.mergePathsUnix(".", baseDir)));
        }
        input.close();
    }

}
