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
package brooklyn.entity.group;

/**
 * Indicates that a stop operation has failed - e.g. stopping of an entity
 * when doing {@link DynamicCluster#replaceMember(String)}.
 * 
 * This exception is generally only used when it is necessary to distinguish
 * between different errors - e.g. when replacing a member, did it fail starting
 * the new member or stopping the old member.
 */
public class StopFailedRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 8993327511541890753L;

    public StopFailedRuntimeException(String message) {
        super(message);
    }
    
    public StopFailedRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
