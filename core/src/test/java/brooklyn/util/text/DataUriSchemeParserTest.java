package brooklyn.util.text;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.bouncycastle.util.encoders.Base64;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DataUriSchemeParserTest {

    @Test
    public void testSimple() {
        Assert.assertEquals(new DataUriSchemeParser("data:,hello").parse().getDataAsString(), "hello");
        Assert.assertEquals(DataUriSchemeParser.toString("data:,hello"), "hello");
    }

    @Test
    public void testMimeType() throws UnsupportedEncodingException {
        DataUriSchemeParser p = new DataUriSchemeParser("data:application/json,"+URLEncoder.encode("{ }", "US-ASCII")).parse();
        Assert.assertEquals(p.getMimeType(), "application/json");
        Assert.assertEquals(p.getData(), "{ }".getBytes());
    }

    @Test
    public void testBase64() {
        Assert.assertEquals(DataUriSchemeParser.toString(
                "data:;base64,"+new String(Base64.encode("hello".getBytes()))), 
            "hello");
    }

    // TODO test pictures, etc
    
}
