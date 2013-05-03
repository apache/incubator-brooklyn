package brooklyn.util.net;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Throwables;

public class Urls {

    public static Function<String,URI> stringToUriFunction() {
        return StringToUri.INSTANCE;
    }
    
    public static Function<String,URL> stringToUrlFunction() {
        return StringToUrl.INSTANCE;
    }
    
    private static enum StringToUri implements Function<String,URI> {
        INSTANCE;
        @Override public URI apply(@Nullable String input) {
            return toUri(input);
        }
        @Override
        public String toString() {
            return "StringToUri";
        }
    }

    private static enum StringToUrl implements Function<String,URL> {
        INSTANCE;
        @Override public URL apply(@Nullable String input) {
            return toUrl(input);
        }
        @Override
        public String toString() {
            return "StringToUrl";
        }
    }

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
