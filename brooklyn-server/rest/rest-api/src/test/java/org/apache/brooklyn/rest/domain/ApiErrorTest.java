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

import static org.apache.brooklyn.rest.util.RestApiTestUtils.asJson;
import static org.apache.brooklyn.rest.util.RestApiTestUtils.fromJson;
import static org.apache.brooklyn.rest.util.RestApiTestUtils.jsonFixture;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.IOException;
import java.net.URI;

import org.testng.annotations.Test;
import org.testng.util.Strings;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class ApiErrorTest extends AbstractDomainTest {

    @Override
    protected String getPath() {
        return "fixtures/api-error-basic.json";
    }

    @Override
    protected Object getDomainObject() {
        return ApiError.builder()
                .message("explanatory message")
                .details("accompanying details")
                .build();
    }

    @Test
    public void testSerializeApiErrorFromThrowable() throws IOException {
        Exception e = new Exception("error");
        e.setStackTrace(Thread.currentThread().getStackTrace());

        ApiError error = ApiError.builderFromThrowable(e).build();
        ApiError deserialised = fromJson(asJson(error), ApiError.class);

        assertFalse(Strings.isNullOrEmpty(deserialised.getDetails()), "Expected details to contain exception stack trace");
        assertEquals(deserialised, error);
    }

    @Test
    public void testSerializeApiErrorWithoutDetails() throws IOException {
        ApiError error = ApiError.builder()
                .message("explanatory message")
                .build();
        assertEquals(asJson(error), jsonFixture("fixtures/api-error-no-details.json"));
    }

}
