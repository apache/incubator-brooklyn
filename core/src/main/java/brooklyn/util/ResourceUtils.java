package brooklyn.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;
import brooklyn.util.text.DataUriSchemeParser;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
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
            getClassLoaderForObject(contextObject), 
            contextObject, contextMessage);
    }
    
    private static List<Function<Object,ClassLoader>> classLoaderProviders =
        new CopyOnWriteArrayList<Function<Object,ClassLoader>>(); 
    
    /** used to register custom mechanisms for getting classloaders given an object */
    public static void addClassLoaderProvider(Function<Object,ClassLoader> provider) {
        classLoaderProviders.add(provider);
    }
    
    public static ClassLoader getClassLoaderForObject(Object contextObject) {
        for (Function<Object,ClassLoader> provider: classLoaderProviders) {
            ClassLoader result = provider.apply(contextObject);
            if (result!=null) return result;
        }
        return contextObject instanceof Class ? ((Class<?>)contextObject).getClassLoader() : 
            contextObject instanceof ClassLoader ? ((ClassLoader)contextObject) : 
                contextObject.getClass().getClassLoader();
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
            String protocol = getProtocol(url);
            if (protocol!=null) {
                if ("classpath".equals(protocol)) {
                    try {
                        return getResourceViaClasspath(url);
                    } catch (IOException e) {
                        //catch the above because both orig and modified url may be interesting
                        throw new IOException("Error accessing "+orig+": "+e, e);
                    }
                }
                if ("sftp".equals(protocol)) {
                    try {
                        return getResourceViaSftp(url);
                    } catch (IOException e) {
                        throw new IOException("Error accessing "+orig+": "+e, e);
                    }
                }

                if ("file".equals(protocol))
                    url = tidyFileUrl(url);
                
                if ("data".equals(protocol)) {
                    return new DataUriSchemeParser(url).lax().parse().getDataAsInputStream();
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
                if (url.startsWith("~")) {
                    // but first, if it starts with tilde, treat specially
                    url = Strings.removeFromStart(tidyFileUrl("file:"+url), "file://", "file:");
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
    
    public static URL tidy(URL url) {
        try {
            if ("file".equals(url.getProtocol()))
                return new URL(tidyFileUrl(url.toString()));
            return url;
        } catch (MalformedURLException e) {
            /* see comments on MalformedURLException in org.reflections.utils.ClasspathHelper */
            return url;
        }
    }
    
    public static String tidyFileUrl(String url) {
        if (url.matches("file://[A-Za-z]:[/\\\\].*")) {
            // file://c:/path/to/x is sometimes mistakenly supplied
            // where file:///c:/path/to/x is the correct syntax.
            // treat the former as the latter since the former doesn't have any other interpretation
            if (log.isDebugEnabled())
                log.debug("quietly changing "+url+" to file:/// prefix");
            url = "file:///"+url.substring(7);
        }

        String urlRelativeToHome = Strings.removeFromStart(url, "file://~/", "file:~/");
        if (!url.equals(urlRelativeToHome)) {
            // allow ~ syntax for home dir
            url = "file://"+System.getProperty("user.home")+"/"+urlRelativeToHome;
            if (log.isDebugEnabled())
                log.debug("quietly changing to "+url+" from file://~/ URL");
        }
        return url;
    }
    
    public static String mergeFilePaths(String... items) {
        char separatorChar = File.separatorChar;
        StringBuilder result = new StringBuilder();
        for (String item: items) {
            if (item.isEmpty()) continue;
            if (result.length() > 0 && result.charAt(result.length()-1) != separatorChar) result.append(separatorChar);
            result.append(item);
        }
        return result.toString();
    }
    
    public static String tidyFilePath(String path) {
        String pathRelativeToHome = Strings.removeFromStart(path, "~/");
        if (path.equals(pathRelativeToHome)) {
            return path;
        }
        // allow ~ syntax for home dir
        String result = mergeFilePaths(System.getProperty("user.home"), pathRelativeToHome);
        if (log.isDebugEnabled()) log.debug("quietly changing to "+path+" to "+result);
        return result;
    }
    
    /** returns the protocol (e.g. http) if one appears to be specified, or else null;
     * 'protocol' here should consist of 2 or more _letters_ only followed by a colon
     * (2 required to prevent  c``:\xxx being treated as a url)
     */
    public static String getProtocol(String url) {
        if (url==null) return null;
        int i=0;
        StringBuilder result = new StringBuilder();
        while (true) {
            if (url.length()<=i) return null;
            char c = url.charAt(i);
            if (Character.isLetter(c)) result.append(c);
            else if (c==':') {
                if (i>=2) return result.toString();
                return null;
            } else return null;
            i++;
        }
    }
    
    private InputStream getResourceViaClasspath(String url) throws IOException {
        assert url.startsWith("classpath:");
        String subUrl = url.substring("classpath:".length());
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
        
        // TODO messy way to get an SCP session 
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

    /** allows failing-fast if URL cannot be read */
    public String checkUrlExists(String url) {
        if (url==null) throw new NullPointerException("URL must not be null");
        InputStream s;
        try {
            s = getResourceFromUrl(url);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalArgumentException("Unable to access URL "+url, e);
        }
        Closeables.closeQuietly(s); 
        return url;
    }

    /** tests whether the url exists, returning true or false */
    public boolean doesUrlExist(String url) {
        InputStream s = null;
        try {
            s = getResourceFromUrl(url);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            Closeables.closeQuietly(s);
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

    public static File mkdirs(File dir) {
        if (dir.isDirectory()) return dir;
        boolean success = dir.mkdirs();
        if (!success) throw Exceptions.propagate(new IOException("Failed to create directory " + dir + 
                (dir.isFile() ? "(is file)" : "")));
        return dir;
    }

    public static File writeToTempFile(InputStream is, String prefix, String suffix) {
        return writeToTempFile(is, new File(System.getProperty("java.io.tmpdir")), prefix, suffix);
    }
    
    public static File writeToTempFile(InputStream is, File tempDir, String prefix, String suffix) {
        if (is == null) throw new NullPointerException("Input stream required to create temp file for "+prefix+"*"+suffix);
        mkdirs(tempDir);
        File tempFile;
        try {
            tempFile = File.createTempFile(prefix, suffix, tempDir);
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

    public static File writeToTempFile(Properties props, String prefix, String suffix) {
        return writeToTempFile(props, new File(System.getProperty("java.io.tmpdir")), prefix, suffix);
    }
    
    public static File writeToTempFile(Properties props, File tempDir, String prefix, String suffix) {
        if (props == null) throw new NullPointerException("Properties required to create temp file for "+prefix+"*"+suffix);
        File tempFile;
        try {
            tempFile = File.createTempFile(prefix, suffix, tempDir);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        tempFile.deleteOnExit();

        OutputStream out = null;
        try {
            out = new FileOutputStream(tempFile);
            props.store(out, "Auto-generated by Brooklyn");
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
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

    /** returns the items with exactly one "/" between items (whether or not the individual items start or end with /),
     * except where character before the / is a : (url syntax) in which case it will permit multiple (will not remove any) */
    public static String mergePaths(String ...items) {
        return Urls.mergePaths(items);
    }
}
