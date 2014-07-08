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
package io.brooklyn.camp.brooklyn;

import java.util.Map;

import org.testng.Assert;

import com.google.common.base.Preconditions;

import brooklyn.entity.Effector;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.config.ConfigBag;

public class TestSensorAndEffectorInitializer implements EntityInitializer {

    public static final String EFFECTOR_SAY_HELLO = "sayHello";
    public static final String SENSOR_LAST_HELLO = "lastHello";
    public static final String SENSOR_HELLO_DEFINED = "sensorHelloDefined";
    public static final String SENSOR_HELLO_DEFINED_EMITTED = "sensorHelloDefinedEmitted";

    protected String helloWord() { return "Hello"; }
    
    @Override
    public void apply(EntityLocal entity) {
        Effector<String> eff = Effectors.effector(String.class, EFFECTOR_SAY_HELLO).parameter(String.class, "name").impl(
            new EffectorBody<String>() {
                @Override
                public String call(ConfigBag parameters) {
                    Object name = parameters.getStringKey("name");
                    entity().setAttribute(Sensors.newStringSensor(SENSOR_LAST_HELLO), ""+name);
                    return helloWord()+" "+name;
                }
            }).build();
        ((EntityInternal)entity).getMutableEntityType().addEffector(eff);
        
        ((EntityInternal)entity).getMutableEntityType().addSensor(Sensors.newStringSensor(SENSOR_HELLO_DEFINED));
        
        AttributeSensor<String> emitted = Sensors.newStringSensor(SENSOR_HELLO_DEFINED_EMITTED);
        ((EntityInternal)entity).getMutableEntityType().addSensor(emitted);
        entity.setAttribute(emitted, "1");
    }

    public static class TestConfigurableInitializer extends TestSensorAndEffectorInitializer {
        public static final String HELLO_WORD = "helloWord";
        final String helloWord;
        public TestConfigurableInitializer(Map<String,String> params) {
            Preconditions.checkNotNull(params);
            if (params.containsKey(HELLO_WORD)) {
                helloWord = params.get(HELLO_WORD);
                Assert.assertEquals(params.size(), 1);
            } else {
                helloWord = "Hello";
                Assert.assertEquals(params.size(), 0);
            }
        }
        
        @Override
        protected String helloWord() {
            return helloWord;
        }
    }
    
}
