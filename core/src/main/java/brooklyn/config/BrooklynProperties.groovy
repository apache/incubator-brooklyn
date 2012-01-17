package brooklyn.config;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.util.ResourceUtils;

/** utils for accessing command-line and system-env properties */
class BrooklynProperties extends LinkedHashMap {

    protected static final Logger LOG = LoggerFactory.getLogger(BrooklynProperties.class)
    
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
            File f = new File(p.getFirst("user.home", "HOME", defaultIfNone:"/")+File.separatorChar+".brooklyn"+File.separatorChar+"brooklyn.properties");
            if (f.exists()) p.addFrom(f);
            return p
        }
    }
    
    protected BrooklynProperties() {
    }
    
    public BrooklynProperties addEnvironmentVars() {
        putAll(System.getenv());
        this
    }
    public BrooklynProperties addSystemProperties() {
        putAll(System.getProperties());
        this
    }
    
    public BrooklynProperties addFrom(InputStream i) {
        Properties p = new Properties();
        p.load(i);
        putAll(p);
        this
    }
    public BrooklynProperties addFrom(File f) {
        if (!f.exists()) {
            LOG.warn("Unable to find file '"+f.getAbsolutePath()+"' when loading properties; ignoring");
            return this   
        } else {
            return addFrom(new FileInputStream(f))
        }
    }
    public BrooklynProperties addFrom(URL u) {
        try {
            addFrom(u.openStream());
        } catch (IOException e) {
            throw new IOException("Error reading properties from ${u}: "+e, e)
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
            addFrom(new ResourceUtils(this).getResourceFromUrl(url));
        } catch (Exception e) {
            throw new IOException("Error reading properties from ${url}: "+e, e);
        }
    }
    
    /** expects a property already set in scope, whose value is acceptable to {@link #addFromUrl(String)};
     * if property not set, does nothing */
    public BrooklynProperties addFromUrlProperty(String urlProperty) {
        String url = get(urlProperty);
        if (url==null) addFromUrl(url);
        this
    }

    /**
    * adds the indicated properties
    */
   public BrooklynProperties addFromMap(Map properties) {
       putAll(properties)
       this
   }

    /** returns the value of the first key which is defined
     * <p>
     * takes the following flags:
     * 'warnIfNone', 'failIfNone' (both taking a boolean (to use default message) or a string (which is the message)); 
     * and 'defaultIfNone' (a default value to return if there is no such property); defaults to no warning and null response */   
    public String getFirst(Map flags=[:], String ...keys) {
        for (String k: keys) {
            if (containsKey(k)) return get(k);
        }
        if (flags.warnIfNone!=null && flags.warnIfNone!=false) {
            if (flags.warnIfNone==true)
                LOG.warn("Unable to find Brooklyn property "+keys);
            else
                LOG.warn(""+flags.warnIfNone);
        }
        if (flags.failIfNone!=null && flags.failIfNone!=false) {
            def f = flags.failIfNone
            if (f in Closure)
                f.call(keys)
            if (f==true)
                throw new NoSuchElementException("Brooklyn unable to find mandatory property "+keys[0]+
                    (keys.length>1 ? " (or "+(keys.length-1)+" other possible names, full list is "+keys+")" : "") );
            else
                throw new NoSuchElementException(""+f);
        }
        if (flags.defaultIfNone!=null) {
            return flags.defaultIfNone;
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "BrooklynProperties["+size()+"]";
    }
}
