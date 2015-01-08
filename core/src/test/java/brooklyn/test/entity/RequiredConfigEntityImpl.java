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
package brooklyn.test.entity;

import static com.google.common.base.Preconditions.checkNotNull;

public class RequiredConfigEntityImpl extends TestEntityImpl implements RequiredConfigEntity {

    @Override
    public void init() {
        super.init();
        checkNotNull(getConfig(NON_NULL_CONFIG_WITH_DEFAULT_VALUE),
                "Require non-null value for " + NON_NULL_CONFIG_WITH_DEFAULT_VALUE);
        checkNotNull(getConfig(NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE),
                "Require non-null value for " + NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE);
        checkNotNull(getConfig(NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE_IN_SUPER),
                "Require non-null value for " + NON_NULL_CONFIG_WITHOUT_DEFAULT_VALUE_IN_SUPER);
    }

}
