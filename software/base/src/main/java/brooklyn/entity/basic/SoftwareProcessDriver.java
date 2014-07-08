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
package brooklyn.entity.basic;

import brooklyn.entity.drivers.EntityDriver;
import brooklyn.entity.trait.Startable;

/**
 * The {@link EntityDriver} for a {@link SoftwareProcess}.
 *
 * <p/>
 * In many cases it is cleaner to store entity lifecycle effectors (and sometimes other implementations) in a class to
 * which the entity delegates.  Classes implementing this interface provide this delegate, often inheriting utilities
 * specific to a particular transport (e.g. ssh) shared among many different entities.
 * <p/>
 * In this way, it is also possible for entities to cleanly support multiple mechanisms for start/stop and other methods.
 */
public interface SoftwareProcessDriver extends EntityDriver {

    /**
     * The entity whose components we are controlling.
     */
    EntityLocal getEntity();

    /**
     * Whether the entity components have started.
     */
    boolean isRunning();

    /**
     * Rebinds the driver to a pre-existing software process.
     */
    void rebind();

    /**
     * Performs software start (or queues tasks to do this)
     */
    void start();

    /**
     * Performs software restart (or queues tasks to do this).
     * Unlike stop/start implementations here are expected to update SERVICE_STATE for STOPPING and STARTING
     * as appropriate (but framework will set RUNNING afterwards, after detecting it is running).
     * @see Startable#restart()
     */
    void restart();
    
    /**
     * Performs software stop (or queues tasks to do this) 
     * @see Startable#stop()
     */
    void stop();
    
    /**
     * Kills the process, ungracefully and immediately where possible (e.g. with `kill -9`).
     */
    void kill();
}
