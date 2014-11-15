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
package brooklyn.management.ha;

public enum MementoCopyMode {
    /** Use items currently managed at this node */ 
    LOCAL,
    /** Use items as stored in the remote persistence store */ 
    REMOTE, 
    /** Auto-detect whether to use {@link #LOCAL} or {@link #REMOTE} depending on the
     * HA mode of this management node (usually {@link #LOCAL} for master and {@link #REMOTE} otherwise)*/ 
    AUTO 
}
