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
package org.apache.brooklyn.core.config;

import org.apache.brooklyn.api.entity.Entity;
import org.slf4j.Logger;

import brooklyn.entity.basic.EntityInternal;

/** contains common logging categories */
public class BrooklynLogging {

    public static final String SSH_IO = "brooklyn.SSH";

    public static final String REST = "brooklyn.REST";

    /** For convenience here, since SLF4J does not define such an enum */
    public static enum LoggingLevel { ERROR, WARN, INFO, DEBUG, TRACE }

    /** As methods on {@link Logger} but taking the level as an argument */
    public static final void log(Logger logger, LoggingLevel level, String message, Object... args) {
        switch (level) {
        case ERROR: logger.error(message, args); break;
        case WARN: logger.warn(message, args); break;
        case INFO: logger.info(message, args); break;
        case DEBUG: logger.debug(message, args); break;
        case TRACE: logger.trace(message, args); break;
        }
    }

    /** As methods on {@link Logger} but taking the level as an argument */
    public static final void log(Logger logger, LoggingLevel level, String message, Throwable t) {
        switch (level) {
        case ERROR: logger.error(message, t); break;
        case WARN: logger.warn(message, t); break;
        case INFO: logger.info(message, t); break;
        case DEBUG: logger.debug(message, t); break;
        case TRACE: logger.trace(message, t); break;
        }
    }

    /** returns one of three log levels depending on the read-only status of the entity;
     * unknown should only be the case very early in the management cycle */
    public static LoggingLevel levelDependingIfReadOnly(Entity entity, LoggingLevel levelIfWriting, LoggingLevel levelIfReadOnly, LoggingLevel levelIfUnknown) {
        if (entity==null) return levelIfUnknown;
        Boolean ro = ((EntityInternal)entity).getManagementSupport().isReadOnlyRaw();
        if (ro==null) return levelIfUnknown;
        if (ro) return levelIfReadOnly;
        return levelIfWriting;
    }

    /** as {@link #levelDependendingIfReadOnly(Entity)} with {@link LoggingLevel#DEBUG} as the default,
     * but {@link LoggingLevel#TRACE} for read-only */
    public static LoggingLevel levelDebugOrTraceIfReadOnly(Entity entity) {
        return levelDependingIfReadOnly(entity, LoggingLevel.DEBUG, LoggingLevel.TRACE, LoggingLevel.DEBUG);
    }
    
}
