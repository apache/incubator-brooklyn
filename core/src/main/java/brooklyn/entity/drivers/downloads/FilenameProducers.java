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

import javax.annotation.Nullable;

import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadRequirement;
import brooklyn.entity.drivers.downloads.DownloadResolverManager.DownloadTargets;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;

public class FilenameProducers {

    public static String inferFilename(String target) {
        String result = target.substring(target.lastIndexOf("/")+1);
        return result.contains("?") ? result.substring(0, result.indexOf("?")) : result;
    }

    public static Function<DownloadRequirement, String> fromFilenameProperty() {
        return new Function<DownloadRequirement, String>() {
            @Override public String apply(@Nullable DownloadRequirement req) {
                Object filename = req.getProperties().get("filename");
                return (filename != null) ? filename.toString() : null;
            }
        };
    }
    
    public static Function<DownloadRequirement, String> firstPrimaryTargetOf(final Function<DownloadRequirement, DownloadTargets> producer) {
        return new Function<DownloadRequirement, String>() {
            @Override public String apply(@Nullable DownloadRequirement req) {
                DownloadTargets targets = producer.apply(req);
                List<String> primaryTargets = targets.getPrimaryLocations();
                for (String primaryTarget : primaryTargets) {
                    String result = inferFilename(primaryTarget);
                    if (!Strings.isBlank(result)) return result;
                }
                return null;
            }
        };
    }
}
