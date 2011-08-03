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
    public void testJsonIntegerProvider() {
        Entity entity = new AbstractEntity() {}
        HttpSensorAdapter adapter = new HttpSensorAdapter(entity) {
            public byte[] getContents(URL url) {
                return '{"abc":"10"}'.getBytes()
            }
        }
        String url = 
        assertEquals(10, adapter.newJsonIntegerProvider("http://myurl", "abc").compute())
    }
}
