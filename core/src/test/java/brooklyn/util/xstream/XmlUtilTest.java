package brooklyn.util.xstream;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;


public class XmlUtilTest {

    @Test
    public void testXpath() throws Exception {
        String xml = "<a><b>myb</b></a>";
        assertEquals(XmlUtil.xpath(xml, "/a/b[text()]"), "myb");
    }
}
