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
package brooklyn.event.basic;

import brooklyn.event.Sensor;

/**
 * A {@link Sensor} used to notify subscribers about events.
 */
public class BasicNotificationSensor<T> extends BasicSensor<T> {
    private static final long serialVersionUID = -7670909215973264600L;

    public BasicNotificationSensor(Class<T> type, String name) {
        this(type, name, name);
    }
    
    public BasicNotificationSensor(Class<T> type, String name, String description) {
        super(type, name, description);
    }
}
