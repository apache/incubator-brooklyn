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
package brooklyn.config;

import java.util.Map;

/** convenience extension where map is principally strings or converted to strings
 * (supporting BrooklynProperties) */
public interface StringConfigMap extends ConfigMap {
    /** @see #getFirst(java.util.Map, String...) */
    public String getFirst(String... keys);
    /** returns the value of the first key which is defined
     * <p>
     * takes the following flags:
     * 'warnIfNone' or 'failIfNone' (both taking a boolean (to use default message) or a string (which is the message));
     * and 'defaultIfNone' (a default value to return if there is no such property);
     * defaults to no warning and null default value */
    public String getFirst(@SuppressWarnings("rawtypes") Map flags, String... keys);
}
