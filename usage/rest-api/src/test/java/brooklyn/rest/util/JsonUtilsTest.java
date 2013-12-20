package brooklyn.rest.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;

@Test
public class JsonUtilsTest {

    public void testMap() throws JsonParseException, JsonMappingException, JsonGenerationException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        // keys must be strings
        MutableMap<Object, Object> map = MutableMap.<Object,Object>of("a", 1, "2", Arrays.<Object>asList(true, 8, "8"));
        Assert.assertTrue(JsonUtils.isJsonable(map, mapper));
        
        Object map2 = JsonUtils.toJsonable(map, mapper);
        Assert.assertEquals(map2, map);
        Object map2prime = mapper.readValue(new StringReader(mapper.writeValueAsString(map)), MutableMap.class);
        Assert.assertEquals(map, map2prime);
        
        Object map3 = JsonUtils.toJsonable(map, null);
        Assert.assertEquals(map3, map);
        
        map.put("c", 1L);
        Object map4 = JsonUtils.toJsonable(map, null);
        Assert.assertEquals(map, map4);
        Object map4prime = mapper.readValue(new StringReader(mapper.writeValueAsString(map)), MutableMap.class);
        // long messes it up
        Assert.assertNotEquals(map, map4prime);
    }

    public void testUnknownNonSerializableIsToStringed() {
        Object input = new Object(); 
        Assert.assertFalse(JsonUtils.isJsonable(input, null));
        Object output = JsonUtils.toJsonable(input);
        Assert.assertTrue(output instanceof String, "got output "+output.getClass()+": "+output);
        Assert.assertTrue(output.equals(input.toString()));
    }

    // TODO would be nice to have such a test, but can't make normal collections recursive!
//    public void testRecursiveNotSerialized() {
//        MutableList<Object> list = MutableList.<Object>of();
//        list.add(list);
//        Assert.assertFalse(JsonUtils.isJsonable(list, null));
//        Object output = JsonUtils.toJsonable(list);
//        System.err.println("RECURSIVE got "+output);
////        Assert.assertTrue(output instanceof String, "got output "+output.getClass()+": "+output);
////        Assert.assertTrue(output.equals(input.toString()));
//    }

}
