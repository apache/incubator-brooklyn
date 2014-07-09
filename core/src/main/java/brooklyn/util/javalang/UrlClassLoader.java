package brooklyn.util.javalang;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;

/** like URLClassLoader (and delegates to it) but:
 * * has a nice toString
 * * supports var args constructor
 * * supports file://~/xxx semantics (remaps it to user.home); 
 *      ideally we'd also support mvn, classpath, osgi, etc
 */
public class UrlClassLoader extends URLClassLoader {

    private URL[] urls;

    public UrlClassLoader(URL ...urls) {
        super(tidy(urls));
        this.urls = urls;
    }

    public UrlClassLoader(String ...urls) {
        this(asUrls(urls));
    }
    
    private static URL[] asUrls(String[] urls) {
        URL[] urlo = new URL[urls.length];
        try {
        for (int i=0; i<urls.length; i++)
            urlo[i] = new URL(urls[i]);
        } catch (MalformedURLException e) {
            throw Exceptions.propagate(e);
        }
        return urlo;
    }

    private static URL[] tidy(URL[] urls) {
        for (int i=0; i<urls.length; i++)
            urls[i] = ResourceUtils.tidy(urls[i]);
        return urls;
    }

    @Override
    public String toString() {
        return "UrlClassLoader"+Arrays.asList(urls);
    }
}
