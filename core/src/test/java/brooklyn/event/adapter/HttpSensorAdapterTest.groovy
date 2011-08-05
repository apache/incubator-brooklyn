package brooklyn.event.adapter

import java.net.URL

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity

class HttpSensorAdapterTest {

    @Test
    public void testJsonLongProvider() {
        Entity entity = new AbstractEntity() {}
        HttpSensorAdapter adapter = new HttpSensorAdapter(entity) {
            public byte[] getContents(URL url) {
                return '{"abc":"10"}'.getBytes()
            }
        }
        String url = 
        assertEquals(10, adapter.newJsonLongProvider("http://myurl", "abc").compute())
    }
    
    @Test
    public void testJsonLongProviderForValueGreaterThanIntegerMaxVal() {
        Entity entity = new AbstractEntity() {}
        HttpSensorAdapter adapter = new HttpSensorAdapter(entity) {
            public byte[] getContents(URL url) {
                return '{"abc":"2845050317"}'.getBytes()
            }
        }
        String url = 
        assertEquals(2845050317, adapter.newJsonLongProvider("http://myurl", "abc").compute())
    }
}
