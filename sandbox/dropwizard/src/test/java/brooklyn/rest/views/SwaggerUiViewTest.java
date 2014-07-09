package brooklyn.rest.views;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Path;

import org.testng.annotations.Test;

import brooklyn.rest.resources.AccessResource;
import brooklyn.rest.resources.ActivityResource;
import brooklyn.rest.resources.ApplicationResource;
import brooklyn.rest.resources.CatalogResource;
import brooklyn.rest.resources.EffectorResource;
import brooklyn.rest.resources.EntityResource;
import brooklyn.rest.resources.LocationResource;
import brooklyn.rest.resources.PolicyResource;
import brooklyn.rest.resources.SensorResource;
import brooklyn.rest.resources.VersionResource;

import com.google.common.collect.Sets;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.Documentation;
import com.wordnik.swagger.core.DocumentationEndPoint;

public class SwaggerUiViewTest {

  private static final Class<?>[] CLASSES = new Class[]{
      ApplicationResource.class, 
      EntityResource.class, 
      SensorResource.class,
      EffectorResource.class, 
      PolicyResource.class, 
      ActivityResource.class, 
      AccessResource.class, 
      
      CatalogResource.class,
      LocationResource.class, 
      VersionResource.class 
  };

  private final Set<Class<?>> resources = Sets.newHashSet(Arrays.asList(CLASSES));
  private SwaggerUiView uiView = new SwaggerUiView(resources);

  @Test
  public void testExactNumberOfResources() {
    for (Class<?> clazz : CLASSES) {
      assertTrue(clazz.isAnnotationPresent(Api.class));
      assertTrue(clazz.isAnnotationPresent(Path.class));
    }
  }

  @Test
  public void testReadDocumentationShouldContainExpectedValues() {
    Documentation doc = SwaggerUiView.readApiListing(uiView.getResourceList());
    assertEquals(doc.getApiVersion(), SwaggerUiView.API_VERSION);
    assertEquals(doc.getBasePath(), SwaggerUiView.BASE_PATH);
    assertEquals(doc.getSwaggerVersion(), SwaggerUiView.SWAGGER_VERSION);
    assertEquals(doc.getApis().size(), CLASSES.length);
    assertEquals(doc.resourcePath(), SwaggerUiResource.RESOURCE_PATH);
    assertEquals(doc.getModels(), null);
  }

  @Test
  public void testDocumentationContainsEndPointsContainsString() {
    Documentation doc = SwaggerUiView.readApiListing(uiView.getResourceList());
    List<DocumentationEndPoint> apis = doc.getApis();
    for (DocumentationEndPoint api : apis) {
      assertTrue(api.getPath().contains("/v1/"));
    }
  }

  @Test
  public void testReadApisHasSensitiveValues() {
    List<Documentation> apis = SwaggerUiView.readApis(uiView.getResourceList());
    assertEquals(apis.size(), CLASSES.length);
    System.out.println(apis);
  }

  @Test
  public void testBuildPathWorksOnlyOnJaxRsPath() {
    assertEquals(SwaggerUiView.buildPath(SwaggerUiResource.class), SwaggerUiResource.RESOURCE_PATH);
    boolean exceptionThrown = false;
    try {
      assertEquals(SwaggerUiView.buildPath(SwaggerUiView.class), "should fail");
    } catch (IllegalStateException e) {
      exceptionThrown = true;
    }
    assertTrue(exceptionThrown);
  }
}
