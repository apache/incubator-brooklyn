package brooklyn.util.xstream;

import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.testng.annotations.Test;
import org.testng.collections.Maps;

import brooklyn.util.collections.MutableMap;

import com.thoughtworks.xstream.XStream;

@SuppressWarnings({ "rawtypes", "unchecked" })
@Test
public class StringKeyMapConverterTest extends ConverterTestFixture {

    protected void registerConverters(XStream xstream) {
        super.registerConverters(xstream);
        xstream.alias("map", Map.class, LinkedHashMap.class);
        xstream.alias("MutableMap", MutableMap.class);
        xstream.registerConverter(new StringKeyMapConverter(xstream.getMapper()), /* priority */ 10);
    }

    @Test
    public void testSimple() throws UnknownHostException {
        Map m = Maps.newLinkedHashMap();
        m.put("a", "v");
        assertX(m, "<map>\n  <a>v</a>\n</map>");
    }
    
    @Test
    public void testDouble() throws UnknownHostException {
        Map m = Maps.newLinkedHashMap();
        m.put("a", "v");
        m.put("x", 1.0d);
        assertX(m, "<map>\n  <a>v</a>\n  <x type=\"double\">1.0</x>\n</map>");
    }
    
    @Test
    public void testEmpty() throws UnknownHostException {
        Map m = Maps.newLinkedHashMap();
        assertX(m, "<map/>");
    }
    
    @Test
    public void testBigSpacedKeyInMutableMap() throws UnknownHostException {
        Map m = MutableMap.of("a b", "x");
        assertX(m, "<MutableMap>\n  <entry key=\"a b\">x</entry>\n</MutableMap>");
    }

    @Test
    public void testWithNumericKey() throws UnknownHostException {
        Map m = Maps.newLinkedHashMap();
        m.put("123", "v");
        m.put("a", "v2");
        assertX(m, "<map>\n  <entry key=\"123\">v</entry>\n  <a>v2</a>\n</map>");
    }
}
