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
package org.apache.brooklyn.api.objs;

import java.io.Serializable;

import org.apache.brooklyn.config.ConfigKey;

public interface SpecParameter<T> extends Serializable {
    /** Short name, to be used in UI */
    String getLabel();
    /** Visible by default in UI, not all inputs may be visible at once */
    boolean isPinned();
    /** Type information for the input */
    ConfigKey<T> getType();
}
