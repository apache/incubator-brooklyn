package brooklyn.util.net;

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
    
}
