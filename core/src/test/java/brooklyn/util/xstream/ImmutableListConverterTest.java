package brooklyn.util.xstream;

import java.net.UnknownHostException;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.thoughtworks.xstream.XStream;

@Test
public class ImmutableListConverterTest extends ConverterTestFixture {

    protected void registerConverters(XStream xstream) {
        super.registerConverters(xstream);
        xstream.aliasType("ImmutableList", ImmutableList.class);
        xstream.registerConverter(new ImmutableListConverter(xstream.getMapper()));
    }

    @Test
    public void testImmutableEmptyList() throws UnknownHostException {
        assertX(ImmutableList.of(), "<ImmutableList/>");
    }

    @Test
    public void testImmutableSingletonDoubleList() throws UnknownHostException {
        assertX(ImmutableList.of(1.2d), "<ImmutableList>\n  <double>1.2</double>\n</ImmutableList>");
    }

    @Test
    public void testImmutableTwoValStringList() throws UnknownHostException {
        assertX(ImmutableList.of("a","b"), "<ImmutableList>\n  <string>a</string>\n  <string>b</string>\n</ImmutableList>");
    }

    @Test
    public void testImmutableEmptyListStaysImmutable() throws UnknownHostException {
        Object x = assertX(ImmutableList.of(), "<ImmutableList/>");
        Assert.assertTrue(x instanceof ImmutableList);
    }

}
