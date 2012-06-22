package brooklyn.rest.resources;


import brooklyn.rest.views.SwaggerUiView;
import com.sun.jersey.api.core.ResourceConfig;
import com.wordnik.swagger.core.Api;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Set;

@Path(SwaggerUiResource.RESOURCE_PATH)
@Produces(MediaType.TEXT_HTML)
public class SwaggerUiResource extends BaseResource {

  public static final String RESOURCE_PATH = "/v1/api/docs";
  private Configuration configuration = new Configuration();

  @GET
  public SwaggerUiView showRestDocumentation(@Context ResourceConfig config) throws IOException, TemplateException {
    Set<Class<?>> classList = config.getRootResourceClasses();
    for (Object singleton : config.getRootResourceSingletons()) {
      if (singleton.getClass().isAnnotationPresent(Api.class)) {
        classList.add(singleton.getClass());
      }
    }
    return new SwaggerUiView(classList);
  }

}
