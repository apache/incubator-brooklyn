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
package brooklyn.catalog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 
 * annotation that can be placed on an Application (template), entity or policy 
 * to give metadata for when used in a catalog and to indicate inclusion in annotation-scanned catalogs
 * <p>
 * the "id" field used in the catalog is not exposed here but is always taken as the Class.getName() of the annotated item
 * if loaded from an annotation.  (the "type" field unsurprisingly is given the same value).  
 * {@link #name()}, if not supplied, is the SimpleName of the class.
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = { ElementType.TYPE })
public @interface Catalog {

    String name() default "";
    String description() default "";
    String iconUrl() default "";
    
}
