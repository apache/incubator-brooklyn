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
package brooklyn.util.exceptions;

/** Exception indicating a fatal error, typically used in CLI routines.
 * The message supplied here should be suitable for display in a CLI response (without stack trace / exception class). */
public class FatalRuntimeException extends UserFacingException {

    private static final long serialVersionUID = -3359163414517503809L;

    public FatalRuntimeException(String message) {
        super(message);
    }
    
    public FatalRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
