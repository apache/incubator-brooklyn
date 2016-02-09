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
package org.apache.brooklyn.rest;

import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Application;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

public class BrooklynRestApp extends Application {
    private Set<Object> singletons;

    public BrooklynRestApp() {
        singletons = Sets.newHashSet(BrooklynRestApi.getAllResources());
    }

    public BrooklynRestApp singleton(Object singleton) {
        singletons.add(singleton);
        return this;
    }

    @Override
    public Set<Object> getSingletons() {
        return singletons;
    }

    //Uncomment after removing jersey dependencies
    //@Override
    public Map<String, Object> getProperties() {
        return ImmutableMap.<String, Object>of(
                // Makes sure that all exceptions are handled by our custom DefaultExceptionMapper
                "default.wae.mapper.least.specific", Boolean.TRUE);
    }

}


