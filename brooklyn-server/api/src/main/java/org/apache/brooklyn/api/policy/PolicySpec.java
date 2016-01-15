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
package org.apache.brooklyn.api.policy;

import java.util.Map;

import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;

/**
 * Gives details of a policy to be created. It describes the policy's configuration, and is
 * reusable to create multiple policies with the same configuration.
 * 
 * To create a PolicySpec, it is strongly encouraged to use {@code create(...)} methods.
 * 
 * @param <T> The type of policy to be created
 * 
 * @author aled
 */
public class PolicySpec<T extends Policy> extends AbstractBrooklynObjectSpec<T,PolicySpec<T>> {

    private final static long serialVersionUID = 1L;


    /**
     * Creates a new {@link PolicySpec} instance for a policy of the given type. The returned 
     * {@link PolicySpec} can then be customized.
     * 
     * @param type A {@link Policy} class
     */
    public static <T extends Policy> PolicySpec<T> create(Class<T> type) {
        return new PolicySpec<T>(type);
    }
    
    /**
     * Creates a new {@link PolicySpec} instance with the given config, for a policy of the given type.
     * 
     * This is primarily for groovy code; equivalent to {@code PolicySpec.create(type).configure(config)}.
     * 
     * @param config The spec's configuration (see {@link PolicySpec#configure(Map)}).
     * @param type   A {@link Policy} class
     */
    public static <T extends Policy> PolicySpec<T> create(Map<?,?> config, Class<T> type) {
        return PolicySpec.create(type).configure(config);
    }
    
    protected PolicySpec(Class<T> type) {
        super(type);
    }
    
    protected void checkValidType(Class<? extends T> type) {
        checkIsImplementation(type, Policy.class);
        checkIsNewStyleImplementation(type);
    }
    
    public PolicySpec<T> uniqueTag(String uniqueTag) {
        flags.put("uniqueTag", uniqueTag);
        return this;
    }
        
}
