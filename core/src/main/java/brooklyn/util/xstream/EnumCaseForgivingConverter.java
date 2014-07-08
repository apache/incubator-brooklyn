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
package brooklyn.util.xstream;

import brooklyn.util.exceptions.Exceptions;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.enums.EnumConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/** ... except this doesn't seem to get applied when we think it should
 * (normal xstream.resgisterConverter doesn't apply to enums) */
public class EnumCaseForgivingConverter extends EnumConverter {

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Class type = context.getRequiredType();
        if (type.getSuperclass() != Enum.class) {
            type = type.getSuperclass(); // polymorphic enums
        }
        String token = reader.getValue();
        // this is the new bit (overriding superclass to accept case-insensitive)
        return resolve(type, token);
    }

    public static <T extends Enum<T>> T resolve(Class<T> type, String token) {
        try {
            return Enum.valueOf(type, token.toUpperCase());
        } catch (Exception e) {
            
            // new stuff here:  try reading case insensitive
            
            Exceptions.propagateIfFatal(e);
            try {
                for (T v: type.getEnumConstants())
                    if (v.name().equalsIgnoreCase(token)) return v;
                throw e;
            } catch (Exception e2) {
                throw Exceptions.propagate(e2);
            }
        }
    }

}
