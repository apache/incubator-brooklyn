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

/** Indicates a runtime exception which has been propagated via {@link Exceptions#propagate} */
public class PropagatedRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 3959054308510077172L;

    final boolean causeEmbeddedInMessage;
    
    /** Callers should typically *not* attempt to summarise the cause in the message here; use toString() to get extended information */
    public PropagatedRuntimeException(String message, Throwable cause) {
        super(message, cause);
        causeEmbeddedInMessage = message.endsWith(Exceptions.collapseText(getCause()));
    }

    public PropagatedRuntimeException(String message, Throwable cause, boolean causeEmbeddedInMessage) {
        super(message, cause);
        this.causeEmbeddedInMessage = causeEmbeddedInMessage;
    }

    public PropagatedRuntimeException(Throwable cause) {
        super("" /* do not use default message as that destroys the toString */, cause);
        causeEmbeddedInMessage = false;
    }

    @Override
    public String toString() {
        if (causeEmbeddedInMessage) return super.toString();
        else return Exceptions.appendSeparator(super.toString(), Exceptions.collapseText(getCause()));
    }
    
    public boolean isCauseEmbeddedInMessage() {
        return causeEmbeddedInMessage;
    }
}
