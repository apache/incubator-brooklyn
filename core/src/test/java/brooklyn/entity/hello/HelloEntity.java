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
package brooklyn.entity.hello;

import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicSensor;
import brooklyn.event.basic.Sensors;

@ImplementedBy(HelloEntityImpl.class)
public interface HelloEntity extends AbstractGroup {

    /** records name of the person represented by this entity */
    public static ConfigKey<String> MY_NAME = ConfigKeys.newStringConfigKey("my.name");
    
    /** this "person"'s favourite name */
    public static AttributeSensor<String> FAVOURITE_NAME = Sensors.newStringSensor("my.favourite.name");
    
    /** records age (in years) of the person represented by this entity */
    public static AttributeSensor<Integer> AGE = Sensors.newIntegerSensor("my.age");
    
    /** emits a "birthday" event whenever age is changed (tests non-attribute events) */    
    public static Sensor<Void> ITS_MY_BIRTHDAY = new BasicSensor<Void>(Void.TYPE, "my.birthday");
    
    /**  */
    public static MethodEffector<Void> SET_AGE = new MethodEffector<Void>(HelloEntity.class, "setAge");
    
    @Effector(description="allows setting the age")
    public void setAge(@EffectorParam(name="age") Integer age);
}
