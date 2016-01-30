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
package org.apache.brooklyn.core.sensor.password;

import java.util.Map;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.AddSensor;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.text.Identifiers;

public class CreatePasswordSensor extends AddSensor<String> {

    public static final ConfigKey<Integer> PASSWORD_LENGTH = ConfigKeys.newIntegerConfigKey("password.length", "The length of the password to be created", 12);

    public static final ConfigKey<String> ACCEPTABLE_CHARS = ConfigKeys.newStringConfigKey("password.chars", "The characters allowed in password");

    private Integer passwordLength;
    private String acceptableChars;

    public CreatePasswordSensor(Map<String, String> params) {
        this(ConfigBag.newInstance(params));
    }

    public CreatePasswordSensor(ConfigBag params) {
        super(params);
        passwordLength = params.get(PASSWORD_LENGTH);
        acceptableChars = params.get(ACCEPTABLE_CHARS);
    }

    @Override
    public void apply(EntityLocal entity) {
        super.apply(entity);

        String password = acceptableChars == null
                ? Identifiers.makeRandomPassword(passwordLength)
                : Identifiers.makeRandomPassword(passwordLength, acceptableChars);
        
        entity.sensors().set(sensor, password);
    }
}
