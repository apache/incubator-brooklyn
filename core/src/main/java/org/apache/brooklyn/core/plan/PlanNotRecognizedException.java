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
package org.apache.brooklyn.core.plan;

import org.apache.brooklyn.core.typereg.BrooklynTypePlanTransformer;
import org.apache.brooklyn.core.typereg.UnsupportedTypePlanException;

/** @deprecated since 0.9.0 use {@link UnsupportedTypePlanException} as part of switch to {@link BrooklynTypePlanTransformer} */
@Deprecated 
public class PlanNotRecognizedException extends RuntimeException {

    private static final long serialVersionUID = -5590108442839125317L;

    public PlanNotRecognizedException(String message, Throwable cause) {
        super(message, cause);
    }

    public PlanNotRecognizedException(String message) {
        super(message);
    }

    public PlanNotRecognizedException(Throwable cause) {
        super(cause);
    }

}
