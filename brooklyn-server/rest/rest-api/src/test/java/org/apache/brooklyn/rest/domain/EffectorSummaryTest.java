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
package org.apache.brooklyn.rest.domain;

import java.net.URI;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class EffectorSummaryTest extends AbstractDomainTest {

    @Override
    protected String getPath() {
        return "fixtures/effector-summary.json";
    }

    @Override
    protected Object getDomainObject() {
        return new EffectorSummary(
                "stop",
                "void",
                ImmutableSet.<EffectorSummary.ParameterSummary<?>>of(),
                "Effector description",
                ImmutableMap.of(
                        "self", URI.create("/v1/applications/redis-app/entities/redis-ent/effectors/stop")));
    }

}
