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
package org.apache.brooklyn.entity.webapp;


import org.apache.brooklyn.entity.software.base.SoftwareProcessDriver;

public interface ApplicationCloudFoundryDriver extends SoftwareProcessDriver {

    //TODO delete?
    /**
     * Kills the process, ungracefully and immediately where possible (e.g. with `kill -9`).
     */
    void deleteApplication();

    /**
     * Return the number of instances that are used for an application.
     * @return
     */
    int getInstancesNumber();

    /**
     * Return the current disk quota used by the application.
     * @return
     */
    int getDisk();

    /**
     * Return the current assigned memory to the application.
     * @return
     */
    int getMemory();

}
