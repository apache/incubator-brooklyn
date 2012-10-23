package brooklyn.web.console.security;

import javax.servlet.ServletContext;

import org.codehaus.groovy.grails.web.context.ServletContextHolder;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;

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
