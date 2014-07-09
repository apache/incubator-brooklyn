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
package brooklyn.util.task;

import com.google.common.base.Supplier;

/**
 * A class that supplies objects of a single type. When used as a ConfigKey value,
 * the evaluation is deferred until getConfig() is called. The returned value will then
 * be coerced to the correct type. 
 * 
 * Subsequent calls to getConfig will result in further calls to deferredProvider.get(), 
 * rather than reusing the result. If you want to reuse the result, consider instead 
 * using a Future.
 * 
 * Note that this functionality replaces the ues of Closure in brooklyn 0.4.0, which 
 * served the same purpose.
 */
public interface DeferredSupplier<T> extends Supplier<T> {
    @Override
    T get();
}
