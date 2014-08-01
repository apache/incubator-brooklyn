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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.file.ArchiveUtils.ArchiveType;
import brooklyn.util.os.Os;

import com.google.common.annotations.Beta;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

/**
 * Build a Zip or Jar archive.
 * <p>
 * Supports creating temporary archives that will be deleted on exit, if no name is
 * specified. The created file must be a Java archive type, with the extension {@code .zip},
 * {@code .jar}, {@code .war} or {@code .ear}.
 * <p>
 * Example:
 * <pre> File zip = ArchiveBuilder.archive("data/archive.zip")
 *         .addAt(new File("./pom.xml"), "")
 *         .addDirContentsAt(new File("./src"), "src/")
 *         .addAt(new File("/tmp/Extra.java"), "src/main/java/")
 *         .addDirContentsAt(new File("/tmp/overlay/"), "")
 *         .create();
 * </pre>
 * <p>
 */
@Beta
public class ArchiveBuilder {

    /**
     * Create an {@link ArchiveBuilder} for an archive with the given name.
     */
    public static ArchiveBuilder archive(String archive) {
        return new ArchiveBuilder(archive);
    }

    /**
     * Create an {@link ArchiveBuilder} for a {@link ArchiveType#ZIP Zip} format archive.
     */
    public static ArchiveBuilder zip() {
        return new ArchiveBuilder(ArchiveType.ZIP);
    }

    /**
     * Create an {@link ArchiveBuilder} for a {@link ArchiveType#JAR Jar} format archive.
     */
    public static ArchiveBuilder jar() {
        return new ArchiveBuilder(ArchiveType.JAR);
    }

    private final ArchiveType type;
    private File archive;
    private Manifest manifest;
    private Multimap<String, File> entries = LinkedHashMultimap.create();

    private ArchiveBuilder() {
        this(ArchiveType.ZIP);
    }

    private ArchiveBuilder(String filename) {
        this(ArchiveType.of(filename));

        named(filename);
    }

    private ArchiveBuilder(ArchiveType type) {
        checkNotNull(type);
        checkArgument(ArchiveType.ZIP_ARCHIVES.contains(type));

        this.type = type;
        this.manifest = new Manifest();
    }

    /**
     * Set the location of the generated archive file.
     */
    public ArchiveBuilder named(String name) {
        checkNotNull(name);
        String ext = Files.getFileExtension(name);
        if (ext.isEmpty()) {
            name = name + "." + type.toString();
        } else if (type != ArchiveType.of(name)) {
            throw new IllegalArgumentException(String.format("Extension for '%s' did not match archive type of %s", ext, type));
        }
        this.archive = new File(Os.tidyPath(name));
        return this;
    }

    /**
     * @see #named(String)
     */
    public ArchiveBuilder named(File file) {
        checkNotNull(file);
        return named(file.getPath());
    }

    /**
     * Add a manifest entry with the given {@code key} and {@code value}.
     */
    public ArchiveBuilder manifest(Object key, Object value) {
        checkNotNull(key, "key");
        checkNotNull(value, "value");
        manifest.getMainAttributes().put(key, value);
        return this;
    }

    /**
     * Add the file located at the {@code filePath} to the archive, 
     * with some complicated base-name strategies.
     *
     * @deprecated since 0.7.0 use one of the other add methods which makes the strategy explicit */ @Deprecated
    public ArchiveBuilder add(String filePath) {
        checkNotNull(filePath, "filePath");
        return add(new File(Os.tidyPath(filePath)));
    }

    /**
     * Add the {@code file} to the archive.
     * <p>
     * If the file path is absolute, or points to a file above the current directory,
     * the file is added to the archive as a top-level entry, using the file name only.
     * For relative {@code filePath}s below the current directory, the file is added
     * using the path given and is assumed to be located relative to the current
     * working directory.
     * <p>
     * No checks for file existence are made at this stage.
     *
     * @see #entry(String, File)
     * @deprecated since 0.7.0 use one of the other add methods which makes the strategy explicit */ @Deprecated
    public ArchiveBuilder add(File file) {
        checkNotNull(file, "file");
        String filePath = Os.tidyPath(file.getPath());
        if (file.isAbsolute() || filePath.startsWith("../")) {
            return entry(Os.mergePaths(".", file.getName()), file);
        } else {
            return entry(Os.mergePaths(".", filePath), file);
        }
    }

    /**
     * Add the file located at the {@code fileSubPath}, relative to the {@code baseDir} on the local system,
     * to the archive.
     * <p>
     * Uses the {@code fileSubPath} as the name of the file in the archive. Note that the
     * file is found by concatenating the two path components using {@link Os#mergePaths(String...)},
     * thus {@code fileSubPath} should not be absolute or point to a location above the current directory.
     * <p>
     * Use {@link #entry(String, String)} directly or {@link #entries(Map)} for complete
     * control over file locations and names in the archive.
     *
     * @see #entry(String, String)
     */
    public ArchiveBuilder addFromLocalBaseDir(File baseDir, String fileSubPath) {
        checkNotNull(baseDir, "baseDir");
        checkNotNull(fileSubPath, "filePath");
        return entry(Os.mergePaths(".", fileSubPath), Os.mergePaths(baseDir.getPath(), fileSubPath));
    }
    /** @deprecated since 0.7.0 use {@link #addFromLocalBaseDir(File, String)}, or
     * one of the other add methods if adding relative to baseDir was not intended */ @Deprecated
    public ArchiveBuilder addFromLocalBaseDir(String baseDir, String fileSubPath) {
        return addFromLocalBaseDir(new File(baseDir), fileSubPath);
    }
    /** @deprecated since 0.7.0 use {@link #addFromLocalBaseDir(File, String)}, or
     * one of the other add methods if adding relative to baseDir was not intended */ @Deprecated
    public ArchiveBuilder add(String baseDir, String fileSubPath) {
        return addFromLocalBaseDir(baseDir, fileSubPath);
    }
     
    /** adds the given file to the archive, preserving its name but putting under the given directory in the archive (may be <code>""</code> or <code>"./"</code>) */
    public ArchiveBuilder addAt(File file, String archiveParentDir) {
        checkNotNull(archiveParentDir, "archiveParentDir");
        checkNotNull(file, "file");
        return entry(Os.mergePaths(archiveParentDir, file.getName()), file);
    }

    /**
     * Add the contents of the directory named {@code dirName} to the archive.
     *
     * @see #addDir(File)
     * @deprecated since 0.7.0 use {@link #addDirContentsAt(File, String) */ @Deprecated
    public ArchiveBuilder addDir(String dirName) {
        checkNotNull(dirName, "dirName");
        return addDir(new File(Os.tidyPath(dirName)));
    }

    /**
     * Add the contents of the directory {@code dir} to the archive.
     * The directory's name is not included; use {@link #addAtRoot(File)} if you want that behaviour. 
     * <p>
     * Uses {@literal .} as the parent directory name for the contents.
     *
     * @see #entry(String, File)
     */
    public ArchiveBuilder addDirContentsAt(File dir, String archiveParentDir) {
        checkNotNull(dir, "dir");
        if (!dir.isDirectory()) throw new IllegalArgumentException(dir+" is not a directory; cannot add contents to archive");
        return entry(archiveParentDir, dir);
    }
    /**
     * As {@link #addDirContentsAt(File, String)}, 
     * using {@literal .} as the parent directory name for the contents.
     * 
     * @deprecated since 0.7.0 use {@link #addDirContentsAt(File, String)
     * to clarify API, argument types, and be explicit about where it should be installed,
     * because JARs seem to require <code>""<code> whereas ZIPs might want <code>"./"</code>. */ @Deprecated
    public ArchiveBuilder addDir(File dir) {
        return addDirContentsAt(dir, ".");
    }

    /**
     * Add the collection of {@code files} to the archive.
     *
     * @see #add(String)
     * @deprecated since 0.7.0 use one of the other add methods if keeping this file's path was not intended */ @Deprecated
    public ArchiveBuilder add(Iterable<String> files) {
        checkNotNull(files, "files");
        for (String filePath : files) {
            add(filePath);
        }
        return this;
    }

    /**
     * Add the collection of {@code files}, relative to the {@code baseDir}, to
     * the archive.
     *
     * @see #add(String, String)
     * @deprecated since 0.7.0 use one of the other add methods if keeping this file's path was not intended */ @Deprecated
    public ArchiveBuilder add(String baseDir, Iterable<String> files) {
        checkNotNull(baseDir, "baseDir");
        checkNotNull(files, "files");
        for (String filePath : files) {
            add(baseDir, filePath);
        }
        return this;
    }

    /**
     * Add the {@code file} to the archive with the path {@code entryPath}.
     *
     * @see #entry(String, File)
     */
    public ArchiveBuilder entry(String entryPath, String filePath) {
        checkNotNull(entryPath, "entryPath");
        checkNotNull(filePath, "filePath");
        return entry(entryPath, new File(filePath));
    }

    /**
     * Add the {@code file} to the archive with the path {@code entryPath}.
     */
    public ArchiveBuilder entry(String entryPath, File file) {
        checkNotNull(entryPath, "entryPath");
        checkNotNull(file, "file");
        this.entries.put(entryPath, file);
        return this;
    }

    /**
     * Add a {@link Map} of entries to the archive.
     * <p>
     * The keys should be the names of the file entries to be added to the archive and
     * the value should point to the actual {@link File} to be added.
     * <p>
     * This allows complete control over the directory structure of the eventual archive,
     * as the entry names do not need to bear any relationship to the name or location
     * of the files on the filesystem.
     */
    public ArchiveBuilder entries(Map<String, File> entries) {
        checkNotNull(entries, "entries");
        for (Map.Entry<String, File> entry: entries.entrySet())
            this.entries.put(entry.getKey(), entry.getValue());
        return this;
    }

    /**
     * Generates the archive and outputs it to the given stream, ignoring any file name.
     * <p>
     * This will add a manifest file if the type is a Jar archive.
     */
    public void stream(OutputStream output) {
        try {
            ZipOutputStream target;
            if (type == ArchiveType.ZIP) {
                target = new ZipOutputStream(output);
            } else {
                manifest(Attributes.Name.MANIFEST_VERSION, "1.0");
                target = new JarOutputStream(output, manifest);
            }
            for (String entry : entries.keySet()) {
                addToArchive(entry, entries.get(entry), target);
            }
            target.close();
        } catch (IOException ioe) {
            throw Exceptions.propagate(ioe);
        }
    }

    /**
     * Generates the archive, saving it with the given name.
     */
    public File create(String archiveFile) {
        return named(archiveFile).create();
    }

    /**
     * Generates the archive.
     * <p>
     * If no name has been specified, the archive will be created as a temporary file with
     * a unique name, that is deleted on exit. Otherwise, the given name will be used.
     */
    public File create() {
        if (archive == null) {
            File temp = Os.newTempFile("brooklyn-archive", type.toString());
            temp.deleteOnExit();
            named(temp);
        }
        try {
            OutputStream output = new FileOutputStream(archive);
            stream(output);
            output.close();
        } catch (IOException ioe) {
            throw Exceptions.propagate(ioe);
        }
        return archive;
    }

    /**
     * Recursively add files to the archive.
     * <p>
     * Code adapted from this <a href="http://stackoverflow.com/questions/1281229/how-to-use-jaroutputstream-to-create-a-jar-file">example</a>
     * <p>
     * <strong>Note</strong> {@link File} provides no support for symbolic links, and as such there is
     * no way to ensure that a symbolic link to a directory is not followed when traversing the
     * tree. In this case, iterables created by this traverser could contain files that are
     * outside of the given directory or even be infinite if there is a symbolic link loop.
     */
    private void addToArchive(String path, Iterable<File> sources, ZipOutputStream target) throws IOException {
        int size = Iterables.size(sources);
        if (size==0) return;
        boolean isDirectory;
        if (size>1) {
            // it must be directories if we are putting multiple things here 
            isDirectory = true;
        } else {
            isDirectory = Iterables.getOnlyElement(sources).isDirectory();
        }
        
        String name = path.replace("\\", "/");
        if (isDirectory) {
            name += "/";
            JarEntry entry = new JarEntry(name);
            
            long lastModified=-1;
            for (File source: sources)
                if (source.lastModified()>lastModified)
                    lastModified = source.lastModified();
            
            entry.setTime(lastModified);
            target.putNextEntry(entry);
            target.closeEntry();

            for (File source: sources) {
                if (!source.isDirectory()) {
                    throw new IllegalStateException("Cannot add multiple items at a path in archive unless they are directories: "+sources+" at "+path+" is not valid.");
                }
                Iterable<File> children = Files.fileTreeTraverser().children(source);
                for (File child : children) {
                    addToArchive(Os.mergePaths(path, child.getName()), Collections.singleton(child), target);
                }
            }
            return;
        }

        File source = Iterables.getOnlyElement(sources);
        JarEntry entry = new JarEntry(name);
        entry.setTime(source.lastModified());
        target.putNextEntry(entry);
        ByteStreams.copy(Files.newInputStreamSupplier(source), target);
        target.closeEntry();
    }
    

}
