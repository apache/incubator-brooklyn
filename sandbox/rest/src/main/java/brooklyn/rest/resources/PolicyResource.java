package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.transform;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.basic.Policies;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.PolicySummary;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications/{application}/entities/{entity}/policies")
@Apidoc("Entity policies")
@Produces("application/json")
public class PolicyResource extends AbstractBrooklynRestResource {

    private static final Logger log = LoggerFactory.getLogger(PolicyResource.class);
    
  @GET
  @ApiOperation(value = "Fetch the policies attached to a specific application entity",
      responseClass = "brooklyn.rest.domain.PolicySummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public List<PolicySummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") final String entityToken
  ) {
    final Entity entity = brooklyn().getEntity(application, entityToken); 
    return Lists.newArrayList(transform(entity.getPolicies(),
        new Function<Policy, PolicySummary>() {
          @Override
          public PolicySummary apply(Policy policy) {
            return PolicySummary.fromEntity(entity, policy);
          }
        }));
  }

  // TODO support parameters  ?show=value,summary&name=xxx
  // (and in sensors class)
  @GET
  @Path("/current-state")
  @ApiOperation(value = "Fetch policy states in batch", notes="Returns a map of policy ID to whether it is active")
  public Map<String, Boolean> batchConfigRead(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken) {
    // TODO: add test
    EntityLocal entity = brooklyn().getEntity(application, entityToken);
    Map<String, Boolean> result = Maps.newLinkedHashMap();
    for (Policy p: entity.getPolicies()) {
        result.put(p.getId(), !p.isSuspended());
    }
    return result;
  }
  
  @POST
  @ApiOperation(value = "Add a policy", notes="Returns ID of policy added; policy type must have no-arg constructor " +
  		"and setConfig(Map) method should be available if non-empty config is supplied")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or policy")
  })
  public String addPolicy(
      @ApiParam(name = "application", value = "Application ID or name", required = true)
      @PathParam("application") String application,
      
      @ApiParam(name = "entity", value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      
      @ApiParam(name = "policyType", value = "Class of policy to add", required = true)
      @QueryParam("type")
      String policyTypeName,
      
      // TODO would like to make this optional but jersey complains if we do
      @ApiParam(name = "config", value = "Configuration for the policy (as key value pairs)", required = true)
      Map<String, String> config
  ) {
      EntityLocal entity = brooklyn().getEntity(application, entityToken);
      
    try {
        Class<?> policyType = Class.forName(policyTypeName);
        Policy policy = (Policy)policyType.newInstance();
        if (config!=null && !config.isEmpty()) {
            // TODO support this:
            //policy.setConfig(config);
            policyType.getMethod("setConfig", Map.class).invoke(policy, config);
        }
        log.debug("REST API adding policy "+policy+" to "+entity);
        entity.addPolicy((AbstractPolicy) policy);
        return policy.getId();
    } catch (Exception e) {
        throw Exceptions.propagate(e);
    }
  }
  
  @GET
  @Path("/{policy}")
  @ApiOperation(value = "Gets status of a policy (RUNNING / SUSPENDED)")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or policy")
  })
  public Lifecycle getStatus(
      @ApiParam(name = "application", value = "Application ID or name", required = true)
      @PathParam("application") String application,
      
      @ApiParam(name = "entity", value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      
      @ApiParam(name = "policy", value = "Policy ID or name", required = true)
      @PathParam("policy") String policyId
  ) {
      EntityLocal entity = brooklyn().getEntity(application, entityToken);
      return Policies.getPolicyStatus(findPolicy(policyId, entity));
  }

  @POST
  @Path("/{policy}/start")
  @ApiOperation(value = "Start or resume a policy")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or policy")
  })
  public Response start(
          @ApiParam(name = "application", value = "Application ID or name", required = true)
          @PathParam("application") String application,
          
          @ApiParam(name = "entity", value = "Entity ID or name", required = true)
          @PathParam("entity") String entityToken,
          
          @ApiParam(name = "policy", value = "Policy ID or name", required = true)
          @PathParam("policy") String policyId
  ) {
      EntityLocal entity = brooklyn().getEntity(application, entityToken);
    final Policy policy = findPolicy(policyId, entity);

    policy.resume();
    return Response.status(Response.Status.OK).build();
  }

  @POST
  @Path("/{policy}/stop")
  @ApiOperation(value = "Suspends a policy")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or policy")
  })
  public Response stop(
          @ApiParam(name = "application", value = "Application ID or name", required = true)
          @PathParam("application") String application,
          
          @ApiParam(name = "entity", value = "Entity ID or name", required = true)
          @PathParam("entity") String entityToken,
          
          @ApiParam(name = "policy", value = "Policy ID or name", required = true)
          @PathParam("policy") String policyId
  ) {
      EntityLocal entity = brooklyn().getEntity(application, entityToken);
    final Policy policy = findPolicy(policyId, entity);

    policy.suspend();
    return Response.status(Response.Status.OK).build();
  }

  @POST
  @Path("/{policy}/destroy")
  @ApiOperation(value = "Destroy a policy", notes="Removes a policy from being associated with the entity and destroys it (stopping first if running)")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or policy")
  })
  public Response destroy(
          @ApiParam(name = "application", value = "Application ID or name", required = true)
          @PathParam("application") String application,
          
          @ApiParam(name = "entity", value = "Entity ID or name", required = true)
          @PathParam("entity") String entityToken,
          
          @ApiParam(name = "policy", value = "Policy ID or name", required = true)
          @PathParam("policy") String policyId
  ) {
      EntityLocal entity = brooklyn().getEntity(application, entityToken);
    final Policy policy = findPolicy(policyId, entity);

    policy.suspend();
    entity.removePolicy((AbstractPolicy) policy);
    return Response.status(Response.Status.OK).build();
  }

  protected Policy findPolicy(String policyId, final EntityLocal entity) {
      for (Policy p: entity.getPolicies()) {
          if (policyId.equals(p.getId())) return p;
      }
      for (Policy p: entity.getPolicies()) {
          if (policyId.equals(p.getName())) return p;
      }
      throw WebResourceUtils.notFound("No policy "+policyId+" found on entity "+entity);
  }

  
  // TODO policy config -- but this requires changes to policies
  
}
