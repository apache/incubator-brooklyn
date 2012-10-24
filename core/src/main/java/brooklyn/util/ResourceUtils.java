package brooklyn.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URL;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.text.Strings;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

public class ResourceUtils {
    
    private static final Logger log = LoggerFactory.getLogger(ResourceUtils.class);

    ClassLoader loader = null;
    String context = null;
    Object contextObject = null;
    
    /** context string used for errors */
    public ResourceUtils(ClassLoader loader, Object contextObject, String contextMessage) {
        this.loader = loader;
        this.contextObject = contextObject;
        this.context = contextMessage;
    }
    /** contextObject used for classloading, contextMessage used for errors */
    public ResourceUtils(Object contextObject, String contextMessage) {
        this(contextObject==null ? null : 
            contextObject instanceof Class ? ((Class<?>)contextObject).getClassLoader() : 
                contextObject instanceof ClassLoader ? ((ClassLoader)contextObject) : 
                    contextObject.getClass().getClassLoader(), 
            contextObject, contextMessage);
    }
    /** uses the classloader of the given object, and the phrase object's toString (preceded by the word 'for') as the context string used in errors */
    public ResourceUtils(Object context) {
        this(context, Strings.toString(context));
    }
    
    public ClassLoader getLoader() {
        //TODO allow a sequence of loaders?
        return (loader!=null ? loader : getClass().getClassLoader());
    }
    
    /**
     * Takes a string which is treated as a URL (with some extended "schemes" also expected),
     * or as a path to something either on the classpath (absolute only) or the local filesystem (relative or absolute, depending on leading slash)
     * <p>
     * URLs can be of the form <b>classpath://com/acme/Foo.properties</b>
     * as well as <b>file:///home/...</b> and <b>http://acme.com/...</b>.
     * <p>
     * Throws exception if not found, using the context parameter passed into the constructor.
     * <p>
     * TODO may want OSGi, or typed object; should consider pax url
     * 
     * @return a stream, or throws exception (never returns null)
     */
    public InputStream getResourceFromUrl(String url) {
        try {
            if (url==null) throw new NullPointerException("Cannot read from null");
            if (url=="") throw new NullPointerException("Cannot read from empty string");
            String orig = url;
            if (url.startsWith("classpath:")) {
                try {
                    return getResourceViaClasspath(url);
                } catch (IOException e) {
                    //catch the above because both orig and modified url may be interesting
                    throw new IOException("Error accessing "+orig+": "+e, e);
                }
            }
            if (url.startsWith("sftp://")) {
                try {
                    return getResourceViaSftp(url);
                } catch (IOException e) {
                    throw new IOException("Error accessing "+orig+": "+e, e);
                }
            }
            if (url.matches("[A-Za-z][A-Za-z]+:.*")) {
                //looks like a URL - require two letters so we don't think e.g. c:/path/ is a url
                if (url.matches("file://[A-Za-z]:[/\\\\].*")) {
                    // file://c:/path/to/x is sometimes mistakenly supplied
                    // where file:///c:/path/to/x is the correct syntax.
                    // treat the former as the latter since the former doesn't have any other interpretation
                    if (log.isDebugEnabled())
                        log.debug("silently changing "+url+" to file:/// prefix");
                    url = "file:///"+url.substring(7);
                }
                return new URL(url).openStream();
            }

            try {
                //try as classpath reference, then as file
                URL u = getLoader().getResource(url);
                if (u!=null) return u.openStream();
                if (url.startsWith("/")) {
                    //some getResource calls fail if argument starts with /
                    String urlNoSlash = url;
                    while (urlNoSlash.startsWith("/")) urlNoSlash = urlNoSlash.substring(1);
                    u = getLoader().getResource(urlNoSlash);
                    if (u!=null) return u.openStream();
//                    //Class.getResource can require a /  (else it attempts to be relative) but Class.getClassLoader doesn't
//                    u = getLoader().getResource("/"+urlNoSlash);
//                    if (u!=null) return u.openStream();
                }
                File f = new File(url);
                if (f.exists()) return new FileInputStream(f);
            } catch (IOException e) {
                //catch the above because both u and modified url will be interesting
                throw new IOException("Error accessing "+orig+": "+e, e);
            }
            throw new IOException("'"+orig+"' not found on classpath or filesystem");
        } catch (Exception e) {
            if (context!=null)
                throw new RuntimeException("Error getting resource for "+context+": "+e, e);
            else throw new RuntimeException(e);
        }
    }

    private InputStream getResourceViaClasspath(String url) throws IOException {
        assert url.startsWith("classpath:");
        String subUrl = url.substring("classpath://".length());
        while (subUrl.startsWith("/")) subUrl = subUrl.substring(1);
        URL u = getLoader().getResource(subUrl);
        if (u!=null) return u.openStream();
        else throw new IOException(subUrl+" not found on classpath");
    }
    
    private InputStream getResourceViaSftp(String url) throws IOException {
        assert url.startsWith("sftp://");
        String subUrl = url.substring("sftp://".length());
        String user;
        String address;
        String path;
        int atIndex = subUrl.indexOf("@");
        int colonIndex = subUrl.indexOf(":", (atIndex > 0 ? atIndex : 0));
        if (colonIndex <= 0 || colonIndex <= atIndex) {
            throw new IllegalArgumentException("Invalid sftp url ("+url+"); IP or hostname must be specified, such as sftp://localhost:/path/to/file");
        }
        if (subUrl.length() <= (colonIndex+1)) {
            throw new IllegalArgumentException("Invalid sftp url ("+url+"); must specify path of remote file, such as sftp://localhost:/path/to/file");
        }
        if (atIndex >= 0) {
            user = subUrl.substring(0, atIndex);
        } else {
            user = null;
        }
        address = subUrl.substring(atIndex + 1, colonIndex);
        path = subUrl.substring(colonIndex+1);
        SshMachineLocation machine = new SshMachineLocation(MutableMap.builder()
                .putIfNotNull("user", user)
                .put("address", InetAddress.getByName(address))
                .build());
        try {
            File tempFile = File.createTempFile("brooklyn-sftp", ".tmp");
            tempFile.deleteOnExit();
            tempFile.setReadable(true, true);
            machine.copyFrom(path, tempFile.getAbsolutePath());
            return new FileInputStream(tempFile);
        } finally {
            Closeables.closeQuietly(machine);
        }
    }
    
    /** takes {@link #getResourceFromUrl(String)} and reads fully, into a string */
    public String getResourceAsString(String url) {
        try {
            return readFullyString(getResourceFromUrl(url));
        } catch (Exception e) {
            log.debug("ResourceUtils got error reading "+url+(context==null?"":" "+context)+" (rethrowing): "+e);
            throw Throwables.propagate(e);
        }
    }

    /** returns the base directory or JAR from which the context is class-loaded, if possible;
     * throws exception if not found */
    public String getClassLoaderDir() {
        if (contextObject==null) throw new IllegalArgumentException("No suitable context ("+context+") to auto-detect classloader dir");
        Class<?> cc = contextObject instanceof Class ? (Class<?>)contextObject : contextObject.getClass();
        return getClassLoaderDir(cc.getCanonicalName().replace('.', '/')+".class");
    }
    public String getClassLoaderDir(String resourceInThatDir) {
        resourceInThatDir = Strings.removeFromStart(resourceInThatDir, "/");
        URL url = getLoader().getResource(resourceInThatDir);
        if (url==null) throw new NoSuchElementException("Resource ("+resourceInThatDir+") not found");
        String urls = url.toString();

        boolean isJar = urls.startsWith("jar:");
        urls = Strings.removeFromStart(urls, "jar:");
        if (!urls.startsWith("file:")) throw new IllegalStateException("Resource ("+resourceInThatDir+") not on file system (at "+urls+")");
        urls = Strings.removeFromStart(urls, "file:");
        urls = Strings.removeFromStart(urls, "//");
        
        int i = urls.indexOf(resourceInThatDir);
        if (i==-1) throw new IllegalStateException("Resource path ("+resourceInThatDir+") not in url substring ("+urls+")");
        urls = urls.substring(0, i);
        
        if (isJar) {
            urls = Strings.removeFromEnd(urls, "/");
            if (!urls.endsWith("!")) throw new IllegalStateException("Context class url mismatch, is jar but does not have ! separator ("+urls+")");
            urls = Strings.removeFromEnd(urls, "!");
            if (!new File(urls).exists()) throw new IllegalStateException("Context class url substring ("+urls+") not found on filesystem");
        }
        return urls;
    }
    
    public static String readFullyString(InputStream is) throws IOException {
        return new String(readFullyBytes(is));
    }

    public static byte[] readFullyBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(is, out);
        return out.toByteArray();
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[1024];
        int bytesRead = input.read(buf);
        while (bytesRead != -1) {
            output.write(buf, 0, bytesRead);
            bytesRead = input.read(buf);
        }
        output.flush();
    }

    public static File writeToTempFile(InputStream is, String prefix, String suffix) {
        if (is == null) throw new NullPointerException("Input stream required to create temp file for "+prefix+"*"+suffix);
        File tempFile;
        try {
            tempFile = File.createTempFile(prefix, suffix);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        tempFile.deleteOnExit();

        OutputStream out = null;
        try {
            out = new FileOutputStream(tempFile);
            ByteStreams.copy(is, out);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            Closeables.closeQuietly(is);
            Closeables.closeQuietly(out);
        }
        return tempFile;
    }

    public static Thread addShutdownHook(final Runnable task) {
        Thread t = new Thread("shutdownHookThread") {
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("Failed to execute shutdownhook", e);
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(t);
        return t;
    }
    public static boolean removeShutdownHook(Thread hook) {
        try {
            return Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException e) {
            // probably shutdown in progress
            log.debug("cannot remove shutdown hook "+hook+": "+e);
            return false;
        }
    }

    /** returns the items with exactly one "/" between items (whether or not the individual items start or end with /) */
    public static String mergePaths(String ...items) {
        StringBuilder result = new StringBuilder();
        for (String item: items) {
            if (result.length()>0) {
                while (result.charAt(result.length()-1)=='/')
                    result.deleteCharAt(result.length()-1);
                result.append('/');
            }
            int i = result.length();
            result.append(item);
            while (result.charAt(i)=='/')
                result.deleteCharAt(i);
        }
        return result.toString();
    }
}
