package brooklyn.util.xstream;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.testng.annotations.Test;

import com.thoughtworks.xstream.XStream;

@Test
public class InetAddressConverterTest extends ConverterTestFixture {

    protected void registerConverters(XStream xstream) {
        super.registerConverters(xstream);
        xstream.registerConverter(new Inet4AddressConverter());
    }

    public void testFoo1234() throws UnknownHostException {
        assertX(InetAddress.getByAddress("foo", new byte[] { 1, 2, 3, 4 }), 
                "<java.net.Inet4Address>foo/1.2.3.4</java.net.Inet4Address>");
    }
    
}
