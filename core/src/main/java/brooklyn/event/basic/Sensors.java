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

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.api.event.AttributeSensor.SensorPersistenceMode;
import org.apache.brooklyn.util.net.UserAndHostAndPort;
import org.apache.brooklyn.util.text.StringFunctions;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import brooklyn.config.render.RendererHints;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.net.HostAndPort;
import com.google.common.reflect.TypeToken;

public class Sensors {

    @Beta
    public static <T> Builder<T> builder(TypeToken<T> type, String name) {
        return new Builder<T>().type(type).name(name);
    }

    @Beta
    public static <T> Builder<T> builder(Class<T> type, String name) {
        return new Builder<T>().type(type).name(name);
    }
    
    @Beta
    public static class Builder<T> {
        private String name;
        private TypeToken<T> type;
        private String description;
        private SensorPersistenceMode persistence;
        
        protected Builder() { // use builder(type, name) instead
        }
        public Builder<T> name(String val) {
            this.name = checkNotNull(val, "name"); return this;
        }
        public Builder<T> type(Class<T> val) {
            return type(TypeToken.of(val));
        }
        public Builder<T> type(TypeToken<T> val) {
            this.type = checkNotNull(val, "type"); return this;
        }
        public Builder<T> description(String val) {
            this.description = val; return this;
        }
        public Builder<T> persistence(SensorPersistenceMode val) {
            this.persistence = val; return this;
        }
        public AttributeSensor<T> build() {
            return new BasicAttributeSensor<T>(type, name, description, persistence);
        }
    }

    public static <T> AttributeSensor<T> newSensor(Class<T> type, String name) {
        return new BasicAttributeSensor<T>(type, name);
    }

    public static <T> AttributeSensor<T> newSensor(Class<T> type, String name, String description) {
        return new BasicAttributeSensor<T>(type, name, description);
    }

    public static <T> AttributeSensor<T> newSensor(TypeToken<T> type, String name, String description) {
        return new BasicAttributeSensor<T>(type, name, description);
    }

    public static AttributeSensor<String> newStringSensor(String name) {
        return newSensor(String.class, name);
    }

    public static AttributeSensor<String> newStringSensor(String name, String description) {
        return newSensor(String.class, name, description);
    }

    public static AttributeSensor<Integer> newIntegerSensor(String name) {
        return newSensor(Integer.class, name);
    }

    public static AttributeSensor<Integer> newIntegerSensor(String name, String description) {
        return newSensor(Integer.class, name, description);
    }

    public static AttributeSensor<Long> newLongSensor(String name) {
        return newSensor(Long.class, name);
    }

    public static AttributeSensor<Long> newLongSensor(String name, String description) {
        return newSensor(Long.class, name, description);
    }

    public static AttributeSensor<Double> newDoubleSensor(String name) {
        return newSensor(Double.class, name);
    }

    public static AttributeSensor<Double> newDoubleSensor(String name, String description) {
        return newSensor(Double.class, name, description);
    }

    public static AttributeSensor<Boolean> newBooleanSensor(String name) {
        return newSensor(Boolean.class, name);
    }

    public static AttributeSensor<Boolean> newBooleanSensor(String name, String description) {
        return newSensor(Boolean.class, name, description);
    }

    // Extensions to sensors

    public static <T> AttributeSensor<T> newSensorRenamed(String newName, AttributeSensor<T> sensor) {
        return new BasicAttributeSensor<T>(sensor.getTypeToken(), newName, sensor.getDescription());
    }

    public static <T> AttributeSensor<T> newSensorWithPrefix(String prefix, AttributeSensor<T> sensor) {
        return newSensorRenamed(prefix+sensor.getName(), sensor);
    }

    // Display hints for common utility objects

    static {
        RendererHints.register(Duration.class, RendererHints.displayValue(Time.fromDurationToTimeStringRounded()));
        RendererHints.register(HostAndPort.class, RendererHints.displayValue(StringFunctions.toStringFunction()));
        RendererHints.register(UserAndHostAndPort.class, RendererHints.displayValue(StringFunctions.toStringFunction()));
        RendererHints.register(InetAddress.class, RendererHints.displayValue(new Function<InetAddress,String>() {
            @Override
            public String apply(@Nullable InetAddress input) {
                return input == null ? null : input.getHostAddress();
            }
        }));

        RendererHints.register(URL.class, RendererHints.displayValue(StringFunctions.toStringFunction()));
        RendererHints.register(URL.class, RendererHints.openWithUrl(StringFunctions.toStringFunction()));
        RendererHints.register(URI.class, RendererHints.displayValue(StringFunctions.toStringFunction()));
        RendererHints.register(URI.class, RendererHints.openWithUrl(StringFunctions.toStringFunction()));
    }

}
