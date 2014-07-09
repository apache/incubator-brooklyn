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
package io.brooklyn.camp.test.fixture;

import java.net.URL;

import io.brooklyn.camp.BasicCampPlatform;
import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.test.mock.web.MockWebPlatform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.reporters.Files;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AbstractRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractRestResourceTest.class);
    
    protected BasicCampPlatform platform;
    protected CampServer server;
    
    @BeforeClass
    public void startServer() {
        platform = new BasicCampPlatform();
        populate();
        
        // new server
        server = new CampServer(platform, "").start();
    }
    
    protected void populate() {
        MockWebPlatform.populate(platform);
    }

    @AfterClass 
    public void stopServer() {
        if (server!=null)
            server.stop();
    }
    
    public String load(String path) {
        try {
            String base = "http://localhost:"+server.getPort();
            String x = path.startsWith(base) ? path : Urls.mergePaths(base, path);
            log.debug("Reading from: "+x);
            String s = Files.streamToString(new URL(x).openStream());
            log.debug("Result from "+x+": "+s);
            return s;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public <T> T load(String path, Class<T> type) {
        try {
            String data = load(path);
            return new ObjectMapper().readValue(data, type);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
}
