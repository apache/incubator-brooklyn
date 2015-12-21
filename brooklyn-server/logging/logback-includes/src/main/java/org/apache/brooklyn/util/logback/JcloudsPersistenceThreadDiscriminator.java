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
package org.apache.brooklyn.util.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.Discriminator;

/**
 * Discriminates logging events per whether the current thread is named "brooklyn-persister" or not.
 * <p>
 * Use a {@link ch.qos.logback.classic.sift.SiftingAppender SiftingAppender} and refer to the
 * <code>jcloudsPersistSwitch</code> property. The property's value will be either "jclouds-persister",
 * for messages logged from the persistence thread, and "jclouds" otherwise.
 */
public class JcloudsPersistenceThreadDiscriminator implements Discriminator<ILoggingEvent> {

    private static final String PERSISTENCE_THREAD = "brooklyn-persister";
    private static final String KEY = "jcloudsPersistSwitch";

    private boolean isStarted;

    @Override
    public String getDiscriminatingValue(ILoggingEvent o) {
        return Thread.currentThread().getName().startsWith(PERSISTENCE_THREAD)
                ? "persistence"
                : "jclouds";
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public void start() {
        isStarted = true;
    }

    @Override
    public void stop() {
        isStarted = false;
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }
}

