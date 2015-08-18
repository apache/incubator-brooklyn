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
package org.apache.brooklyn.api.internal;

import java.util.ServiceLoader;

import org.apache.brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;

/** 
 * This class grants access to implementations in core for operations needed in API classes.
 * The majority of the API classes are interfaces or have minimal behaviour, but there are a
 * few instances where more complex behaviour from core is desired.
 * <p>
 * This class acts as a bridge for those instances. See the concrete implementation of the
 * {@link ApiObjectsFactoryInterface} in brooklyn-core class ApiObjectsFactoryImpl.
 */
@Beta
public class ApiObjectsFactory {
    
    private static Maybe<ApiObjectsFactoryInterface> INSTANCE;

    private static synchronized ApiObjectsFactoryInterface getFactoryInstance() {
        // defer initialization to allow any other static initialization to complete,
        // and use maybe so we (1) don't check multiple times, but (2) do throw error in the caller's stack
        if (INSTANCE!=null) return INSTANCE.get();
        
        ServiceLoader<ApiObjectsFactoryInterface> LOADER = ServiceLoader.load(ApiObjectsFactoryInterface.class);
        for (ApiObjectsFactoryInterface item : LOADER) {
            INSTANCE = Maybe.of(item);
            return INSTANCE.get();
        }
        INSTANCE = Maybe.absent("Implementation of " + ApiObjectsFactoryInterface.class + " not found on classpath; "
            + "can be caused by IDE not copying resources, or by something else clobbering non-class resources needed for service loading");
        return INSTANCE.get();
    }

    /**
     * Create (if necessary) and return the concrete implementation from core for the
     * methods exposed here. */
    public static ApiObjectsFactoryInterface get() {
        return getFactoryInstance();
    }
}
