package brooklyn.entity.java;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import com.google.common.io.ByteStreams;

public class JarBuilder {

    // Code adapted from example in http://stackoverflow.com/questions/1281229/how-to-use-jaroutputstream-to-create-a-jar-file
    // but modified so that given an absolute dir the entries in the jar file will all be relative.
    
    public static File buildJar(File dir) throws IOException {
        File result = File.createTempFile("brooklyn-built", ".jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        JarOutputStream target = new JarOutputStream(new FileOutputStream(result), manifest);
        add(dir, new File(""), target);
        target.close();
        return result;
    }

    // TODO Probably doesn't handle symbolic links etc
    private static void add(File rootDir, File relativeSource, JarOutputStream target) throws IOException {
        BufferedInputStream in = null;
        try {
            File absoluteSource = new File(rootDir.getAbsolutePath()+File.separator+relativeSource.getPath());
            if (absoluteSource.isDirectory()) {
                String name = relativeSource.getPath().replace("\\", "/");
                if (!name.isEmpty()) {
                    if (!name.endsWith("/"))
                        name += "/";
                    JarEntry entry = new JarEntry(name);
                    entry.setTime(relativeSource.lastModified());
                    target.putNextEntry(entry);
                    target.closeEntry();
                }
                for (File nestedFile : absoluteSource.listFiles()) {
                    String relativeNestedFileStr = nestedFile.toString().substring(rootDir.toString().length());
                    if (relativeNestedFileStr.startsWith(File.separator)) relativeNestedFileStr = relativeNestedFileStr.substring(File.separator.length());
                    File relativeNestedFile = new File(relativeNestedFileStr);
                    add(rootDir, relativeNestedFile, target);
                }
                return;
            }

            JarEntry entry = new JarEntry(relativeSource.getPath().replace("\\", "/"));
            entry.setTime(relativeSource.lastModified());
            target.putNextEntry(entry);
            in = new BufferedInputStream(new FileInputStream(absoluteSource));

            ByteStreams.copy(in, target);
            target.closeEntry();
        } finally {
            if (in != null) in.close();
        }
    }
}
