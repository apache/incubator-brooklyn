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

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableList;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.text.Strings;

public class MavenArtifact {

    private static final Logger log = LoggerFactory.getLogger(MavenArtifact.class);
    
    protected final @Nonnull String groupId; 
    protected final @Nonnull String artifactId; 
    protected final @Nonnull String packaging;
    protected final @Nullable String classifier; 
    protected final @Nonnull String version; 
    
    /** a custom marker inserted after the artifactId and before the version, offset by an additional "-";
     * defaults to null (nothing)
     * <p>
     * uses: when a shaded JAR is built, sometimes the word shaded is inserted before the version
     * (and the "with-dependencies" classifier overwritten) */
    protected @Nullable String customFileNameAfterArtifactMarker;
    
    /** a custom marker inserted after the version and before the extension, offset by an additional "-" if non-empty;
     * defaults to {@link #getClassifier()} if null, but can replace the classifier
     * <p>
     * uses: removing classifier by specifying "", or adding a notional classifier such as "dist" */
    protected @Nullable String classifierFileNameMarker;
    
    public MavenArtifact(String groupId, String artifactId, String packaging, String classifier, String version) {
        super();
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.packaging = packaging;
        this.classifier = classifier;
        this.version = version;
    }
    
    public MavenArtifact(String groupId, String artifactId, String packaging, String version) {
        super();
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.packaging = packaging;
        this.classifier = null;
        this.version = version;
    }

    public static MavenArtifact fromCoordinate(String coordinate) {
        String[] parts = checkNotNull(coordinate, "coordinate").split(":");
        if (parts.length==4)
            return new MavenArtifact(parts[0], parts[1], parts[2], parts[3]);
        if (parts.length==5)
            return new MavenArtifact(parts[0], parts[1], parts[2], parts[3], parts[4]);
        throw new IllegalArgumentException("Invalid maven coordinate '"+coordinate+"'");
    }
    
    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    @Nullable public String getClassifier() {
        return classifier;
    }

    public String getPackaging() {
        return packaging;
    }

    public boolean isSnapshot() {
        return getVersion().toUpperCase().contains("SNAPSHOT");
    }
    
    /** @see #customFileNameAfterArtifactMarker */
    public String getCustomFileNameAfterArtifactMarker() {
        return customFileNameAfterArtifactMarker;
    }
    
    /** @see #customFileNameAfterArtifactMarker */
    public void setCustomFileNameAfterArtifactMarker(String customFileNameMarker) {
        this.customFileNameAfterArtifactMarker = customFileNameMarker;
    }

    /** @ee {@link #classifierFileNameMarker} */
    public String getClassifierFileNameMarker() {
        return classifierFileNameMarker!=null ? classifierFileNameMarker : getClassifier();
    }
    
    /** @ee {@link #classifierFileNameMarker} */
    public void setClassifierFileNameMarker(String classifierFileNameMarker) {
        this.classifierFileNameMarker = classifierFileNameMarker;
    }
    
    /** returns a "groupId:artifactId:version:(classifier:)packaging" string
     * which maven refers to as the co-ordinate */
    public String getCoordinate() {
        return Strings.join(MutableList.<String>of().append(groupId, artifactId, packaging).
            appendIfNotNull(classifier).append(version), ":");
    }

    public String getFilename() {
        return artifactId+"-"+
                (Strings.isNonEmpty(getCustomFileNameAfterArtifactMarker()) ? getCustomFileNameAfterArtifactMarker()+"-" : "")+
                version+
                (Strings.isNonEmpty(getClassifierFileNameMarker()) ? "-"+getClassifierFileNameMarker() : "")+
                (Strings.isNonEmpty(getExtension()) ? "."+getExtension() : "");
    }
    
    /** returns an extension, defaulting to {@link #packaging} if one cannot be inferred */
    @Nullable public String getExtension() {
        if ("jar".equalsIgnoreCase(packaging) || "bundle".equalsIgnoreCase(packaging))
            return "jar";
        if ("war".equalsIgnoreCase(packaging))
            return "war";
        log.debug("Unrecognised packaging for autodetecting extension, defaulting to {} for: {}", packaging, this);
        return packaging;
    }

    @Override
    public String toString() {
        return JavaClassNames.simpleClassName(this)+"["+getCoordinate()+"]";
    }

    @Override
    public int hashCode() {
        // autogenerated code
        
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
        result = prime * result + ((classifier == null) ? 0 : classifier.hashCode());
        result = prime * result + ((classifierFileNameMarker == null) ? 0 : classifierFileNameMarker.hashCode());
        result = prime * result + ((customFileNameAfterArtifactMarker == null) ? 0 : customFileNameAfterArtifactMarker.hashCode());
        result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
        result = prime * result + ((packaging == null) ? 0 : packaging.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        // autogenerated code
        
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MavenArtifact other = (MavenArtifact) obj;
        if (artifactId == null) {
            if (other.artifactId != null)
                return false;
        } else if (!artifactId.equals(other.artifactId))
            return false;
        if (classifier == null) {
            if (other.classifier != null)
                return false;
        } else if (!classifier.equals(other.classifier))
            return false;
        if (classifierFileNameMarker == null) {
            if (other.classifierFileNameMarker != null)
                return false;
        } else if (!classifierFileNameMarker.equals(other.classifierFileNameMarker))
            return false;
        if (customFileNameAfterArtifactMarker == null) {
            if (other.customFileNameAfterArtifactMarker != null)
                return false;
        } else if (!customFileNameAfterArtifactMarker.equals(other.customFileNameAfterArtifactMarker))
            return false;
        if (groupId == null) {
            if (other.groupId != null)
                return false;
        } else if (!groupId.equals(other.groupId))
            return false;
        if (packaging == null) {
            if (other.packaging != null)
                return false;
        } else if (!packaging.equals(other.packaging))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

    
}
