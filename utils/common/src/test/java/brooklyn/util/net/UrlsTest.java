package brooklyn.util.net;

import static org.testng.Assert.assertEquals;

import org.testng.Assert;
import org.testng.annotations.Test;

public class UrlsTest {

    @Test
    public void testUrlToUriToStringAndBack() {
        String u = "http://localhost:8080/sample";
        Assert.assertEquals(Urls.toUrl(u).toString(), u);
        Assert.assertEquals(Urls.toUri(u).toString(), u);
        Assert.assertEquals(Urls.toUri(Urls.toUrl(u)).toString(), u);
        Assert.assertEquals(Urls.toUrl(Urls.toUri(u)).toString(), u);        
    }
    
    @Test
    public void testMergePaths() throws Exception {
        assertEquals(Urls.mergePaths("a","b"), "a/b");
        assertEquals(Urls.mergePaths("/a//","/b/"), "/a/b/");
        assertEquals(Urls.mergePaths("foo://","/b/"), "foo:///b/");
        assertEquals(Urls.mergePaths("/","a","b","/"), "/a/b/");
    }

}
