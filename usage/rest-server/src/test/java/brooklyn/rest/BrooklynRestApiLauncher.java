/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.rest;

import brooklyn.rest.util.HaMasterCheckFilter;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherAbstract;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.InetSocketAddress;
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

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.security.BrooklynPropertiesSecurityFilter;
import brooklyn.rest.security.provider.AnyoneSecurityProvider;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Networking;
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
        BrooklynCampPlatformLauncherAbstract platform = new BrooklynCampPlatformLauncherNoServer()
            .useManagementContext(new LocalManagementContext())
            .launch();
        
        return startRestResourcesViaFilter(platform.getBrooklynMgmt());
    }
    public static Server startRestResourcesViaFilter(ManagementContext managementContext) throws Exception {
        WebAppContext context = new WebAppContext();
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext);
        context.setContextPath("/");
        // here we run with the JS GUI, for convenience, if we can find it, else set up an empty dir
        // TODO pretty sure there is an option to monitor this dir and load changes to static content
        context.setWar(findJsguiWebapp()!=null ? findJsguiWebapp() : createTempWebDirWithIndexHtml("Brooklyn REST API <p> (gui not available)"));
        
        installAsServletFilter(context);
        
        return startServer(managementContext, context, "programmatic Jersey ServletContainer filter on webapp at "+context.getWar());
    }

    public static Server startRestResourcesViaServlet() throws Exception {
        BrooklynCampPlatformLauncherAbstract platform = new BrooklynCampPlatformLauncherNoServer()
            .useManagementContext(new LocalManagementContext())
            .launch();
        
        return startRestResourcesViaServlet(platform.getBrooklynMgmt());
    }
    public static Server startRestResourcesViaServlet(ManagementContext managementContext) throws Exception {
        ResourceConfig config = new DefaultResourceConfig();
        for (Object r: BrooklynRestApi.getAllResources())
            config.getSingletons().add(r);
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext);
        ServletHolder servletHolder = new ServletHolder(new ServletContainer(config));
        context.addServlet(servletHolder, "/*");
        context.setContextPath("/");

        installBrooklynFilters(context);

        return startServer(managementContext, context, "programmatic Jersey ServletContainer servlet");
    }
    
    private static void installBrooklynFilters(ServletContextHandler context) {
        context.addFilter(BrooklynPropertiesSecurityFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        context.addFilter(HaMasterCheckFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
    }
    
    public static void installAsServletFilter(ServletContextHandler context) {
        installBrooklynFilters(context);

        // now set up the REST servlet resources
        ResourceConfig config = new DefaultResourceConfig();
        // load all our REST API modules, JSON, and Swagger
        for (Object r: BrooklynRestApi.getAllResources())
            config.getSingletons().add(r);
        // configure to match empty path, or any thing which looks like a file path with /assets/ and extension html, css, js, or png
        // and treat that as static content
        config.getProperties().put(ServletContainer.PROPERTY_WEB_PAGE_CONTENT_REGEX, "(/?|[^?]*/assets/[^?]+\\.[A-Za-z0-9_]+)");
        // and anything which is not matched as a servlet also falls through (but more expensive than a regex check?)
        config.getFeatures().put(ServletContainer.FEATURE_FILTER_FORWARD_ON_404, true);
        // finally create this as a _filter_ which falls through to a web app or something (optionally)
        FilterHolder filterHolder = new FilterHolder(new ServletContainer(config));
        context.addFilter(filterHolder, "/*", EnumSet.allOf(DispatcherType.class));
    }

    public static Server startRestResourcesViaWebXml() throws Exception {
        return startRestResourcesViaWebXml(new LocalManagementContext());
    }
    public static Server startRestResourcesViaWebXml(ManagementContext managementContext) throws Exception {
        // TODO add security to web.xml
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
    
    /** starts server on all nics (even if security not enabled).
     * @deprecated since 0.6.0; use {@link #startServer(ManagementContext, ContextHandler, String)} or
     * {@link #startServer(ContextHandler, String, InetSocketAddress)} */
    @Deprecated
    public static Server startServer(ContextHandler context, String summary) {
        return startServer(context, summary, 
                new InetSocketAddress(Networking.ANY_NIC, Networking.nextAvailablePort(FAVOURITE_PORT)));
    }
    /** starts a server, on all NICs if security is configured,
     * otherwise (no security) only on loopback interface */
    public static Server startServer(ManagementContext mgmt, ContextHandler context, String summary) {
        // TODO this repeats code in BrooklynLauncher / WebServer. should merge the two paths.
        boolean secure = mgmt!=null && !BrooklynWebConfig.hasNoSecurityOptions(mgmt.getConfig()) ? true : false;
        if (secure) {
            log.debug("Detected security configured, launching server on all network interfaces");
        } else {
            log.debug("Detected no security configured, launching server on loopback (localhost) network interface only");
            if (mgmt!=null) {
                log.debug("Detected no security configured, running on loopback; disabling authentication");
                ((BrooklynProperties)mgmt.getConfig()).put(BrooklynWebConfig.SECURITY_PROVIDER_CLASSNAME, AnyoneSecurityProvider.class.getName());
            }
        }
        if (mgmt != null)
            mgmt.getHighAvailabilityManager().disabled();
        InetSocketAddress bindLocation = new InetSocketAddress(
                secure ? Networking.ANY_NIC : Networking.LOOPBACK, 
                        Networking.nextAvailablePort(FAVOURITE_PORT));
        return startServer(context, summary, bindLocation);
    }
    public static Server startServer(ContextHandler context, String summary, InetSocketAddress bindLocation) {
        Server server = new Server(bindLocation);
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
