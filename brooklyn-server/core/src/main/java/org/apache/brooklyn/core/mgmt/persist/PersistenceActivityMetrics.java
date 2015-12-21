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
package org.apache.brooklyn.core.mgmt.persist;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;

public class PersistenceActivityMetrics {
    
    final static int MAX_ERRORS = 200;
    
    long count=0, failureCount=0;
    Long lastSuccessTime, lastDuration, lastFailureTime;
    List<Map<String,Object>> errorMessages = MutableList.of();

    public void noteSuccess(Duration duration) {
        count++;
        lastSuccessTime = System.currentTimeMillis();
        lastDuration = duration.toMilliseconds();
    }
    
    public void noteFailure(Duration duration) {
        count++;
        failureCount++;
        lastFailureTime = System.currentTimeMillis();
        lastDuration = duration!=null ? duration.toMilliseconds() : -1;
    }

    public void noteError(String error) {
        noteErrorObject(error);
    }
    
    public void noteError(List<?> error) {
        noteErrorObject(error);
    }
    
    /** error should be json-serializable; exceptions can be problematic */
    protected synchronized void noteErrorObject(Object error) {
        errorMessages.add(0, MutableMap.<String,Object>of("error", error, "timestamp", System.currentTimeMillis()));
        while (errorMessages.size() > MAX_ERRORS) {
            errorMessages.remove(errorMessages.size()-1);
        }
    }
    
    public synchronized Map<String,Object> asMap() {
        Map<String,Object> result = MutableMap.of();
        result.put("count", count);
        result.put("lastSuccessTimeUtc", lastSuccessTime);
        result.put("lastSuccessTimeMillisSince", since(lastSuccessTime));
        result.put("lastDuration", lastDuration);
        result.put("failureCount", failureCount);
        result.put("lastFailureTimeUtc", lastFailureTime);
        result.put("lastFailureTimeMillisSince", since(lastFailureTime));
        result.put("errorMessages", MutableList.copyOf(errorMessages));
        return result;
    }

    private Long since(Long time) {
        if (time==null) return null;
        return System.currentTimeMillis() - time;
    }
    
}