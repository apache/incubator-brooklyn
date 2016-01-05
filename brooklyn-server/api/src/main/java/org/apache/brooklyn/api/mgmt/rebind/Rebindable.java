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
package org.apache.brooklyn.api.mgmt.rebind;

import org.apache.brooklyn.api.mgmt.rebind.mementos.Memento;

import com.google.common.annotations.Beta;

/**
 * Indicates that this can be recreated, e.g. after a brooklyn restart, and by
 * using a {@link Memento} it can repopulate the brooklyn objects. The purpose
 * of the rebind is to reconstruct and reconnect the brooklyn objects, including
 * binding them to external resources.
 * 
 * Users are strongly discouraged to call or use this interface.
 * It is for internal use only, relating to persisting/rebinding entities.
 * This interface may change (or be removed) in a future release without notice.
 */
@Beta
public interface Rebindable {

    public RebindSupport<?> getRebindSupport();
    
}
