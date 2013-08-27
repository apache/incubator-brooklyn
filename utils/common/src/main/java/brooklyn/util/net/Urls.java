package brooklyn.util.net;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;

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

    /** returns true if the string begins with a non-empty string of letters followed by a colon,
     * i.e. "protocol:" returns true, but "/" returns false */
    public static boolean isUrlWithProtocol(String x) {
        if (x==null) return false;
        for (int i=0; i<x.length(); i++) {
            char c = x.charAt(i);
            if (c==':') return i>0;
            if (!Character.isLetter(c)) return false; 
        }
        return false;
    }
    
    /** returns the items with exactly one "/" between items (whether or not the individual items start or end with /),
     * except where character before the / is a : (url syntax) in which case it will permit multiple (will not remove any) */
    public static String mergePaths(String ...items) {
        StringBuilder result = new StringBuilder();
        for (String item: items) {
            boolean trimThisMerge = result.length()>0 && !result.toString().endsWith("://") && !result.toString().endsWith(":///") && !result.toString().endsWith(":");
            if (trimThisMerge) {
                while (result.length()>0 && result.charAt(result.length()-1)=='/')
                    result.deleteCharAt(result.length()-1);
                result.append('/');
            }
            int i = result.length();
            result.append(item);
            if (trimThisMerge) {
                while (result.length()>i && result.charAt(i)=='/')
                    result.deleteCharAt(i);
            }
        }
        return result.toString();
    }

    /** encodes the string suitable for use in a URL, using default character set
     * (non-deprecated version of URLEncoder.encode) */
    @SuppressWarnings("deprecation")
    public static String encode(String text) {
        return URLEncoder.encode(text);
    }

}
