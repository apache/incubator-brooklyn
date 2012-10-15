package brooklyn.web.console.security;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import org.codehaus.groovy.grails.web.context.ServletContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;

/**
 * Locates the ManagementContext by using the ServletContextHolder.getServletContext().
 * In this ServletContext the ManagementContext can be found under key:
 * BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT
 *
 * The properties are stored in the ManagementContext using the
 * {@link brooklyn.management.ManagementContext#getConfig()}.
 */
public class ManagementContextLocator {

    public static ManagementContext getManagementContext() {
        ServletContext servletContext = ServletContextHolder.getServletContext();
        return (ManagementContext) servletContext.getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
    }
}
