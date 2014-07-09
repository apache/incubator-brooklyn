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
package brooklyn.util.maven;

import java.io.File;

import brooklyn.util.net.Urls;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;

/** returns the URL for accessing the artifact, assuming sonatype for snapshots and maven.org for releases by default,
 * with some methods checking local file system, and allowing the generators for each to be specified */
public class MavenRetriever {

    public static final Function<MavenArtifact,String> SONATYPE_SNAPSHOT_URL_GENERATOR = new Function<MavenArtifact, String>() {
        public String apply(MavenArtifact artifact) {
            return "https://oss.sonatype.org/service/local/artifact/maven/redirect?" +
                    "r=snapshots&" +
                    "v="+Urls.encode(artifact.version)+"&" +
                    "g="+Urls.encode(artifact.groupId)+"&" +
                    "a="+Urls.encode(artifact.artifactId)+"&" +
                    (artifact.classifier!=null ? "c="+Urls.encode(artifact.classifier)+"&" : "")+
                    "e="+Urls.encode(artifact.packaging);
        }
    };

    public static final Function<MavenArtifact,String> MAVEN_CENTRAL_URL_GENERATOR = new Function<MavenArtifact, String>() {
        public String apply(MavenArtifact artifact) {
            return "http://search.maven.org/remotecontent?filepath="+
                    Urls.encode(Strings.replaceAllNonRegex(artifact.groupId, ".", "/")+"/"+
                            artifact.artifactId+"/"+artifact.version+"/"+
                            artifact.getFilename());
        }
    };

    public static final Function<MavenArtifact,String> LOCAL_REPO_PATH_GENERATOR = new Function<MavenArtifact, String>() {
        public String apply(MavenArtifact artifact) {
            return System.getProperty("user.home")+"/.m2/repository/"+
                    Strings.replaceAllNonRegex(artifact.groupId, ".", "/")+"/"+
                            artifact.artifactId+"/"+artifact.version+"/"+
                            artifact.getFilename();
        }
    };

    protected Function<MavenArtifact,String> snapshotUrlGenerator = SONATYPE_SNAPSHOT_URL_GENERATOR;
    protected Function<MavenArtifact,String> releaseUrlGenerator = MAVEN_CENTRAL_URL_GENERATOR;
    
    public void setSnapshotUrlGenerator(Function<MavenArtifact, String> snapshotUrlGenerator) {
        this.snapshotUrlGenerator = snapshotUrlGenerator;
    }
    
    public void setReleaseUrlGenerator(Function<MavenArtifact, String> releaseUrlGenerator) {
        this.releaseUrlGenerator = releaseUrlGenerator;
    }
    
    public String getHostedUrl(MavenArtifact artifact) {
        if (artifact.isSnapshot()) return snapshotUrlGenerator.apply(artifact);
        else return releaseUrlGenerator.apply(artifact);
    }
    
    public String getLocalPath(MavenArtifact artifact) {
        return LOCAL_REPO_PATH_GENERATOR.apply(artifact);
    }
    
    public boolean isInstalledLocally(MavenArtifact artifact) {
        return new File(getLocalPath(artifact)).exists();
    }

    /** returns a URL for accessing the given artifact, preferring a local file if available,
     * else generating a hosted URL (but not checking) */
    public String getLocalUrl(MavenArtifact artifact) {
        if (isInstalledLocally(artifact)) return "file://"+getLocalPath(artifact);
        if (artifact.isSnapshot()) return snapshotUrlGenerator.apply(artifact);
        else return releaseUrlGenerator.apply(artifact);
    }
    
    /** returns a URL for accessing the artifact from the local machine (ie preferring a local repo),
     * using the default remote sits (sonatype for snapshots and maven.org for releases) */
    public static String localUrl(MavenArtifact artifact) {
        return new MavenRetriever().getLocalUrl(artifact);
    }

    /** returns a URL for accessing the artifact from any machine (ie not allowing a local repo),
     * using the default remote sits (sonatype for snapshots and maven.org for releases) */
    public static String hostedUrl(MavenArtifact artifact) {
        return new MavenRetriever().getHostedUrl(artifact);
    }


}
