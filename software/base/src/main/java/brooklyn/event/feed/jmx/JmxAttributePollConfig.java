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
import javax.management.ObjectName;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class JmxAttributePollConfig<T> extends PollConfig<Object, T, JmxAttributePollConfig<T>>{

    private ObjectName objectName;
    private String attributeName;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public JmxAttributePollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        onSuccess((Function)Functions.identity());
    }

    public JmxAttributePollConfig(JmxAttributePollConfig<T> other) {
        super(other);
        this.objectName = other.objectName;
        this.attributeName = other.attributeName;
    }

    public ObjectName getObjectName() {
        return objectName;
    }
    
    public String getAttributeName() {
        return attributeName;
    }
    
    public JmxAttributePollConfig<T> objectName(ObjectName val) {
        this.objectName = val; return this;
    }
    
    public JmxAttributePollConfig<T> objectName(String val) {
        try {
            return objectName(new ObjectName(val));
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid object name ("+val+")", e);
        }
    }
    
    public JmxAttributePollConfig<T> attributeName(String val) {
        this.attributeName = val; return this;
    }
    
    @Override
    public String toString() {
        return "jmx["+objectName+":"+attributeName+"]";
    }
}
