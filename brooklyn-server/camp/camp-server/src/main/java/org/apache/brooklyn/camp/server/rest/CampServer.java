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
package org.apache.brooklyn.camp.server.rest;

import java.io.File;
import java.io.IOException;

import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.server.RestApiSetup;
import org.apache.brooklyn.camp.server.rest.resource.PlatformRestResource;
import org.apache.brooklyn.camp.server.rest.util.DtoFactory;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.net.Networking;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

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
        NetworkConnector networkConnector = (NetworkConnector) server.getConnectors()[0];
        return networkConnector.getLocalPort();
    }

    public static class CampServerUtils {

        public static void installAsServletFilter(ServletContextHandler context) {
            RestApiSetup.install(context);
        }

        public static Server startServer(ContextHandler context, String summary) {
            // FIXME port hardcoded
            int port = Networking.nextAvailablePort(8080);

            // use a nice name in the thread pool (otherwise this is exactly the same as Server defaults)
            QueuedThreadPool threadPool = new QueuedThreadPool();
            threadPool.setName("camp-jetty-server-"+port+"-"+threadPool.getName());

            Server server = new Server(threadPool);

            ServerConnector httpConnector = new ServerConnector(server);
            httpConnector.setPort(port);
            server.addConnector(httpConnector);

            server.setHandler(context);

            try {
                server.start();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            } 
            log.info("CAMP REST server started ("+summary+") on");
            log.info("  http://localhost:"+httpConnector.getLocalPort()+"/");

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
