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
package brooklyn.util.stream;

import java.io.IOException;
import java.io.InputStream;

import com.google.common.io.InputSupplier;

public class InputStreamSupplier implements InputSupplier<InputStream> {

    private final InputStream target;

    /** @deprecated since 0.7.0; use {@link InputStreamSupplier#of(InputStream)} instead */
    @Deprecated
    public InputStreamSupplier(InputStream target) {
        this.target = target;
    }

    @Override
    public InputStream getInput() throws IOException {
        return target;
    }

    public static InputStreamSupplier of(InputStream target) {
        return new InputStreamSupplier(target);
    }

    public static InputStreamSupplier fromString(String input) {
        return new InputStreamSupplier(Streams.newInputStreamWithContents(input));
    }

}
