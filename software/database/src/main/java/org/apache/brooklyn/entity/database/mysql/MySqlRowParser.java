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
package org.apache.brooklyn.entity.database.mysql;

import java.util.Map;

import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.text.Strings;

public class MySqlRowParser {
    public static Map<String, String> parseSingle(String row) {
        Map<String, String> values = MutableMap.of();
        String[] lines = row.split("\\n");
        for (String line : lines) {
            if (line.startsWith("*")) continue; // row delimiter
            String[] arr = line.split(":", 2);
            String key = arr[0].trim();
            String value = Strings.emptyToNull(arr[1].trim());
            values.put(key, value);
        }
        return values;
    };
}
