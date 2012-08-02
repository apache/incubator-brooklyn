package brooklyn.rest.resources;

import static com.google.common.base.Preconditions.checkNotNull;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiOperation;
import java.io.IOException;
import static java.lang.String.format;
import java.util.Properties;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/v1/version")
@Api(value = "/v1/version", description = "Get brooklyn version")
@Produces(MediaType.APPLICATION_JSON)
public class VersionResource extends BaseResource {

  private static final String VERSION_RESOURCE_FILE = "META-INF/maven/io.brooklyn/brooklyn-rest-SANDBOX/pom.properties";

  private static final String VERSION_PROPERTY_NAME = "version";

  private final String version;

  public VersionResource() {
    this.version = readVersionPropertyFromClasspath(VersionResource.class.getClassLoader());
  }

  private String readVersionPropertyFromClasspath(ClassLoader resourceLoader) {
    Properties versionProperties = new Properties();
    try {
      versionProperties.load(checkNotNull(resourceLoader.getResourceAsStream(VERSION_RESOURCE_FILE), VERSION_RESOURCE_FILE));
    } catch (IOException exception) {
      throw new IllegalStateException(format("Unable to load version resource file '%s'", VERSION_RESOURCE_FILE), exception);
    }
    return checkNotNull(versionProperties.getProperty(VERSION_PROPERTY_NAME), VERSION_PROPERTY_NAME);
  }

  @GET
  @Path("/")
  @ApiOperation(value = "Get brooklyn version", responseClass = "String", multiValueResponse = false)
  public String getVersion() {
    return version;
  }

}
