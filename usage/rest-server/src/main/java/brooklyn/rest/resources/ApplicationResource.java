/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.rest.resources;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynAssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.management.entitlement.EntitlementPredicates;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.entitlement.Entitlements.EntityAndItem;
import brooklyn.rest.api.ApplicationApi;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.transform.ApplicationTransformer;
import brooklyn.rest.transform.EntityTransformer;
import brooklyn.rest.transform.TaskTransformer;
import brooklyn.rest.util.BrooklynRestResourceUtils;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;


public class ApplicationResource extends AbstractBrooklynRestResource implements ApplicationApi {

    private static final Logger log = LoggerFactory.getLogger(ApplicationResource.class);

    @Context
    private UriInfo uriInfo;

    /** @deprecated since 0.6.0 use {@link #fetch(String)} (with slightly different, but better semantics) */
    @Deprecated
    @Override
    public JsonNode applicationTree() {
        ArrayNode apps = mapper().createArrayNode();
        for (Application application : mgmt().getApplications())
            apps.add(recursiveTreeFromEntity(application));
        return apps;
    }

    private ObjectNode entityBase(Entity entity) {
        ObjectNode aRoot = mapper().createObjectNode();
        aRoot.put("name", entity.getDisplayName());
        aRoot.put("id", entity.getId());
        aRoot.put("type", entity.getEntityType().getName());

        Boolean serviceUp = entity.getAttribute(Attributes.SERVICE_UP);
        if (serviceUp!=null) aRoot.put("serviceUp", serviceUp);

        Lifecycle serviceState = entity.getAttribute(Attributes.SERVICE_STATE_ACTUAL);
        if (serviceState!=null) aRoot.put("serviceState", serviceState.toString());

        String iconUrl = entity.getIconUrl();
        if (iconUrl!=null) {
            if (brooklyn().isUrlServerSideAndSafe(iconUrl))
                // route to server if it is a server-side url
                iconUrl = EntityTransformer.entityUri(entity)+"/icon";
            aRoot.put("iconUrl", iconUrl);
        }

        return aRoot;
    }

    private JsonNode recursiveTreeFromEntity(Entity entity) {
        ObjectNode aRoot = entityBase(entity);

        if (!entity.getChildren().isEmpty())
            aRoot.put("children", childEntitiesRecursiveAsArray(entity));

        return aRoot;
    }

    // TODO when applicationTree can be removed, replace this with an extension to EntitySummary (without links)
    private JsonNode fromEntity(Entity entity) {
        ObjectNode aRoot = entityBase(entity);

        aRoot.put("applicationId", entity.getApplicationId());

        if (entity.getParent()!=null) {
            aRoot.put("parentId", entity.getParent().getId());
        }

        if (!entity.getGroups().isEmpty())
            aRoot.put("groupIds", entitiesIdAsArray(entity.getGroups()));

        if (!entity.getChildren().isEmpty())
            aRoot.put("children", entitiesIdAndNameAsArray(entity.getChildren()));

        if ((entity instanceof Group) && !((Group) entity).getMembers().isEmpty())
            aRoot.put("members", entitiesIdAndNameAsArray(((Group) entity).getMembers()));

        return aRoot;
    }

    private ArrayNode childEntitiesRecursiveAsArray(Entity entity) {
        ArrayNode node = mapper().createArrayNode();
        for (Entity e : entity.getChildren()) {
            if (Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY, entity)) {
                node.add(recursiveTreeFromEntity(e));
            }
        }
        return node;
    }

    private ArrayNode entitiesIdAndNameAsArray(Collection<? extends Entity> entities) {
        ArrayNode node = mapper().createArrayNode();
        for (Entity entity : entities) {
            if (Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY, entity)) {
                ObjectNode holder = mapper().createObjectNode();
                holder.put("id", entity.getId());
                holder.put("name", entity.getDisplayName());
                node.add(holder);
            }
        }
        return node;
    }

    private ArrayNode entitiesIdAsArray(Collection<? extends Entity> entities) {
        ArrayNode node = mapper().createArrayNode();
        for (Entity entity : entities) {
            if (Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY, entity)) {
                node.add(entity.getId());
            }
        }
        return node;
    }

    @Override
    public JsonNode fetch(String entityIds) {
        Map<String, JsonNode> jsonEntitiesById = MutableMap.of();
        for (Application application : mgmt().getApplications())
            jsonEntitiesById.put(application.getId(), fromEntity(application));
        if (entityIds != null) {
            for (String entityId: entityIds.split(",")) {
                Entity entity = mgmt().getEntityManager().getEntity(entityId.trim());
                while (entity != null && entity.getParent() != null) {
                    if (Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY, entity)) {
                        jsonEntitiesById.put(entity.getId(), fromEntity(entity));
                    }
                    entity = entity.getParent();
                }
            }
        }

        ArrayNode result = mapper().createArrayNode();
        for (JsonNode n: jsonEntitiesById.values()) result.add(n);
        return result;
    }

    @Override
    public List<ApplicationSummary> list() {
        return FluentIterable
                .from(mgmt().getApplications())
                .filter(EntitlementPredicates.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ENTITY))
                .transform(ApplicationTransformer.FROM_APPLICATION)
                .toList();
    }

    @Override
    public ApplicationSummary get(String application) {
        return ApplicationTransformer.summaryFromApplication(brooklyn().getApplication(application));
    }

    public Response create(ApplicationSpec applicationSpec) {
        return createFromAppSpec(applicationSpec);
    }

    /** @deprecated since 0.7.0 see #create */ @Deprecated
    protected Response createFromAppSpec(ApplicationSpec applicationSpec) {
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.DEPLOY_APPLICATION, applicationSpec)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to start application %s",
                Entitlements.getEntitlementContext().user(), applicationSpec);
        }

        checkApplicationTypesAreValid(applicationSpec);
        checkLocationsAreValid(applicationSpec);
        // TODO duplicate prevention
        List<Location> locations = brooklyn().getLocations(applicationSpec);
        Application app = brooklyn().create(applicationSpec);
        Task<?> t = brooklyn().start(app, locations);
        TaskSummary ts = TaskTransformer.FROM_TASK.apply(t);
        URI ref = uriInfo.getBaseUriBuilder()
                .path(ApplicationApi.class)
                .path(ApplicationApi.class, "get")
                .build(app.getApplicationId());
        return created(ref).entity(ts).build();
    }

    @Override
    public Response createFromYaml(String yaml) {
        // First of all, see if it's a URL
        URI uri;
        try {
            uri = new URI(yaml);
        } catch (URISyntaxException e) {
            // It's not a URI then...
            uri = null;
        }
        if (uri != null) {
            log.debug("Create app called with URI; retrieving contents: {}", uri);
            yaml = ResourceUtils.create(mgmt()).getResourceAsString(uri.toString());
        }

        log.debug("Creating app from yaml:\n{}", yaml);
        Reader input = new StringReader(yaml);
        AssemblyTemplate at = camp().pdp().registerDeploymentPlan(input);
        
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.DEPLOY_APPLICATION, at)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to start application %s",
                Entitlements.getEntitlementContext().user(), yaml);
        }

        return launch(at);
    }

    private Response launch(AssemblyTemplate at) {
        try {
            AssemblyTemplateInstantiator instantiator = at.getInstantiator().newInstance();
            Assembly assembly;
            Task<?> task = null;
            if (instantiator instanceof BrooklynAssemblyTemplateInstantiator) {
                Application app = ((BrooklynAssemblyTemplateInstantiator) instantiator).create(at, camp());
                assembly = camp().assemblies().get(app.getApplicationId());

                task = Entities.invokeEffector((EntityLocal)app, app, Startable.START,
                        // locations already set in the entities themselves;
                        // TODO make it so that this arg does not have to be supplied to START !
                        MutableMap.of("locations", MutableList.of()));
            } else {
                assembly = instantiator.instantiate(at, camp());
            }
            Entity app = mgmt().getEntityManager().getEntity(assembly.getId());
            
            if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, EntityAndItem.of(app, Startable.START.getName()))) {
                throw WebResourceUtils.unauthorized("User '%s' is not authorized to start application %s",
                    Entitlements.getEntitlementContext().user(), at.getType());
            }

            log.info("Launched from YAML: " + assembly + " (" + task + ")");

            URI ref = URI.create(app.getApplicationId());
            ResponseBuilder response = created(ref);
            if (task != null)
                response.entity(TaskTransformer.FROM_TASK.apply(task));
            return response.build();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public Response createPoly(byte[] inputToAutodetectType) {
        log.debug("Creating app from autodetecting input");

        boolean looksLikeLegacy = false;
        Exception legacyFormatException = null;
        // attempt legacy format
        try {
            ApplicationSpec appSpec = mapper().readValue(inputToAutodetectType, ApplicationSpec.class);
            return createFromAppSpec(appSpec);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            legacyFormatException = e;
            log.debug("Input is not legacy ApplicationSpec JSON (will try others): "+e, e);
        }

        AssemblyTemplate template = null;
        boolean looksLikeYaml = false;
        try {
            template = camp().pdp().registerDeploymentPlan(new StringReader(new String(inputToAutodetectType)));
            if (!template.getPlatformComponentTemplates().isEmpty() || !template.getApplicationComponentTemplates().isEmpty()) {
                looksLikeYaml = true;
            } else {
                if (template.getCustomAttributes().containsKey("type") || template.getCustomAttributes().containsKey("entities")) {
                    looksLikeLegacy = true;
                }
            }
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.debug("Input is not valid YAML: "+e);
        }

        // TODO not json - try ZIP, etc

        if (template!=null) {
            try {
                return launch(template);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                if (looksLikeYaml)
                    throw Exceptions.propagate(e);
            }
        }

        if ((looksLikeLegacy || !looksLikeYaml) && legacyFormatException!=null)
            // throw the error from legacy creation if it looks like it was the legacy format
            throw Throwables.propagate(legacyFormatException);

        return Response.serverError().entity("Unsupported format; not able to autodetect.").build();
    }

    @Override
    public Response createFromForm(String contents) {
        log.debug("Creating app from form");
        return createPoly(contents.getBytes());
    }

    @Override
    public Response delete(String application) {
        Application app = brooklyn().getApplication(application);
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.INVOKE_EFFECTOR, Entitlements.EntityAndItem.of(app, Entitlements.LifecycleEffectors.DELETE))) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized to delete application %s",
                Entitlements.getEntitlementContext().user(), app);
        }
        Task<?> t = brooklyn().destroy(app);
        TaskSummary ts = TaskTransformer.FROM_TASK.apply(t);
        return status(ACCEPTED).entity(ts).build();
    }

    private void checkApplicationTypesAreValid(ApplicationSpec applicationSpec) {
        String appType = applicationSpec.getType();
        if (appType != null) {
            checkEntityTypeIsValid(appType);

            if (applicationSpec.getEntities() != null) {
                throw WebResourceUtils.preconditionFailed("Application given explicit type '%s' must not define entities", appType);
            }
            return;
        }

        for (EntitySpec entitySpec : applicationSpec.getEntities()) {
            String entityType = entitySpec.getType();
            checkEntityTypeIsValid(checkNotNull(entityType, "entityType"));
        }
    }

    private void checkEntityTypeIsValid(String type) {
        if (brooklyn().getCatalog().getCatalogItem(type) == null) {
            try {
                brooklyn().getCatalog().getRootClassLoader().loadClass(type);
            } catch (ClassNotFoundException e) {
                log.debug("Class not found for type '" + type + "'; reporting 404", e);
                throw WebResourceUtils.notFound("Undefined type '%s'", type);
            }
            log.info("Entity type '{}' not defined in catalog but is on classpath; continuing", type);
        }
    }

    @SuppressWarnings("deprecation")
    private void checkLocationsAreValid(ApplicationSpec applicationSpec) {
        for (String locationId : applicationSpec.getLocations()) {
            locationId = BrooklynRestResourceUtils.fixLocation(locationId);
            if (!brooklyn().getLocationRegistry().canMaybeResolve(locationId) && brooklyn().getLocationRegistry().getDefinedLocationById(locationId)==null) {
                throw WebResourceUtils.notFound("Undefined location '%s'", locationId);
            }
        }
    }

    @Override
    public List<EntitySummary> getDescendants(String application, String typeRegex) {
        return EntityTransformer.entitySummaries(brooklyn().descendantsOfType(application, application, typeRegex));
    }

    @Override
    public Map<String, Object> getDescendantsSensor(String application, String sensor, String typeRegex) {
        Iterable<Entity> descs = brooklyn().descendantsOfType(application, application, typeRegex);
        return getSensorMap(sensor, descs);
    }

    public static Map<String, Object> getSensorMap(String sensor, Iterable<Entity> descs) {
        if (Iterables.isEmpty(descs))
            return Collections.emptyMap();
        Map<String, Object> result = MutableMap.of();
        Iterator<Entity> di = descs.iterator();
        Sensor<?> s = null;
        while (di.hasNext()) {
            Entity potentialSource = di.next();
            s = potentialSource.getEntityType().getSensor(sensor);
            if (s!=null) break;
        }
        if (s==null)
            s = Sensors.newSensor(Object.class, sensor);
        if (!(s instanceof AttributeSensor<?>)) {
            log.warn("Cannot retrieve non-attribute sensor "+s+" for entities; returning empty map");
            return result;
        }
        for (Entity e: descs) {
            Object v = null;
            try {
                v = e.getAttribute((AttributeSensor<?>)s);
            } catch (Exception exc) {
                Exceptions.propagateIfFatal(exc);
                log.warn("Error retrieving sensor "+s+" for "+e+" (ignoring): "+exc);
            }
            if (v!=null)
                result.put(e.getId(), v);
        }
        return result;
    }

}