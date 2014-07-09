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
package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.concurrent.TimeUnit;

import brooklyn.event.AttributeSensor;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.time.Duration;

/**
 * Configuration for polling, which is being added to a feed (e.g. to poll a given URL over http).
 * 
 * @author aled
 */
public class PollConfig<V, T, F extends PollConfig<V, T, F>> extends FeedConfig<V, T, F> {

    private long period = -1;
    private String description;

    public PollConfig(AttributeSensor<T> sensor) {
        super(sensor);
    }

    public PollConfig(PollConfig<V,T,F> other) {
        super(other);
        this.period = other.period;
    }

    public long getPeriod() {
        return period;
    }
    
    public F period(Duration val) {
        checkArgument(val.toMilliseconds() >= 0, "period must be greater than or equal to zero");
        this.period = val.toMilliseconds();
        return self();
    }
    
    public F period(long val) {
        checkArgument(val >= 0, "period must be greater than or equal to zero");
        this.period = val; return self();
    }
    
    public F period(long val, TimeUnit units) {
        checkArgument(val >= 0, "period must be greater than or equal to zero");
        return period(units.toMillis(val));
    }
    
    public F description(String description) {
        this.description = description;
        return self();
    }
    
    @Override
    public String toString() {
        if (description!=null) return description;
        return JavaClassNames.simpleClassName(this);
    }

}
