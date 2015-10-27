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
package org.apache.brooklyn.util;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.reflect.TypeToken;

@SuppressWarnings("serial")
public class GenericTypes {
    public static final TypeToken<Collection<String>> COLLECTION_STRING = new TypeToken<Collection<String>>() {};
    public static final TypeToken<List<String>> LIST_STRING = new TypeToken<List<String>>() {};
    public static final TypeToken<Set<String>> SET_STRING = new TypeToken<Set<String>>() {};
    public static final TypeToken<Collection<Integer>> COLLECTION_INTEGER = new TypeToken<Collection<Integer>>() {};
    public static final TypeToken<Set<Integer>> LIST_INTEGER = new TypeToken<Set<Integer>>() {};
    public static final TypeToken<Set<Integer>> SET_INTEGER = new TypeToken<Set<Integer>>() {};
    public static final TypeToken<Map<String,Object>> MAP_STRING_OBJECT = new TypeToken<Map<String,Object>>() {};
}
