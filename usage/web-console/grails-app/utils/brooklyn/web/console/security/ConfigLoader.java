package brooklyn.web.console.security;

import java.util.Enumeration;

import org.codehaus.groovy.grails.web.context.ServletContextHolder;

import brooklyn.config.BrooklynProperties;
import brooklyn.util.internal.BrooklynSystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** convenience class for accessing system properties,
 * currently reading from brooklyn.properties
 * but ideally reading from the management context
 * <p>
 * see {@link BrooklynSystemProperties} for list of keys
 * (those starting brooklyn.security are relevant)
 */
public class ConfigLoader {

    public static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    static BrooklynProperties _props;

    static synchronized BrooklynProperties getProps() {
        LOG.info("=========================== security provider ==============================================");
        LOG.info(_props.toString());
        LOG.info("=========================== security provider ==============================================");

        if (_props!=null) return _props;
        _props = BrooklynProperties.Factory.newWithSystemAndEnvironment();



        Enumeration ae = ServletContextHolder.getServletContext().getAttributeNames();
        while (ae.hasMoreElements()) {
            String k = (String)ae.nextElement();
            Object v = ServletContextHolder.getServletContext().getAttribute(k);
            _props.put(k, v);
        }
        return _props;
    }
    
    public static Object getConfig(String key) { return getProps().getFirst(key); }

}
