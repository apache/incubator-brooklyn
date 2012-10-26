package brooklyn.rest.resources;

import groovy.lang.GroovyClassLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.reflections.Reflections;

import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityTypes;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/catalog")
@Api(value = "/v1/catalog", description = "Manage entities and policies available on the server")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogResource extends BaseResource {

  private boolean scanNeeded = true;
  private Map<String, Class<? extends AbstractEntity>> scannedEntities = Collections.emptyMap();
  private final Map<String, Class<? extends AbstractEntity>> registeredEntities = Maps.newConcurrentMap();
  private Map<String, Class<? extends AbstractPolicy>> scannedPolicies = Collections.emptyMap();
  private final Map<String, Class<? extends AbstractPolicy>> registeredPolicies = Maps.newConcurrentMap();

  private synchronized void scanIfNeeded() {
      // defer expensive scans, particularly for unit tests
      if (scanNeeded==false) return;
      scanNeeded = false;
      // TODO allow other prefixes to be supplied?
      scannedEntities = buildMapOfSubTypesOf("brooklyn", AbstractEntity.class);
      scannedPolicies = buildMapOfSubTypesOf("brooklyn", AbstractPolicy.class);
  }
  
  private <T> Map<String, Class<? extends T>> buildMapOfSubTypesOf(String prefix, Class<T> clazz) {
    Reflections reflections = new Reflections(prefix);
    ImmutableMap.Builder<String, Class<? extends T>> builder = ImmutableMap.builder();
    for (Class<? extends T> candidate : reflections.getSubTypesOf(clazz)) {
      if (!Modifier.isAbstract(candidate.getModifiers()) &&
          !candidate.isInterface() &&
          !candidate.isAnonymousClass()) {
        builder.put(candidate.getName(), candidate);
      }
    }
    return builder.build();
  }

  public boolean containsEntity(String entityName) {
    if (registeredEntities.containsKey(entityName)) return true;
    if (scanNeeded) {
        // test early to avoid scan
        if (forName(entityName, false)!=null) return true;
    }
    scanIfNeeded();
    if (scannedEntities.containsKey(entityName)) return true;
    return false;
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<? extends T> forName(String className, boolean required) {
    try {
        return (Class<? extends T>) Class.forName(className);
    } catch (ClassNotFoundException e) {
        if (required) throw Exceptions.propagate(e);
        return null;
    }
  }
  
  public Class<? extends AbstractEntity> getEntityClass(String entityName) {
    Class<? extends AbstractEntity> result = registeredEntities.get(entityName);
    if (result!=null) return result;
    result = forName(entityName, false);
    if (result!=null) return result;
    scanIfNeeded();
    result = scannedEntities.get(entityName);
    if (result!=null) return result;
    throw new NoSuchElementException("No entity class "+entityName);
  }

  public Class<? extends AbstractPolicy> getPolicyClass(String policyName) {
      Class<? extends AbstractPolicy> result = registeredPolicies.get(policyName);
      if (result!=null) return result;
      result = forName(policyName, false);
      if (result!=null) return result;
      scanIfNeeded();
      result = scannedPolicies.get(policyName);
      if (result!=null) return result;
      throw new NoSuchElementException("No policy class "+policyName);
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @POST
  @ApiOperation(value = "Create new entity or policy by uploading a Groovy script", responseClass = "String")
  public Response create(
      @ApiParam(name = "groovyCode", value = "Groovy code for the entity or policy", required = true)
      @Valid String groovyCode) {
    ClassLoader parent = getClass().getClassLoader();
    GroovyClassLoader loader = new GroovyClassLoader(parent);

    Class clazz = loader.parseClass(groovyCode);

    if (AbstractEntity.class.isAssignableFrom(clazz)) {
      registeredEntities.put(clazz.getName(), clazz);
      return Response.created(URI.create("entities/" + clazz.getName())).build();

    } else if (AbstractPolicy.class.isAssignableFrom(clazz)) {
      registeredPolicies.put(clazz.getName(), clazz);
      return Response.created(URI.create("policies/" + clazz.getName())).build();
    }

    return Response.ok().build();
  }

  @POST
  @ApiOperation(value = "Create a new entity by uploading a Groovy script from browser using multipart/form-data",
      responseClass = "String")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response createFromMultipart(
      @ApiParam(name = "groovyCode", value = "multipart/form-data file input field")
      @FormDataParam("groovyCode") InputStream uploadedInputStream,
      @FormDataParam("groovyCode") FormDataContentDisposition fileDetail) throws IOException {

    return create(CharStreams.toString(new InputStreamReader(uploadedInputStream, Charsets.UTF_8)));
  }

  @GET
  @Path("/entities")
  @ApiOperation(value = "Fetch a list of entities matching a query", responseClass = "String", multiValueResponse = true)
  public Iterable<String> listEntities(
      @ApiParam(name = "name", value = "Query to filter entities by")
      final @QueryParam("name") @DefaultValue("") String name
  ) {
    scanIfNeeded();
    List<String> result = new ArrayList<String>();
    result.addAll(registeredEntities.keySet());
    result.addAll(scannedEntities.keySet());
    if (name!=null && !name.isEmpty()) {
      final String normalizedName = name.toLowerCase();
      Iterator<String> ri = result.iterator();
      while (ri.hasNext()) {
          if (!ri.next().toLowerCase().contains(normalizedName)) ri.remove(); 
      }
    }
    return result;
  }

  @GET
  @Path("/entities/{entity}")
  @ApiOperation(value = "Fetch an entity", responseClass = "String", multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Entity not found")
  })
  // TODO should return more than config keys?
  public List<String> getEntity(
      @ApiParam(name = "entity", value = "The name of the entity to retrieve", required = true)
      @PathParam("entity") String entityType) throws Exception {
    if (!containsEntity(entityType)) {
      throw notFound("Entity with type '%s' not found", entityType);
    }

    return Lists.newArrayList(EntityTypes.getDefinedConfigKeys(getEntityClass(entityType)).keySet());
  }

  @GET
  @Path("/policies")
  @ApiOperation(value = "List available policies", responseClass = "String", multiValueResponse = true)
  public Iterable<String> listPolicies() {
      scanIfNeeded();
      List<String> result = new ArrayList<String>();
      result.addAll(registeredPolicies.keySet());
      result.addAll(scannedPolicies.keySet());
      return result;
  }
}
