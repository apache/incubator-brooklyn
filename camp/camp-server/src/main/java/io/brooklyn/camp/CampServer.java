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
package io.brooklyn.camp;

import io.brooklyn.camp.rest.resource.PlatformRestResource;
import io.brooklyn.camp.rest.util.DtoFactory;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Networking;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

public class CampServer {

    private static final Logger log = LoggerFactory.getLogger(CampServer.class);

    public static final String CAMP_PLATFORM_ATTRIBUTE = CampPlatform.class.getCanonicalName();
    public static final String DTO_FACTORY = DtoFactory.class.getCanonicalName();
    
    private final CampPlatform platform;
    private final String uriBase;
    private DtoFactory dtoFactory;
    
    WebAppContext webAppContext;
    Server server;
    
    public CampServer(CampPlatform platform, String uriBase) {
        this.platform = platform;
        this.uriBase = uriBase;
    }

    public CampPlatform getPlatform() {
        return platform;
    }

    public String getUriBase() {
        return uriBase;
    }
    
    public WebAppContext getWebAppContext() {
        return webAppContext;
    }
    
    public synchronized DtoFactory getDtoFactory() {
        if (dtoFactory!=null) return dtoFactory;
        dtoFactory = createDtoFactory();
        return dtoFactory;
    }
    
    protected DtoFactory createDtoFactory() {
        return new DtoFactory(getPlatform(), getUriBase());
    }
    
    public synchronized CampServer start() {
        if (webAppContext!=null)
            throw new IllegalStateException("Already started");
        
        webAppContext = new WebAppContext();
        webAppContext.setContextPath("/");
        webAppContext.setAttribute(CAMP_PLATFORM_ATTRIBUTE, getPlatform());
        webAppContext.setAttribute(DTO_FACTORY, getDtoFactory());
        webAppContext.setWar(
                // TODO if there is a GUI or other war...
                //findJsguiWebapp()!=null ? findJsguiWebapp() : 
                CampServerUtils.createTempWebDirWithIndexHtml("CAMP REST API <p> (no gui available - " +
                		"rest endpoint at <a href=\""+PlatformRestResource.CAMP_URI_PATH+"\">"+PlatformRestResource.CAMP_URI_PATH+"</a>)"));
        CampServerUtils.installAsServletFilter(webAppContext);
        
        server = CampServerUtils.startServer(webAppContext, "CAMP server");
        
        return this;
    }

    public synchronized void stop() {
        try {
            server.stop();
            server = null;
            webAppContext.stop();
            webAppContext = null;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public Integer getPort() {
        if (server==null) return null;
        return server.getConnectors()[0].getLocalPort();
    }

    public static class CampServerUtils {

        public static void installAsServletFilter(ServletContextHandler context) {
            // TODO security
            //        installBrooklynPropertiesSecurityFilter(context);

            // now set up the REST servlet resources
            ResourceConfig config = new DefaultResourceConfig();
            // load all our REST API modules, JSON, and Swagger
            for (Object r: CampRestResources.getAllResources())
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

        public static Server startServer(ContextHandler context, String summary) {
            // FIXME port hardcoded
            int port = Networking.nextAvailablePort(8080);
            Server server = new Server(port);
            server.setHandler(context);
            
            // use a nice name in the thread pool (otherwise this is exactly the same as Server defaults)
            QueuedThreadPool threadPool = new QueuedThreadPool();
            threadPool.setName("camp-jetty-server-"+port+"-"+threadPool.getName());
            server.setThreadPool(threadPool);

            try {
                server.start();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            } 
            log.info("CAMP REST server started ("+summary+") on");
            log.info("  http://localhost:"+server.getConnectors()[0].getLocalPort()+"/");

            return server;
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

}
