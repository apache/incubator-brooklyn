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
package brooklyn.internal;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

/**
 * For enabling/disabling experimental features.
 * They can be enabled via java system properties, or by explicitly calling {@link #setEnablement(String, boolean)}.
 * <p>
 * For example, start brooklyn with {@code -Dbrooklyn.experimental.feature.policyPersistence=true}
 * 
 * @author aled
 */
public class BrooklynFeatureEnablement {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynFeatureEnablement.class);

    public static final String FEATURE_POLICY_PERSISTENCE_PROPERTY = "brooklyn.experimental.feature.policyPersistence";
    
    public static final String FEATURE_ENRICHER_PERSISTENCE_PROPERTY = "brooklyn.experimental.feature.enricherPersistence";
    
    private static final Map<String, Boolean> FEATURE_ENABLEMENTS = Maps.newLinkedHashMap();

    private static final Object MUTEX = new Object();
    
    static void setDefaults() {
        // Idea is here one can put experimental features that are *enabled* by default, but 
        // that can be turned off via system properties, or vice versa.
        // Typically this is useful where a feature is deemed risky!
        
        setDefault(FEATURE_POLICY_PERSISTENCE_PROPERTY, true);
        setDefault(FEATURE_ENRICHER_PERSISTENCE_PROPERTY, true);
    }
    
    static {
        setDefaults();
    }
    
    public static boolean isEnabled(String property) {
        synchronized (MUTEX) {
            if (!FEATURE_ENABLEMENTS.containsKey(property)) {
                String rawVal = System.getProperty(property);
                boolean val = Boolean.parseBoolean(rawVal);
                FEATURE_ENABLEMENTS.put(property, val);
            }
            return FEATURE_ENABLEMENTS.get(property);
        }
    }

    public static boolean enable(String property) {
        return setEnablement(property, true);
    }
    
    public static boolean disable(String property) {
        return setEnablement(property, false);
    }
    
    public static boolean setEnablement(String property, boolean val) {
        synchronized (MUTEX) {
            boolean oldVal = isEnabled(property);
            FEATURE_ENABLEMENTS.put(property, val);
            return oldVal;
        }
    }
    
    static void setDefault(String property, boolean val) {
        synchronized (MUTEX) {
            if (!FEATURE_ENABLEMENTS.containsKey(property)) {
                String rawVal = System.getProperty(property);
                if (rawVal == null) {
                    FEATURE_ENABLEMENTS.put(property, val);
                    LOG.debug("Default enablement of "+property+" set to "+val);
                } else {
                    LOG.debug("Not setting default enablement of "+property+" to "+val+", because system property is "+rawVal);
                }
            }
        }
    }
    
    static void clearCache() {
        synchronized (MUTEX) {
            FEATURE_ENABLEMENTS.clear();
            setDefaults();
        }
    }
}
