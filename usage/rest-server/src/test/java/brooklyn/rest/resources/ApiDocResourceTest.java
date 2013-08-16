package brooklyn.rest.resources;

import brooklyn.rest.BrooklynRestApi;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import com.google.common.collect.Iterables;
import com.wordnik.swagger.core.Documentation;
import com.wordnik.swagger.core.DocumentationEndPoint;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Adam Lowe
 */
@Test(singleThreaded = true)
public class ApiDocResourceTest extends BrooklynRestResourceTest {


    @Override
    protected void setUpResources() throws Exception {
        addResources();
        for (Object o : BrooklynRestApi.getApidocResources()) {
            addResource(o);
        }
    }

    @Test
    public void testCountRestResources() throws Exception {
        Documentation response = client().resource("/v1/apidoc/").get(Documentation.class);
        assertEquals(response.getApis().size(), 1 + Iterables.size(BrooklynRestApi.getBrooklynRestResources()));
    }

    @Test
    public void testApiDocDetails() throws Exception {
        Documentation response = client().resource("/v1/apidoc/brooklyn.rest.resources.ApidocResource").get(Documentation.class);
        assertEquals(countOperations(response), 1);
    }

    @Test
    public void testEffectorDetails() throws Exception {
        Documentation response = client().resource("/v1/apidoc/brooklyn.rest.resources.EffectorResource").get(Documentation.class);
        assertEquals(countOperations(response), 2);
    }

    @Test
    public void testEntityDetails() throws Exception {
        Documentation response = client().resource("/v1/apidoc/brooklyn.rest.resources.EntityResource").get(Documentation.class);
        assertEquals(countOperations(response), 5);
    }

    @Test
    public void testCatalogDetails() throws Exception {
        Documentation response = client().resource("/v1/apidoc/brooklyn.rest.resources.CatalogResource").get(Documentation.class);
        assertEquals(countOperations(response), 8);
    }

    /* Note in some cases we might have more than one Resource method per 'endpoint'
     */
    private int countOperations(Documentation doc) throws Exception {
        int result = 0;
        for (DocumentationEndPoint endpoint : doc.getApis()) {
            result += endpoint.getOperations().size();
        }
        return result;
    }
}

