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
package brooklyn.entity.webapp;

import java.io.File;
import java.util.Set;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface JavaWebAppDriver extends JavaSoftwareProcessDriver {

    Set<String> getEnabledProtocols();

    Integer getHttpPort();

    Integer getHttpsPort();

    HttpsSslConfig getHttpsSslConfig();
    
    void deploy(File file);

    void deploy(File f, String targetName);

    /**
     * deploys a URL as a webapp at the appserver;
     * returns a token which can be used as an argument to undeploy,
     * typically the web context with leading slash where the app can be reached (just "/" for ROOT)
     * <p>
     * see {@link JavaWebAppSoftwareProcess#deploy(String, String)} for details of how input filenames are handled
     */
    String deploy(String url, String targetName);
    
    void undeploy(String targetName);
    
    FilenameToWebContextMapper getFilenameContextMapper();
}
