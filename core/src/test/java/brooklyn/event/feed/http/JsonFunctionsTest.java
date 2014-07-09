package brooklyn.event.feed.http;

import java.util.NoSuchElementException;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.Jsonya.Navigator;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Functionals;
import brooklyn.util.guava.Maybe;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class JsonFunctionsTest {

    public static JsonElement europeMap() {
        Navigator<MutableMap<Object, Object>> europe = Jsonya.newInstance().at("europe", "uk", "edinburgh")
                .put("population", 500*1000)
                .put("weather", "wet", "lighting", "dark")
                .root().at("europe").at("france").put("population", 80*1000*1000)
                .root();
        return new JsonParser().parse( europe.toString() );
    }

    @Test
    public void testWalk1() {
        JsonElement pop = JsonFunctions.walk("europe", "france", "population").apply(europeMap());
        Assert.assertEquals( (int)JsonFunctions.cast(Integer.class).apply(pop), 80*1000*1000 );
    }

    @Test
    public void testWalk2() {
        String weather = Functionals.chain(
            JsonFunctions.walk("europe.uk.edinburgh.weather"),
            JsonFunctions.cast(String.class) ).apply(europeMap());
        Assert.assertEquals(weather, "wet");
    }

    @Test(expectedExceptions=NoSuchElementException.class)
    public void testWalkWrong() {
        Functionals.chain(
            JsonFunctions.walk("europe", "spain", "barcelona"),
            JsonFunctions.cast(String.class) ).apply(europeMap());
    }


    @Test
    public void testWalkM() {
        Maybe<JsonElement> pop = JsonFunctions.walkM("europe", "france", "population").apply( Maybe.of(europeMap()) );
        Assert.assertEquals( (int)JsonFunctions.castM(Integer.class).apply(pop), 80*1000*1000 );
    }

    @Test
    public void testWalkMWrong1() {
        Maybe<JsonElement> m = JsonFunctions.walkM("europe", "spain", "barcelona").apply( Maybe.of( europeMap()) );
        Assert.assertTrue(m.isAbsent());
    }

    @Test(expectedExceptions=Exception.class)
    public void testWalkMWrong2() {
        Maybe<JsonElement> m = JsonFunctions.walkM("europe", "spain", "barcelona").apply( Maybe.of( europeMap()) );
        JsonFunctions.castM(String.class).apply(m);
    }

    
    @Test
    public void testWalkN() {
        JsonElement pop = JsonFunctions.walkN("europe", "france", "population").apply( europeMap() );
        Assert.assertEquals( (int)JsonFunctions.cast(Integer.class).apply(pop), 80*1000*1000 );
    }

    @Test
    public void testWalkNWrong1() {
        JsonElement m = JsonFunctions.walkN("europe", "spain", "barcelona").apply( europeMap() );
        Assert.assertNull(m);
    }

    public void testWalkNWrong2() {
        JsonElement m = JsonFunctions.walkN("europe", "spain", "barcelona").apply( europeMap() );
        String n = JsonFunctions.cast(String.class).apply(m);
        Assert.assertNull(n);
    }


}
