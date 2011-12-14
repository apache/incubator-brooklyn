package brooklyn.config;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BrooklynProperties extends LinkedHashMap {

    protected static final Logger LOG = LoggerFactory.getLogger(BrooklynProperties.class)
    
    public static class Factory {
        public static BrooklynProperties newEmpty() {
            return new BrooklynProperties();
        }
        public static BrooklynProperties newWithSystemAndEnvironment() {
            BrooklynProperties p = new BrooklynProperties().addEnvironmentVars().addSystemProperties();
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
     * aof form file:///home/... or http:// or classpath://xx ;
     * for convenience if not starting with xxx: it is treated as a classpath reference or a file;
     * throws if not found
     */
    public BrooklynProperties addFromUrl(String url) {
        if (url==null) throw new NullPointerException("Cannot read properties from null");
        if (url=="") throw new NullPointerException("Cannot read properties from empty string");
        String orig = url;
        if (url.startsWith("classpath:")) {
            url = url.substring(10);
            while (url.startsWith("/")) url = url.substring(1);
            URL u = getClass().getClassLoader().getResource(url);
            try {
                if (u) return addFrom(u);
                else throw new IOException("${url} not found on classpath");
            } catch (IOException e) {
                //catch the above because both u and modified url will be interesting
                throw new IOException("Error reading properties from ${orig}: "+e, e);
            }
        }
        if (url.matches("[A-Za-z]+:.*")) {
            //looks like a URL
            return addFrom(new URL(url));
        }
        
        try {
            //try as classpath reference, then as file
            URL u = getClass().getClassLoader().getResource(url);
            if (u) return addFrom(u);
            File f = new File(url);
            if (f.exists()) return addFrom(new File(url));
        } catch (IOException e) {
            //catch the above because both u and modified url will be interesting
            throw new IOException("Error reading properties from ${orig}: "+e, e);
        }
        throw new IOException("Error reading properties from ${orig}: not found on classpath or filesystem");
    }
    
    /** expects a property already set in scope, whose value is acceptable to {@link #addFromUrl(String)};
     * if property not set, does nothing */
    public BrooklynProperties addFromUrlProperty(String urlProperty) {
        String url = get(urlProperty);
        if (url==null) addFromUrl(url);
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
                throw new NoSuchElementException("Unable to find Brooklyn property "+keys);
            else
                throw new NoSuchElementException(""+f);
        }
        if (flags.defaultIfNone!=null) {
            return flags.defaultIfNone;
        }
        return null;
    }
}
