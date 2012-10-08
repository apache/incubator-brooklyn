package brooklyn.web.console.security;

import java.util.Enumeration;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import org.codehaus.groovy.grails.web.context.ServletContextHolder;

import brooklyn.config.BrooklynProperties;
import brooklyn.util.internal.BrooklynSystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;

/** convenience class for accessing system properties,
 * currently reading from brooklyn.properties
 * but ideally reading from the management context
 * <p>
 * see {@link BrooklynSystemProperties} for list of keys
 * (those starting brooklyn.security are relevant)
 */
public class ManagementContextLocator {

    public static final Logger LOG = LoggerFactory.getLogger(ManagementContextLocator.class);

    static BrooklynProperties _props;

    static synchronized BrooklynProperties getProps() {
        LOG.info("=========================== security provider ==============================================");
        LOG.info(""+_props);
        LOG.info("=========================== security provider ==============================================");

        if (_props!=null) return _props;
        _props = BrooklynProperties.Factory.newWithSystemAndEnvironment();

        Enumeration ae = ServletContextHolder.getServletContext().getAttributeNames();
        LOG.info("Adding all ServletContextHolder.servletContext attributes to brooklyn props");
        while (ae.hasMoreElements()) {
            String k = (String)ae.nextElement();
            Object v = ServletContextHolder.getServletContext().getAttribute(k);
            LOG.info(k+"="+v);
            _props.put(k, v);
        }
        return _props;
    }

    public static ManagementContext getManagementContext(){
        ServletContext servletContext = ServletContextHolder.getServletContext();
        return (ManagementContext) servletContext.getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
    }
}
