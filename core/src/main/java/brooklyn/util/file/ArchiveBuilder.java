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
import java.util.EnumSet;
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
 * Examples:
 * <pre> File zip = ArchiveBuilder.archive("data/archive.zip")
 *         .add("src", applicationDir + "/deploy/" + version + "/src/")
 *         .add("lib", applicationDir + "/deploy/" + version + "/lib/")
 *         .add("etc/config.ini", applicationDir + "/config.ini")
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

    public static ArchiveBuilder archive(String archive) {
        return new ArchiveBuilder(archive);
    }

    public static ArchiveBuilder zip() {
        return new ArchiveBuilder(ArchiveType.ZIP);
    }

    public static ArchiveBuilder jar() {
        return new ArchiveBuilder(ArchiveType.JAR);
    }

    private ArchiveType type;
    private File archive;
    private Manifest manifest;
    private Map<String, File> entries = Maps.newHashMap();

    public ArchiveBuilder() {
        this(ArchiveType.ZIP);
    }

    public ArchiveBuilder(String filename) {
        this(ArchiveType.of(filename));

        named(filename);
    }

    public ArchiveBuilder(ArchiveType type) {
        checkNotNull(type);
        checkArgument(EnumSet.of(ArchiveType.ZIP, ArchiveType.JAR).contains(type));

        this.type = type;
        this.manifest = new Manifest();
    }

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

    public ArchiveBuilder named(File file) {
        checkNotNull(file);
        return named(file.getPath());
    }

    public ArchiveBuilder manifest(Object key, Object value) {
        manifest.getMainAttributes().put(key, value);
        return this;
    }

    public ArchiveBuilder add(String file) {
        checkNotNull(file, "file");
        File path = new File(Os.tidyPath(file));
        if (path.isAbsolute()) {
            entries.put(path.getName(), path.getAbsoluteFile());
        } else {
            entries.put(file, path);
        }
        return this;
    }

    public ArchiveBuilder add(String base, String file) {
        checkNotNull(base, "base");
        checkNotNull(file, "file");
        File path = new File(Os.tidyPath(base), Os.tidyPath(file));
        entries.put(file, path);
        return this;
    }

    public ArchiveBuilder add(Iterable<String> files) {
        checkNotNull(files, "files");
        for (String file : files) {
            add(file);
        }
        return this;
    }

    public ArchiveBuilder add(String base, Iterable<String> files) {
        checkNotNull(base, "base");
        checkNotNull(files, "files");
        for (String file : files) {
            add(base, file);
        }
        return this;
    }

    public ArchiveBuilder addAll(Map<String, File> entries) {
        checkNotNull(entries, "entries");
        this.entries.putAll(entries);
        return this;
    }

    public void stream(OutputStream output) {
        try {
            JarOutputStream target;
            if (type == ArchiveType.JAR) {
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

    public File create(String archive) {
        return named(archive).create();
    }

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
