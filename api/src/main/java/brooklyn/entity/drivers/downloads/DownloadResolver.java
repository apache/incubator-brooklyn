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

import java.util.List;

import com.google.common.annotations.Beta;

/**
 * Gives download details for an entity or an entity add-on.
 * Returned by the {@link DownloadResolverManager}, when queried for a specific entity or entity add-on. 
 * 
 * @author aled
 */
public interface DownloadResolver {
    /**
     * The targets (normally URLs) for downloading the artifact. These should be tried in-order
     * until one works.
     */
    public List<String> getTargets();

    /**
     * The name of the artifact.
     * The caller is free to use this name, or not. But using this name gives consistency particularly
     * between brooklyn local-repos and brooklyn install directories.
     */
    public String getFilename();
    
    /**
     * The name of the directory in the expanded artifact (e.g. if it's a tar.gz file then the name of
     * the directory within it). If no value is known, the defaultVal will be returned.
     * 
     * This can return null if the artifact is not an archive (and if defaultVal is null).
     * 
     * TODO The driver needs to know what will happen when an install archive is unpacked (e.g. an 
     * AS7 install tgz may be automatically expanded into a directory named "jboss-as-7.1.1-FINAL").
     * However, it's unclear where the best place to encode that is. The driver supplying the default
     * seems sensible.
     */
    @Beta
    public String getUnpackedDirectoryName(String defaultVal);
}
