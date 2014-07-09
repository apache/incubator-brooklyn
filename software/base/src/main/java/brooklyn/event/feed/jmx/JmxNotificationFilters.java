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
package brooklyn.event.feed.jmx;

import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationFilterSupport;

public class JmxNotificationFilters {

    private JmxNotificationFilters() {} // instead use static utility methods
    
    /**
     * Matches the given notification type.
     * @see {@link NotificationFilterSupport#enableType(String)}
     */
    public static NotificationFilter matchesType(String type) {
        return matchesTypes(type);
    }

    /**
     * Matches any of the given notification types.
     * @see {@link NotificationFilterSupport#enableType(String)}
     */
    public static NotificationFilter matchesTypes(String... types) {
        NotificationFilterSupport result = new NotificationFilterSupport();
        for (String type : types) {
            result.enableType(type);
        }
        return result;
    }

    /**
     * @deprecated since 0.6.0;
     *             only works if this brooklyn class is on the classpath of the JVM that your 
     *             subscribing to notifications on (because it tries to push the filter instance
     *             to that JVM). So of very limited use in real-world java processes to be managed.
     *             Therefore this will be deleted to avoid people hitting this surprising behaviour.
     */
    @SuppressWarnings("serial")
    public static NotificationFilter matchesTypeRegex(final String typeRegex) {
        return new NotificationFilter() {
            @Override public boolean isNotificationEnabled(Notification notif) {
                return notif.getType().matches(typeRegex);
            }
        };
    }
}
