package brooklyn.util.git;

import brooklyn.util.net.Urls;

public class GithubUrls {

    public static String BASE_URL = "https://github.com/";
    
    /** returns URL for the root of the given repo */
    public static String root(String owner, String repo) {
        return Urls.mergePaths(BASE_URL, owner, repo);
    }
    
    /** returns URL for downloading a .tar.gz version of a tag of a repository */
    public static String tgz(String owner, String repo, String tag) {
        return Urls.mergePaths(root(owner, repo), "archive", tag+".tar.gz");
    }

    /** returns URL for downloading a .zip version of a tag of a repository */
    public static String zip(String owner, String repo, String tag) {
        return Urls.mergePaths(root(owner, repo), "archive", tag+".zip");
    }

}
