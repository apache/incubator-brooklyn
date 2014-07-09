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
package brooklyn.util.exceptions;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CompoundRuntimeException extends RuntimeException {

    private static final long serialVersionUID = 6110995537064639587L;

    private final List<Throwable> causes;

    public CompoundRuntimeException(String message) {
        super(message);
        this.causes = Collections.emptyList();
    }

    public CompoundRuntimeException(String message, Throwable cause) {
        super(message, cause);
        this.causes = (cause == null) ? Collections.<Throwable>emptyList() : Collections.singletonList(cause);
    }

    public CompoundRuntimeException(Throwable cause) {
        super(cause);
        this.causes = (cause == null) ? Collections.<Throwable>emptyList() : Collections.singletonList(cause);
    }

    public CompoundRuntimeException(String message, Iterable<? extends Throwable> causes) {
        this(message, (Iterables.isEmpty(causes) ? null : Iterables.get(causes, 0)), causes);
    }
    public CompoundRuntimeException(String message, Throwable primaryCauseToReport, Iterable<? extends Throwable> allCauses) {
        super(message, primaryCauseToReport);
        this.causes = ImmutableList.copyOf(allCauses);
    }

    public List<Throwable> getAllCauses() {
        return causes;
    }
}
