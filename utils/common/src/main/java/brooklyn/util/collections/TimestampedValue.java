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
package brooklyn.util.collections;

import com.google.common.base.Objects;

public class TimestampedValue<T> {

    private final T value;
    private final long timestamp;
    
    public TimestampedValue(T value, long timestamp) {
        this.value = value;
        this.timestamp = timestamp;
    }
    
    public T getValue() {
        return value;
    }
    
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value, timestamp);
    }
    
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TimestampedValue)) {
            return false;
        }
        TimestampedValue<?> o = (TimestampedValue<?>) other;
        return o.getTimestamp() == timestamp && Objects.equal(o.getValue(), value);
    }
    
    @Override
    public String toString() {
        return "val="+value+"; timestamp="+timestamp;
    }
}
