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
package org.apache.brooklyn.test.framework;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.drivers.EntityDriver;
import org.apache.brooklyn.api.location.Location;

import java.util.Collection;

/**
 * Driver to invoke a command on a node.
 */
public interface SimpleCommandDriver extends EntityDriver {

    /**
     * Result of the command invocation.
     */
    interface Result {
        int getExitCode();
        String getStdout();
        String getStderr();
    }

    /**
     * The entity whose components we are controlling.
     */
    EntityLocal getEntity();

    /**
     * Execute the simple command during the start operation.
     */
    void start();

    /**
     * Execute the simple command during the restart.
     */
    void restart();

    /**
     * Does nothing.
     */
    void stop();

    /**
     * Execute the given command on the supplied host.
     */
    Result execute(Collection<? extends Location> hostLocations, String command);

    /**
     * Download the script at the given URL to the given directory on the host and execute it.
     */
    Result executeDownloadedScript(Collection<? extends Location> hostLocations, String url, String directory);
}
