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
package org.apache.brooklyn.rest.jsgui;

import java.net.InetSocketAddress;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.rest.BrooklynRestApiLauncher;
import org.apache.brooklyn.util.net.Networking;

/** launches Javascript GUI programmatically. and used for tests.
 * see {@link BrooklynRestApiLauncher} for more information.
 *
 * WINDOWS tips:
 * On Windows Jetty will lock all static files preventing any changes on them.
 * To work around the problem and tell Jetty not to lock files:
 * <ul>
 *   <li>find jetty-webapp-&lt;ver&gt;.jar from your classpath
 *   <li>extract the file webdefault.xml from folder org/eclipse/jetty/webapp (On Eclipse
 *      just expanding the jar from the dependencies, right click/copy on the file.)
 *   <li>in this project create a java package org.eclipse.jetty.webapp and put the webdefault.html file in it
 *   <li>edit the file and change the property useFileMappedBuffer to false
 * </ul> 
 **/
public class BrooklynJavascriptGuiLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrooklynJavascriptGuiLauncher.class);
    
    public static void main(String[] args) throws Exception {
        // NOTE: When running Brooklyn from an IDE (i.e. by launching BrooklynJavascriptGuiLauncher.main())
        // you will need to ensure that the working directory is set to the jsgui folder. For IntelliJ,
        // set the 'Working directory' of the Run/Debug Configuration to $MODULE_DIR/../jsgui.
        // For Eclipse, use the default option of ${workspace_loc:brooklyn-jsgui}.
        // If the working directory is not set correctly, Brooklyn will be unable to find the jsgui .war
        // file and the 'gui not available' message will be shown.
        startJavascriptAndRest();
        
        log.info("Press Ctrl-C to quit.");
    }
    
    final static int FAVOURITE_PORT = 8080;
    
    /** due to the ../jsgui trick in {@link BrooklynRestApiLauncher} we can just call that method */ 
    public static Server startJavascriptAndRest() throws Exception {
        return BrooklynRestApiLauncher.startRestResourcesViaFilter();
    }

    /** not much fun without a REST client. but TODO we should make it so the REST endpoint can be configured. */
    public static Server startJavascriptWithoutRest() throws Exception {
        WebAppContext context = new WebAppContext("./src/main/webapp", "/");
        
        Server server = new Server(new InetSocketAddress(Networking.LOOPBACK, Networking.nextAvailablePort(FAVOURITE_PORT)));
        server.setHandler(context);
        server.start();
        log.info("JS GUI server started (no REST) at  http://localhost:"+server.getConnectors()[0].getLocalPort()+"/");
        
        return server;
    }

}
