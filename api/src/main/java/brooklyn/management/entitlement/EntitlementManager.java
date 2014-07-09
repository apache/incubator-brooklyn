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
package brooklyn.management.entitlement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.annotations.Beta;

/** 
 * Entitlement lookup relies on:
 * <li>an "entitlement context", consisting of at minimum a string identifier of the user/actor for which entitlement is being requested
 * <li>an "entitlement class", representing the category of activity for which entitlement is being requested
 * <li>an "entitlement class argument", representing the specifics of the activity for which entitlement is being requested
 * <p>
 * Instances of this class typically have a 1-arg constructor taking a BrooklynProperties object
 * (configuration injected by the Brooklyn framework)
 * or a 0-arg constructor (if no external configuration is needed).
 * <p>
 * An EntitlementManagerAdapter class is available to do dispatch to common methods.
 * <p>
 * Instantiation is done e.g. by Entitlements.newManager.  
 * @since 0.7.0 */
@Beta
public interface EntitlementManager {

    public <T> boolean isEntitled(@Nullable EntitlementContext context, @Nonnull EntitlementClass<T> entitlementClass, @Nullable T entitlementClassArgument);
    
}
