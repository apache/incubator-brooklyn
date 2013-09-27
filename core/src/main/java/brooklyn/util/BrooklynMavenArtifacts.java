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
