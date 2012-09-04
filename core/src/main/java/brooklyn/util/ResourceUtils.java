package brooklyn.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

public class ResourceUtils {
    
    private static final Logger log = LoggerFactory.getLogger(ResourceUtils.class);

    ClassLoader loader = null;
    String context = null;
    
    /** context string used for errors */
    public ResourceUtils(ClassLoader loader, String context) {
        this.loader = loader;
        this.context = context;
    }
    /** uses the classloader of the given object, and the phrase object's toString (preceded by the word 'for') as the context string used in errors */
    public ResourceUtils(Object context) {
        this(context==null ? null : context instanceof Class ? ((Class)context).getClassLoader() : context.getClass().getClassLoader(), context==null ? null : ""+context);
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
                url = url.substring(10);
                while (url.startsWith("/")) url = url.substring(1);
                URL u = getLoader().getResource(url);
                try {
                    if (u!=null) return u.openStream();
                    else throw new IOException(url+" not found on classpath");
                } catch (IOException e) {
                    //catch the above because both orig and modified url may be interesting
                    throw new IOException("Error accessing "+orig+": "+e, e);
                }
            }
            if (url.matches("[A-Za-z]+:.*")) {
                //looks like a URL
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

    /** takes {@link #getResourceFromUrl(String)} and reads fully, into a string */
    public String getResourceAsString(String url) {
        try {
            return readFullyString(getResourceFromUrl(url));
        } catch (Exception e) {
            log.debug("ResourceUtils got error reading "+url+(context==null?"":" "+context)+" (rethrowing): "+e);
            throw Throwables.propagate(e);
        }
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
        File tmpWarFile;

        try {
            tmpWarFile = File.createTempFile(prefix, suffix);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        tmpWarFile.deleteOnExit();

        OutputStream out = null;
        try {
            if (is == null) throw new NullPointerException();
            out = new FileOutputStream(tmpWarFile);
            ByteStreams.copy(is, out);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            Closeables.closeQuietly(is);
            Closeables.closeQuietly(out);
        }
        return tmpWarFile;
    }

}
