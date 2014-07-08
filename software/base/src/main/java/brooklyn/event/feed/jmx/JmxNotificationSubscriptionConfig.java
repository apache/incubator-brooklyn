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

import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.ObjectName;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.FeedConfig;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class JmxNotificationSubscriptionConfig<T> extends FeedConfig<javax.management.Notification, T, JmxNotificationSubscriptionConfig<T>>{

    private ObjectName objectName;
    private NotificationFilter notificationFilter;
    private Function<Notification, T> onNotification;

    public JmxNotificationSubscriptionConfig(AttributeSensor<T> sensor) {
        super(sensor);
        onSuccess((Function)Functions.identity());
    }

    public JmxNotificationSubscriptionConfig(JmxNotificationSubscriptionConfig<T> other) {
        super(other);
        this.objectName = other.objectName;
        this.notificationFilter = other.notificationFilter;
        this.onNotification = other.onNotification;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public NotificationFilter getNotificationFilter() {
        return notificationFilter;
    }
    
    public Function<Notification, T> getOnNotification() {
        return onNotification;
    }
    
    public JmxNotificationSubscriptionConfig<T> objectName(ObjectName val) {
        this.objectName = val; return this;
    }
    
    public JmxNotificationSubscriptionConfig<T> objectName(String val) {
        try {
            return objectName(new ObjectName(val));
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid object name ("+val+")", e);
        }
    }
    
    public JmxNotificationSubscriptionConfig<T> notificationFilter(NotificationFilter val) {
        this.notificationFilter = val; return this;
    }

    public JmxNotificationSubscriptionConfig<T> onNotification(Function<Notification,T> val) {
        this.onNotification = val; return this;
    }
}
