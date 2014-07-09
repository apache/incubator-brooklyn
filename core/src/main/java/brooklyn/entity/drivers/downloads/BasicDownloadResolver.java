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
package brooklyn.entity.drivers.downloads;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

public class BasicDownloadResolver implements DownloadResolver {

    private final List<String> targets;
    private final String filename;
    private final String unpackDirectoryName;

    public BasicDownloadResolver(Iterable<String> targets, String filename) {
        this(targets, filename, null);
    }
    
    public BasicDownloadResolver(Iterable<String> targets, String filename, String unpackDirectoryName) {
        this.targets = ImmutableList.copyOf(checkNotNull(targets, "targets"));
        this.filename = checkNotNull(filename, "filename");
        this.unpackDirectoryName = unpackDirectoryName;
    }
    
    @Override
    public List<String> getTargets() {
        return targets;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public String getUnpackedDirectoryName(String defaultVal) {
        return unpackDirectoryName == null ? defaultVal : unpackDirectoryName;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("targets", targets).add("filename", filename)
                .add("unpackDirName", unpackDirectoryName).omitNullValues().toString();
    }
}
