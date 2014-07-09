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
package brooklyn.util;

import brooklyn.BrooklynVersion;
import brooklyn.util.maven.MavenArtifact;
import brooklyn.util.maven.MavenRetriever;
import brooklyn.util.text.Strings;

public class BrooklynMavenArtifacts {

    public static MavenArtifact jar(String artifactId) {
        return artifact(null, artifactId, "jar");
    }
    
    public static MavenArtifact artifact(String subgroupUnderIoBrooklyn, String artifactId, String packaging) {
        return artifact(subgroupUnderIoBrooklyn, artifactId, packaging, null);
    }

    public static MavenArtifact artifact(String subgroupUnderIoBrooklyn, String artifactId, String packaging, String classifier) {
        return new MavenArtifact(
                Strings.isEmpty(subgroupUnderIoBrooklyn) ? "io.brooklyn" : "io.brooklyn."+subgroupUnderIoBrooklyn,
                artifactId, packaging, classifier, BrooklynVersion.get());
    }

    public static String localUrlForJar(String artifactId) {
        return MavenRetriever.localUrl(jar(artifactId));
    }
    
    public static String localUrl(String subgroupUnderIoBrooklyn, String artifactId, String packaging) {
        return MavenRetriever.localUrl(artifact(subgroupUnderIoBrooklyn, artifactId, packaging));
    }

    public static String hostedUrlForJar(String artifactId) {
        return MavenRetriever.hostedUrl(jar(artifactId));
    }
    
    public static String hostedUrl(String subgroupUnderIoBrooklyn, String artifactId, String packaging) {
        return MavenRetriever.hostedUrl(artifact(subgroupUnderIoBrooklyn, artifactId, packaging));
    }

}
