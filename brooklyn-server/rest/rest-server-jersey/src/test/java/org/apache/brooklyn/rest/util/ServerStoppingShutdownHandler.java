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
package org.apache.brooklyn.rest.util;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* not the cleanest way to enforce a clean shutdown, but a simple and effective way;
 * usage is restricted to BrooklynRestApiLauncher and subclasses, to stop it inline.
 * the main app stops the server in a more principled way using callbacks. */
public class ServerStoppingShutdownHandler implements ShutdownHandler {

    private static final Logger log = LoggerFactory.getLogger(ServerStoppingShutdownHandler.class);
    
    private final ManagementContext mgmt;
    private Server server;
    
    public ServerStoppingShutdownHandler(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }

    @Override
    public void onShutdownRequest() {
        log.info("Shutting down server (when running in rest-api dev mode, using background thread)");

        // essentially same as BrooklynLauncher.terminate() but cut down ...
        // NB: this is only used in dev mode use of BrooklynJavascriptGuiLauncher
        new Thread(new Runnable() {
            public void run() {
                Time.sleep(Duration.millis(250));
                log.debug("Shutting down server in background thread, closing "+server+" and "+mgmt);
                if (server!=null) {
                    try {
                        server.stop();
                        server.join();
                    } catch (Exception e) {
                        log.debug("Stopping server gave an error (not usually a concern): "+e);
                        /* NPE may be thrown e.g. if threadpool not started */
                    }
                }

                if (mgmt instanceof ManagementContextInternal) {
                    ((ManagementContextInternal)mgmt).terminate();
                }
            }
        }).start();
    }

    /** Expect this to be injected; typically it is not known when this is created, but we need it to trigger shutdown. */
    public void setServer(Server server) {
        this.server = server;
    }

}
