/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.util.file;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.file.ArchiveUtils.ArchiveType;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;

import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

/**
 * Build a Zip or Jar archive.
 * <p>
 * Supports creating temporary archives that will be deleted on exit, if no name is
 * specified. The created file must be a Java archive type, with the extension {@code .zip},
 * {@code .jar}, {@code .war} or {@code .ear}.
 * <p>
 * Examples:
 * <pre> File zip = ArchiveBuilder.archive("data/archive.zip")
 *         .entry("src", applicationDir + "/deploy/" + version + "/src/")
 *         .entry("lib", applicationDir + "/deploy/" + version + "/lib/")
 *         .entry("etc/config.ini", applicationDir + "/config.ini")
 *         .create();
 * </pre>
 * <pre> OutputStream remote = ...;
 * Map&lt;String, File&gt; entries = ...;
 * ArchiveBuilder.zip()
 *         .add("resources/data.csv")
 *         .addAll(entries)
 *         .stream(remote);
 * </pre>
 */
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

    private ArchiveType type;
    private File archive;
    private Manifest manifest;
    private Map<String, File> entries = Maps.newHashMap();

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
     * Set the name and location of the generated archive file.
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
     * Add a manifest entry with the given {@literal key} and {@literal value}.
     */
    public ArchiveBuilder manifest(Object key, Object value) {
        checkNotNull(key, "key");
        checkNotNull(value, "value");
        manifest.getMainAttributes().put(key, value);
        return this;
    }

    /**
     * Add the file located at the {@literal filePath} to the archive.
     *
     * @see #add(File)
     */
    public ArchiveBuilder add(String filePath) {
        checkNotNull(filePath, "filePath");
        return add(new File(Os.tidyPath(filePath)));
    }

    /**
     * Add {@literal file} to the archive.
     * <p>
     * If the file path is absolute, the file is added as a top-level entry
     * to the archive using the file name only. For relative {@literal filePath}s the
     * file is added using the path given and is assumed to be located relative to
     * the current working directory. No checks for file existence are made at this
     * stage.
     *
     * @see #entry(String, File)
     */
    public ArchiveBuilder add(File file) {
        checkNotNull(file, "file");
        if (file.isAbsolute()) {
            return entry(Os.mergePaths(".", file.getName()), file.getAbsoluteFile());
        } else {
            return entry(Os.mergePaths(".", file.getPath()), file);
        }
    }

    /**
     * Add the file located at the {@literal filePath}, relative to the {@literal baseDir},
     * to the archive.
     * <p>
     * Uses the {@literal filePath} as the name of the file in the archive. Note that the
     * two path components are simply concatenated using {@link Os#mergePaths(String...)}
     * which may not behave as expected if the {@literal filePath} is absolute. Use the
     * {@link #entry(String, File)} or {@link #entries(Map)} methods for complete control
     * over file locations and names.
     *
     * @see #entry(String, String)
     */
    public ArchiveBuilder add(String baseDir, String filePath) {
        checkNotNull(baseDir, "baseDir");
        checkNotNull(filePath, "filePath");
        return entry(Os.mergePaths(".", filePath), Os.mergePaths(baseDir, filePath));
    }

    /**
     * Add the contents of the directory named {@literal dirName} to the archive.
     *
     * @see #addDir(File)
     */
    public ArchiveBuilder addDir(String dirName) {
        checkNotNull(dirName, "dirName");
        return addDir(new File(Os.tidyPath(dirName)));
    }

    /**
     * Add the contents of the directory {@literal dir} to the archive.
     * <p>
     * Uses {@literal .} as the parent directory name for the contents.
     *
     * @see #entry(String, File)
     */
    public ArchiveBuilder addDir(File dir) {
        checkNotNull(dir, "dir");
        return entry(".", dir);
    }

    /**
     * Add the collection of {@literal files} to the archive.
     *
     * @see #add(String)
     */
    public ArchiveBuilder add(Iterable<String> files) {
        checkNotNull(files, "files");
        for (String filePath : files) {
            add(filePath);
        }
        return this;
    }

    /**
     * Add the collection of {@literal files}, relative to the {@literal baseDir}, to
     * the archive.
     *
     * @see #add(String, String)
     */
    public ArchiveBuilder add(String baseDir, Iterable<String> files) {
        checkNotNull(baseDir, "baseDir");
        checkNotNull(files, "files");
        for (String filePath : files) {
            add(baseDir, filePath);
        }
        return this;
    }

    /**
     * Add the {@literal file} to the archive with the path {@literal entryPath}.
     *
     * @see #entry(String, File)
     */
    public ArchiveBuilder entry(String entryPath, String filePath) {
        checkNotNull(entryPath, "entryPath");
        checkNotNull(filePath, "filePath");
        return entry(entryPath, new File(filePath));
    }

    /**
     * Add the {@literal file} to the archive with the path {@literal entryPath}.
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
     * the associated {@link File} value should point to the actual file to add. This
     * allows complete control over the directory structure of the eventual archive, as
     * the file names do not need to bear any relationship to the location of the files
     * on the filesystem.
     */
    public ArchiveBuilder entries(Map<String, File> entries) {
        checkNotNull(entries, "entries");
        this.entries.putAll(entries);
        return this;
    }

    /**
     * Generates the archive and ouputs it to the given stream, ignoring any file name.
     * <p>
     * This will add a manifest filw if the type is a Jar archive.
     */
    public void stream(OutputStream output) {
        try {
            JarOutputStream target;
            if (type == ArchiveType.JAR) { // TODO what about War and Ear types?
                manifest(Attributes.Name.MANIFEST_VERSION, "1.0");
                target = new JarOutputStream(output, manifest);
            } else {
                target = new JarOutputStream(output);
            }
            for (String entry : entries.keySet()) {
                entry(entry, entries.get(entry), target);
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
     * If no name has been specified, the archive will be created as a tenporary file with
     * a unique name, that is deleted on exit. Otherwise, the given name will be used.
     */
    public File create() {
        if (archive == null) {
            File temp = new File(Os.tmp(), "brooklyn-archive-" + Identifiers.makeRandomId(6));
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

    // Code adapted from example in http://stackoverflow.com/questions/1281229/how-to-use-jaroutputstream-to-create-a-jar-file
    // but modified so that given an absolute dir the entries in the jar file will all be relative.
    // TODO Probably doesn't handle symbolic links etc

    private void entry(String path, File source, JarOutputStream target) throws IOException {
        String name = path.replace("\\", "/");
        if (source.isDirectory()) {
            name += "/";
            JarEntry entry = new JarEntry(name);
            entry.setTime(source.lastModified());
            target.putNextEntry(entry);
            target.closeEntry();

            Iterable<File> children = Files.fileTreeTraverser().children(source);
            for (File child : children) {
                entry(Os.mergePaths(path, child.getName()), child, target);
            }
            return;
        }

        JarEntry entry = new JarEntry(name);
        entry.setTime(source.lastModified());
        target.putNextEntry(entry);
        ByteStreams.copy(Files.newInputStreamSupplier(source), target);
        target.closeEntry();
    }
}
