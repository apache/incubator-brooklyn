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
package org.apache.brooklyn.util.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Indicates a runtime exception which has been propagated via {@link Exceptions#propagate} */
public class PropagatedRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 3959054308510077172L;
    private static final Logger LOG = LoggerFactory.getLogger(PropagatedRuntimeException.class);

    private final boolean causeEmbeddedInMessage;
    
    /** Callers should typically *not* attempt to summarise the cause in the message here; use toString() to get extended information */
    public PropagatedRuntimeException(String message, Throwable cause) {
        super(message, cause);
        warnIfWrapping(cause);
        causeEmbeddedInMessage = message.endsWith(Exceptions.collapseText(getCause()));
    }

    public PropagatedRuntimeException(String message, Throwable cause, boolean causeEmbeddedInMessage) {
        super(message, cause);
        warnIfWrapping(cause);
        this.causeEmbeddedInMessage = causeEmbeddedInMessage;
    }

    public PropagatedRuntimeException(Throwable cause) {
        super("" /* do not use default message as that destroys the toString */, cause);
        warnIfWrapping(cause);
        causeEmbeddedInMessage = false;
    }

    private void warnIfWrapping(Throwable cause) {
        if (LOG.isTraceEnabled() && cause instanceof PropagatedRuntimeException) {
            LOG.trace("Wrapping a PropagatedRuntimeException in another PropagatedRuntimeException. Call chain:", new Exception());
        }
    }

    @Override
    public String toString() {
        if (causeEmbeddedInMessage) {
            return super.toString();
        } else {
            return Exceptions.appendSeparator(super.toString(), Exceptions.collapseText(getCause()));
        }
    }

    public boolean isCauseEmbeddedInMessage() {
        return causeEmbeddedInMessage;
    }

}
