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
package brooklyn.entity.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//FIXME Move to brooklyn.entity.effector?

/**
 * Gives meta-data about a parameter of the effector.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface EffectorParam {
    String name();
    String description() default MAGIC_STRING_MEANING_NULL;
    String defaultValue() default MAGIC_STRING_MEANING_NULL;
    boolean nullable() default true;
    
    /** Cannot use null as a default (e.g. for defaultValue); therefore define a magic string to mean that
    /* so can tell when no-one has set it. */
    public static final String MAGIC_STRING_MEANING_NULL = "null default value; do not mis-use! 3U=Hhfkr8wuov]WO";
}
