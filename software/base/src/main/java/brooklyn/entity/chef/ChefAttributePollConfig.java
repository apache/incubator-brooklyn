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
package brooklyn.entity.chef;

import org.apache.brooklyn.api.event.AttributeSensor;

import brooklyn.event.feed.PollConfig;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class ChefAttributePollConfig<T> extends PollConfig<Object, T, ChefAttributePollConfig<T>>{

    private String chefAttributePath;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ChefAttributePollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        onSuccess((Function)Functions.identity());
    }

    public ChefAttributePollConfig(ChefAttributePollConfig<T> other) {
        super(other);
        this.chefAttributePath = other.chefAttributePath;
    }

    public String getChefAttributePath() {
        return chefAttributePath;
    }
    
    public ChefAttributePollConfig<T> chefAttributePath(String val) {
        this.chefAttributePath = val; return this;
    }
    
    @Override protected String toStringBaseName() { return "chef"; }
    @Override protected String toStringPollSource() { return chefAttributePath; }
    
}
