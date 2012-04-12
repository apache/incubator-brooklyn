package brooklyn.rest.resources;

import brooklyn.entity.Effector;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/applications/{application}/effectors")
public class EffectorResource {

  @GET
  public Set<Effector> listEffectors(@PathParam("application") String appId) {
    return ImmutableSet.of();
  }

}
