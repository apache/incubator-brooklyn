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

import java.lang.reflect.Type;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.container.servlet.WebConfig;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;

@Provider
public class NullServletConfigProvider implements InjectableProvider<Context, Type> { 
    public Injectable<ServletContext> getInjectable(ComponentContext ic, 
            Context a, Type c) { 
        if (ServletContext.class == c) { 
            return new Injectable<ServletContext>() {
                public ServletContext getValue() { return null; }
            }; 
        } else if (WebConfig.class == c) {
            return new Injectable<ServletContext>() {
                public ServletContext getValue() { return null; }
            }; 
        } else 
            return null; 
    } 
    public ComponentScope getScope() { 
        return ComponentScope.Singleton; 
    } 
} 
