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
package brooklyn.util.flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation to indicate that a variable may be set through the use of a named argument,
 * looking for the name specified here or inferred from the annotated field/argument/object.
 * <p>
 * This is used to automate the processing where named arguments are passed in constructors
 * and other methods, and the values of those named arguments should be transferred to
 * other known fields/arguments/objects at runtime.
 * <p>
 * Fields on a class are typically set from values in a map with a call to
 * {@link FlagUtils#setFieldsFromFlags(java.util.Map, Object)}.
 * That method (and related, in the same class) will attend to the arguments here.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface SetFromFlag {

    /** the flag (key) which should be used to find the value; if empty defaults to field/argument/object name */
    String value() default "";
    
    /** whether the object should not be changed once set; defaults to false
     * <p>
     * this is partially tested for in many routines, but not all;
     * when nullable=false the testing (when done) is guaranteed.
     * however if nullable is allowed we do not distinguish between null and unset
     * so explicitly setting null then setting to a value is not detected as an illegal mutating.
     */
    boolean immutable() default false;
    
    /** whether the object is required & should not be set to null; defaults to true.
     * (there is no 'required' parameter, but setting nullable false then invoking 
     * e.g. {@link FlagUtils#checkRequiredFields(Object)} has the effect of requiring a value)
     * <p>
     * code should call that method explicitly to enforce nullable false;
     * errors are not done during a call to setFieldsFromFlags 
     * because fields may be initialised in multiple passes.) 
     * <p>
     * this is partially tested for in many routines, but not all
     */
    boolean nullable() default true;

    /** The default value, if it is not explicitly set.
     * <p>
     * The value will be coerced from String where required, for types supported by {@link TypeCoercions}.
     * <p>
     * The field will be initialised with its default value on the first call to setFieldsFromFlags
     * (or related).  (The field will not be initialised if that method is not called.) 
     */
    String defaultVal() default "";
}
