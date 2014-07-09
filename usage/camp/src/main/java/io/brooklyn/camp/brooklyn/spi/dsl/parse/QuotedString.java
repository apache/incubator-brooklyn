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
package io.brooklyn.camp.brooklyn.spi.dsl.parse;

import static com.google.common.base.Preconditions.checkNotNull;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;

import com.google.common.base.Objects;

public class QuotedString {
    private final String s;
    
    public QuotedString(String s) {
        this.s = checkNotNull(s, "string");
    }
    @Override
    public String toString() {
        return s;
    }
    public String unwrapped() {
        return JavaStringEscapes.unwrapJavaString(s);
    }
    
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof QuotedString) && ((QuotedString)obj).toString().equals(toString());
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(s);
    }
}