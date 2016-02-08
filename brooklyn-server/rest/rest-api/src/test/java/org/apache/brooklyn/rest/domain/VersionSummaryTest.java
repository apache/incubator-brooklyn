/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.rest.domain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class VersionSummaryTest  extends AbstractDomainTest {

    @Override
    protected String getPath() {
        return "fixtures/server-version.json";
    }

    @Override
    protected Object getDomainObject() {
        BrooklynFeatureSummary features = new BrooklynFeatureSummary(
                "Sample Brooklyn Project com.acme.sample:brooklyn-sample v0.1.0-SNAPSHOT",
                "com.acme.sample.brooklyn-sample",
                "0.1.0.SNAPSHOT",
                "523305000",
                ImmutableMap.of("Brooklyn-Feature-Build-Id", "e0fee1adf795c84eec4735f039503eb18d9c35cc")
        );
        return new VersionSummary(
                "0.7.0-SNAPSHOT",
                "cb4f0a3af2f5042222dd176edc102bfa64e7e0b5",
                "versions",
                ImmutableList.of(features)
        );
    }

}
