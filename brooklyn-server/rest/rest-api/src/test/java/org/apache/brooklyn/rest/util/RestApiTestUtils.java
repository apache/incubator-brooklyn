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
package org.apache.brooklyn.rest.util;

import java.io.InputStream;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.stream.Streams;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RestApiTestUtils {

    public static <T> T fromJson(String text, Class<T> type) {
        try {
            return new ObjectMapper().readValue(text, type);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    public static String asJson(Object x) {
        try {
            return new ObjectMapper()
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(x);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    public static String jsonFixture(String path) {
        InputStream stream = RestApiTestUtils.class.getClassLoader().getResourceAsStream(path);
        if (stream==null) throw new IllegalStateException("Cannot find resource: "+path);
        return asJson(fromJson(Streams.readFullyString(stream), Object.class));
    }

    public static <T> T fromJson(String text, TypeReference<T> type) {
        try {
            return new ObjectMapper().readValue(text, type);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
}
