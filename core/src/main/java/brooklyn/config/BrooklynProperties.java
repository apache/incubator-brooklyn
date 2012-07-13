package brooklyn.config;

import groovy.lang.Closure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.MutableMap;
import brooklyn.util.ResourceUtils;

import com.google.common.base.Throwables;

/** utils for accessing command-line and system-env properties */
public class BrooklynProperties extends LinkedHashMap {

    protected static final Logger LOG = LoggerFactory.getLogger(BrooklynProperties.class);
    
    public static class Factory {
        public static BrooklynProperties newEmpty() {
            return new BrooklynProperties();
        }
        // FIXME remove
        public static BrooklynProperties newWithSystemAndEnvironment() {
            return newDefault();
        }
        public static BrooklynProperties newDefault() {
            BrooklynProperties p = new BrooklynProperties().addEnvironmentVars().addSystemProperties();
            File f = new File(p.getFirst(MutableMap.of("defaultIfNone", "/"), "user.home", "HOME")+File.separatorChar+".brooklyn"+File.separatorChar+"brooklyn.properties");
            if (f.exists()) p.addFrom(f);
            return p;
        }
    }
    
    protected BrooklynProperties() {
    }
    
    public BrooklynProperties addEnvironmentVars() {
        putAll(System.getenv());
        return this;
    }
    public BrooklynProperties addSystemProperties() {
        putAll(System.getProperties());
        return this;
    }
    
    public BrooklynProperties addFrom(InputStream i) {
        Properties p = new Properties();
        try {
            p.load(i);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        putAll(p);
        return this;
    }
    public BrooklynProperties addFrom(File f) {
        if (!f.exists()) {
            LOG.warn("Unable to find file '"+f.getAbsolutePath()+"' when loading properties; ignoring");
            return this;
        } else {
            try {
                return addFrom(new FileInputStream(f));
            } catch (FileNotFoundException e) {
                throw Throwables.propagate(e);
            }
        }
    }
    public BrooklynProperties addFrom(URL u) {
        try {
            return addFrom(u.openStream());
        } catch (IOException e) {
            throw new RuntimeException("Error reading properties from "+u+": "+e, e);
        }
    }
    /**
     * @see ResourceUtils#getResourceFromUrl(String)
     * 
     * of the form form file:///home/... or http:// or classpath://xx ;
     * for convenience if not starting with xxx: it is treated as a classpath reference or a file;
     * throws if not found
     */
    public BrooklynProperties addFromUrl(String url) {
        try {
            return addFrom(new ResourceUtils(this).getResourceFromUrl(url));
        } catch (Exception e) {
            throw new RuntimeException("Error reading properties from ${url}: "+e, e);
        }
    }
    
    /** expects a property already set in scope, whose value is acceptable to {@link #addFromUrl(String)};
     * if property not set, does nothing */
    public BrooklynProperties addFromUrlProperty(String urlProperty) {
        String url = (String) get(urlProperty);
        if (url==null) addFromUrl(url);
        return this;
    }

    /**
    * adds the indicated properties
    */
   public BrooklynProperties addFromMap(Map properties) {
       putAll(properties);
       return this;
   }

   /** inserts the value under the given key, if it was not present */
   public boolean putIfAbsent(String key, Object value) {
       // TODO Not thread-safe
       if (containsKey(key)) return false;
       put(key, value);
       return true;
   }

   /** @deprecated attempts to call get with this syntax are probably mistakes; get(key, defaultValue) is fine but
    * Map is unlikely the key, much more likely they meant getFirst(flags, key).   
    */
   @Deprecated
   public String get(Map flags, String key) {
       LOG.warn("Discouraged use of 'BrooklynProperties.get(Map,String)' (ambiguous); use getFirst(Map,String) or get(String) -- assuming the former");
       LOG.debug("Trace for discouraged use of 'BrooklynProperties.get(Map,String)'",
           new Throwable("Arguments: "+flags+" "+key));
       return getFirst(flags, key);
   }
       
    /** returns the value of the first key which is defined
     * <p>
     * takes the following flags:
     * 'warnIfNone', 'failIfNone' (both taking a boolean (to use default message) or a string (which is the message)); 
     * and 'defaultIfNone' (a default value to return if there is no such property); defaults to no warning and null response */   
    public String getFirst(String ...keys) {
       return getFirst(MutableMap.of(), keys);
    }
    public String getFirst(Map flags, String ...keys) {
        for (String k: keys) {
            if (containsKey(k)) return (String) get(k);
        }
        if (flags.get("warnIfNone")!=null && !Boolean.FALSE.equals(flags.get("warnIfNone"))) {
            if (Boolean.TRUE.equals(flags.get("warnIfNone")))
                LOG.warn("Unable to find Brooklyn property "+keys);
            else
                LOG.warn(""+flags.get("warnIfNone"));
        }
        if (flags.get("failIfNone")!=null && !Boolean.FALSE.equals(flags.get("failIfNone"))) {
            Object f = flags.get("failIfNone");
            if (f instanceof Closure)
                ((Closure)f).call(keys);
            if (Boolean.TRUE.equals(f))
                throw new NoSuchElementException("Brooklyn unable to find mandatory property "+keys[0]+
                    (keys.length>1 ? " (or "+(keys.length-1)+" other possible names, full list is "+keys+")" : "") );
            else
                throw new NoSuchElementException(""+f);
        }
        if (flags.get("defaultIfNone")!=null) {
            return (String) flags.get("defaultIfNone");
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "BrooklynProperties["+size()+"]";
    }
}
