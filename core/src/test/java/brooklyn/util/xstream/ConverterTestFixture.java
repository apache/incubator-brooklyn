package brooklyn.util.xstream;

import org.testng.Assert;

import com.thoughtworks.xstream.XStream;

public class ConverterTestFixture {

    protected Object assertX(Object obj, String fmt) {
        XStream xstream = new XStream();
        registerConverters(xstream);
        String s1 = xstream.toXML(obj);
        Assert.assertEquals(s1, fmt);
        Object out = xstream.fromXML(s1);
        Assert.assertEquals(out, obj);
        return out;
    }

    protected void registerConverters(XStream xstream) {
    }

}
