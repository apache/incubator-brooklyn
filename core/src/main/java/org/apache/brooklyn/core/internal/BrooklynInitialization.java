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
package org.apache.brooklyn.core.internal;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.core.util.crypto.SecureKeys;
import org.apache.brooklyn.core.util.flags.TypeCoercions;
import org.apache.brooklyn.location.basic.PortRanges;
import org.apache.brooklyn.util.net.Networking;

import com.google.common.annotations.Beta;

/** Various static initialization tasks are routed through this class,
 * to give us better traceability of their invocation. */ 
@Beta
public class BrooklynInitialization {

    private static AtomicBoolean done = new AtomicBoolean(false);
    
    public static void initTypeCoercionStandardAdapters() {
        TypeCoercions.initStandardAdapters();
    }

    public static void initSecureKeysBouncyCastleProvider() {
        SecureKeys.initBouncyCastleProvider();
    }

    public static void initNetworking() {
        Networking.init();
    }
    
    public static void initPortRanges() {
        PortRanges.init();
    }

    @SuppressWarnings("deprecation")
    public static void initLegacyLanguageExtensions() {
        org.apache.brooklyn.core.util.BrooklynLanguageExtensions.init();
    }

    /* other things:
     * 
     * RendererHints - done by the entity classes which need them, including Sensors
     * 
     */
    
    public synchronized static void initAll() {
        if (done.get()) return;
        initTypeCoercionStandardAdapters();
        initSecureKeysBouncyCastleProvider();
        initNetworking();
        initPortRanges();
        initLegacyLanguageExtensions();
        done.set(true);
    }

    @SuppressWarnings("deprecation")
    public synchronized static void reinitAll() {
        done.set(false);
        org.apache.brooklyn.core.util.BrooklynLanguageExtensions.reinit();
        initAll();
    }

}
