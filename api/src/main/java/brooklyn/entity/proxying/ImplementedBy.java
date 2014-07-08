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
package brooklyn.entity.proxying;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import brooklyn.entity.Entity;

/**
 * A pointer to the default implementation of an entity.
 * 
 * A common naming convention is for the implementation class to have the suffix "Impl",
 * but this is not required.
 * 
 * See {@link EntityTypeRegistry} for how to override the implementation to be used, if
 * the class referenced by this annotation is not desired.
 * 
 * @author aled
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface ImplementedBy {

  /**
   * The implementation type.
   */
  Class<? extends Entity> value();
}
