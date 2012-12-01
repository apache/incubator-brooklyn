package brooklyn.rest;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.security.BrooklynPropertiesSecurityFilter;
import brooklyn.util.NetworkUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.WildcardGlobs;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

/** Convenience and demo for launching programmatically. Also used for automated tests.
 * <p>
 * BrooklynLauncher has a more full-featured CLI way to start, 
 * but if you want more control you can:
 * <li> take the WAR this project builds (REST API) -- NB probably want the unshaded one (containing all deps)
 * <li> take the WAR from the jsgui project _and_ this WAR and combine them 
 *      (this one should run as a filter on the others, _not_ as a ResourceCollection where they fight over who's got root)
 * <li> programmatically install things, following the examples herein; 
 *      in particular {@link #installAsServletFilter(ServletContextHandler)} is quite handy! 
 * <p>
 * You can also just run this class. In most installs it just works, assuming your IDE or maven-fu gives you the classpath.
 * Add more apps and entities on the classpath and they'll show up in the catalog.
 **/
public class BrooklynRestApiLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrooklynRestApiLauncher.class);
    
    public static void main(String[] args) throws Exception {
        startRestResourcesViaFilter();
        // filter (above) is most flexible, but can also do either of the methods below
//        startRestResourcesViaServlet();
//        startRestResourcesViaWebXml();
        
        log.info("Press Ctrl-C to quit.");
    }
    
    final static int FAVOURITE_PORT = 8081;
    
    public static Server startRestResourcesViaFilter() throws Exception {
        ManagementContext managementContext = new LocalManagementContext();
        
        WebAppContext context = new WebAppContext();
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext);
        context.setContextPath("/");
        // here we run with the JS GUI, for convenience, if we can find it, else set up an empty dir
        // TODO pretty sure there is an option to monitor this dir and load changes to static content
        context.setWar(findJsguiWebapp()!=null ? findJsguiWebapp() : createTempWebDirWithIndexHtml("Brooklyn REST API <p> (gui not available)"));
        
        installAsServletFilter(context);
        
        return startServer(context, "programmatic Jersey ServletContainer filter on webapp at "+context.getWar());
    }

    public static Server startRestResourcesViaServlet() throws Exception {
        ManagementContext managementContext = new LocalManagementContext();
        
        ResourceConfig config = new DefaultResourceConfig();
        for (Object r: BrooklynRestApi.getAllResources())
            config.getSingletons().add(r);
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext);
        ServletHolder servletHolder = new ServletHolder(new ServletContainer(config));
        context.addServlet(servletHolder, "/*");
        context.setContextPath("/");
        
        installBrooklynPropertiesSecurityFilter(context);
        
        return startServer(context, "programmatic Jersey ServletContainer servlet");
    }
    
    public static void installBrooklynPropertiesSecurityFilter(ServletContextHandler context) {
        context.addFilter(BrooklynPropertiesSecurityFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
    }
    
    public static void installAsServletFilter(ServletContextHandler context) {
        installBrooklynPropertiesSecurityFilter(context);
        
        // now set up the REST servlet resources
        ResourceConfig config = new DefaultResourceConfig();
        // load all our REST API modules, JSON, and Swagger
        for (Object r: BrooklynRestApi.getAllResources())
            config.getSingletons().add(r);
        // configure to match empty path, or any thing which looks like a file path with /assets/ and extension html, css, js, or png
        // and treat that as static content
        config.getProperties().put(ServletContainer.PROPERTY_WEB_PAGE_CONTENT_REGEX, "(/?|[^?]*/asserts/[^?]+\\.[A-Za-z0-9_]+)");
        // and anything which is not matched as a servlet also falls through (but more expensive than a regex check?)
        config.getFeatures().put(ServletContainer.FEATURE_FILTER_FORWARD_ON_404, true);
        // finally create this as a _filter_ which falls through to a web app or something (optionally)
        FilterHolder filterHolder = new FilterHolder(new ServletContainer(config));
        context.addFilter(filterHolder, "/*", EnumSet.allOf(DispatcherType.class));
    }

    public static Server startRestResourcesViaWebXml() throws Exception {
        // TODO add security to web.xml
        ManagementContext managementContext = new LocalManagementContext();

        WebAppContext context;
        
        if (findMatchingFile("src/main/webapp")!=null) {
            // running in source mode; need to use special classpath
            context = new WebAppContext("src/main/webapp", "/");
            context.setExtraClasspath("./target/classes");
        } else if (findRestApiWar()!=null) {
            context = new WebAppContext(findRestApiWar(), "/");
        } else {
            throw new IllegalStateException("Cannot find WAR for REST API. Expected in target/*.war, Maven repo, or in source directories.");
        }
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext);
        
        return startServer(context, "from WAR at "+context.getWar());
    }
    
    public static Server startServer(ContextHandler context, String summary) {
        Server server = new Server(NetworkUtils.nextAvailablePort(FAVOURITE_PORT));
        server.setHandler(context);
        try {
            server.start();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        } 
        log.info("Brooklyn REST server started ("+summary+") on");
        log.info("  http://localhost:"+server.getConnectors()[0].getLocalPort()+"/");
        
        return server;
    }

    /** look for the JS GUI webapp in common places, returning path to it if found, or null */
    public static String findJsguiWebapp() {
        String result = null;
        result = findMatchingFile("../jsgui/src/main/webapp");  if (result!=null) return result;
        result = findMatchingFile("../jsgui/target/*.war");  if (result!=null) return result;
        // could also look in maven repo ?
        return null;
    }

    /** look for the REST WAR file in common places, returning path to it if found, or null */
    public static String findRestApiWar() {
        String result = null;
        // don't look at src/main/webapp here -- because classes won't be there!
        result = findMatchingFile("../rest/target/*.war");  if (result!=null) return result;
        // could also look in maven repo ?
        return null;
    }

    /** returns the supplied filename if it exists (absolute or relative to the current directory);
     * supports globs in the filename portion only, in which case it returns the _newest_ matching file.
     * <p>
     * otherwise returns null */
    public static String findMatchingFile(String filename) {
        final File f = new File(filename);
        if (f.exists()) return filename;
        File dir = f.getParentFile();
        File result = null;
        if (dir.exists()) {
            File[] matchingFiles = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return WildcardGlobs.isGlobMatched(f.getName(), name);
                }
            });
            for (File mf: matchingFiles) {
                if (result==null || mf.lastModified() > result.lastModified()) result = mf;
            }
        }
        if (result==null) return null;
        return result.getAbsolutePath();
    }

    /** create a directory with a simple index.html so we have some content being served up */
    public static String createTempWebDirWithIndexHtml(String indexHtmlContent) {
        File dir = Files.createTempDir();
        dir.deleteOnExit();
        try {
            Files.write(indexHtmlContent, new File(dir, "index.html"), Charsets.UTF_8);
        } catch (IOException e) {
            Exceptions.propagate(e);
        }
        return dir.getAbsolutePath();
    }
    
}
