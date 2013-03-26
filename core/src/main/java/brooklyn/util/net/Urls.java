package brooklyn.util.net;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Throwables;

public class Urls {

    public static final Function<String,URI> URI_FROM_STRING = new Function<String,URI>() {
        public URI apply(String input) { return toUri(input); } 
    };

    public static final Function<String,URL> URL_FROM_STRING = new Function<String,URL>() {
        public URL apply(String input) { return toUrl(input); } 
    };

    /** creates a URL, preserving null and propagating exceptions *unchecked* */
    public static final URL toUrl(@Nullable String url) {
        if (url==null) return null;
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            // FOAD
            throw Throwables.propagate(e);
        }
    }
    
    /** creates a URL, preserving null and propagating exceptions *unchecked* */
    public static final URL toUrl(@Nullable URI uri) {
        if (uri==null) return null;
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            // FOAD
            throw Throwables.propagate(e);
        }
    }

    /** creates a URI, preserving null and propagating exceptions *unchecked* */
    public static final URI toUri(@Nullable String uri) {
        if (uri==null) return null;
        return URI.create(uri);
    }
    
    /** creates a URI, preserving null and propagating exceptions *unchecked* */
    public static final URI toUri(@Nullable URL url) {
        if (url==null) return null;
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            // FOAD
            throw Throwables.propagate(e);
        }
    }

}
