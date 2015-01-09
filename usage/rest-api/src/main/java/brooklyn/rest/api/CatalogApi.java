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
package brooklyn.rest.api;

import java.io.InputStream;
import java.util.List;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/catalog")
@Apidoc("Catalog")
@Produces(MediaType.APPLICATION_JSON)
public interface CatalogApi {

    @POST
    @ApiOperation(value = "Add a catalog item (e.g. new entity or policy type) by uploading YAML descriptor from browser using multipart/form-data",
        responseClass = "String")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response createFromMultipart(
        @ApiParam(name = "yaml", value = "multipart/form-data file input field")
        @FormDataParam("yaml") InputStream uploadedInputStream,
        @FormDataParam("yaml") FormDataContentDisposition fileDetail);
    
    @POST
    @ApiOperation(value = "Add a catalog item (e.g. new entity or policy type) by uploading YAML descriptor", responseClass = "String")
    public Response create(
            @ApiParam(name = "yaml", value = "YAML descriptor of catalog item", required = true)
            @Valid String yaml);

    @POST
    @Consumes(MediaType.APPLICATION_XML)
    @Path("/reset")
    @ApiOperation(value = "Resets the catalog to the given (XML) format")
    public Response resetXml(
            @ApiParam(name = "xml", value = "XML descriptor of the entire catalog to install", required = true)
            @Valid String xml);

    /** @deprecated since 0.7.0 use {@link #getEntity(String, String)} */
    @Deprecated
    @DELETE
    @Path("/entities/{entityId}")
    @ApiOperation(value = "Deletes a specific version of an entity's definition from the catalog")
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public void deleteEntity(
        @ApiParam(name = "entityId", value = "The ID of the entity or template to delete", required = true)
        @PathParam("entityId") String entityId) throws Exception;

    @DELETE
    @Path("/entities/{entityId}/{version}")
    @ApiOperation(value = "Deletes a specific version of an entity's definition from the catalog")
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public void deleteEntity(
        @ApiParam(name = "entityId", value = "The ID of the entity or template to delete", required = true)
        @PathParam("entityId") String entityId,

        @ApiParam(name = "version", value = "The version identifier of the entity or template to delete", required = true)
        @PathParam("version") String version) throws Exception;

    @GET
    @Path("/entities")
    @ApiOperation(value = "List available entity types optionally matching a query", responseClass = "CatalogItemSummary", multiValueResponse = true)
    public List<CatalogItemSummary> listEntities(
        @ApiParam(name = "regex", value = "Regular expression to search for")
        @QueryParam("regex") @DefaultValue("") String regex,
        @ApiParam(name = "fragment", value = "Substring case-insensitive to search for")
        @QueryParam("fragment") @DefaultValue("") String fragment);

    @GET
    @Path("/applications")
    @ApiOperation(value = "Fetch a list of application templates optionally matching a query", responseClass = "CatalogItemSummary", multiValueResponse = true)
    public List<CatalogItemSummary> listApplications(
            @ApiParam(name = "regex", value = "Regular expression to search for")
            @QueryParam("regex") @DefaultValue("") String regex,
            @ApiParam(name = "fragment", value = "Substring case-insensitive to search for")
            @QueryParam("fragment") @DefaultValue("") String fragment);

    /** @deprecated since 0.7.0 use {@link #getEntity(String, String)} */
    @Deprecated
    @GET
    @Path("/entities/{entityId}")
    @ApiOperation(value = "Fetch an entity's definition from the catalog", responseClass = "CatalogEntitySummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogEntitySummary getEntity(
        @ApiParam(name = "entityId", value = "The ID of the entity or template to retrieve", required = true)
        @PathParam("entityId") String entityId) throws Exception;

    @GET
    @Path("/entities/{entityId}/{version}")
    @ApiOperation(value = "Fetch a specific version of an entity's definition from the catalog", responseClass = "CatalogEntitySummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogEntitySummary getEntity(
        @ApiParam(name = "entityId", value = "The ID of the entity or template to retrieve", required = true)
        @PathParam("entityId") String entityId,
        
        @ApiParam(name = "version", value = "The version identifier of the entity or template to retrieve", required = true)
        @PathParam("version") String version) throws Exception;

    /** @deprecated since 0.7.0 use {@link #getEntity(String, String)} */
    @Deprecated
    @GET
    @Path("/applications/{applicationId}")
    @ApiOperation(value = "Fetch a specific version of an application's definition from the catalog", responseClass = "CatalogEntitySummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogEntitySummary getApplication(
        @ApiParam(name = "applicationId", value = "The ID of the application to retrieve", required = true)
        @PathParam("applicationId") String entityId) throws Exception;

    @GET
    @Path("/applications/{applicationId}/{version}")
    @ApiOperation(value = "Fetch a specific version of an application's definition from the catalog", responseClass = "CatalogEntitySummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogEntitySummary getApplication(
        @ApiParam(name = "applicationId", value = "The ID of the application to retrieve", required = true)
        @PathParam("applicationId") String entityId,
        
        @ApiParam(name = "version", value = "The version identifier of the application to retrieve", required = true)
        @PathParam("version") String version) throws Exception;

    @GET
    @Path("/policies")
    @ApiOperation(value = "List available policies optionally matching a query", responseClass = "CatalogItemSummary", multiValueResponse = true)
    public List<CatalogItemSummary> listPolicies(
            @ApiParam(name = "regex", value = "Regular expression to search for")
            @QueryParam("regex") @DefaultValue("") String regex,
            @ApiParam(name = "fragment", value = "Substring case-insensitive to search for")
            @QueryParam("fragment") @DefaultValue("") String fragment);
    
    /** @deprecated since 0.7.0 use {@link #getEntity(String, String)} */
    @Deprecated
    @GET
    @Path("/policies/{policyId}")
    @ApiOperation(value = "Fetch a policy's definition from the catalog", responseClass = "CatalogItemSummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogItemSummary getPolicy(
        @ApiParam(name = "policyId", value = "The ID of the policy to retrieve", required = true)
        @PathParam("policyId") String policyId) throws Exception;
    
    @GET
    @Path("/policies/{policyId}/{version}")
    @ApiOperation(value = "Fetch a policy's definition from the catalog", responseClass = "CatalogItemSummary", multiValueResponse = true)
    @ApiErrors(value = {
        @ApiError(code = 404, reason = "Entity not found")
    })
    public CatalogItemSummary getPolicy(
        @ApiParam(name = "policyId", value = "The ID of the policy to retrieve", required = true)
        @PathParam("policyId") String policyId,
        @ApiParam(name = "version", value = "The version identifier of the application to retrieve", required = true)
        @PathParam("version") String version) throws Exception;
    
    /** @deprecated since 0.7.0 use {@link #getIcon(String, String)} */
    @Deprecated
    @GET
    @Path("/icon/{itemId}")
    @ApiOperation(value = "Return the icon for a given catalog entry (application/image or HTTP redirect)")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Item not found")
        })
    @Produces("application/image")
    public Response getIcon(
        @ApiParam(name = "itemId", value = "ID of catalog item (application, entity, policy)")
        @PathParam("itemId") @DefaultValue("") String itemId);

    @GET
    @Path("/icon/{itemId}/{version}")
    @ApiOperation(value = "Return the icon for a given catalog entry (application/image or HTTP redirect)")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Item not found")
        })
    @Produces("application/image")
    public Response getIcon(
        @ApiParam(name = "itemId", value = "ID of catalog item (application, entity, policy)", required=true)
        @PathParam("itemId") String itemId,

        @ApiParam(name = "version", value = "version identifier of catalog item (application, entity, policy)", required=true)
        @PathParam("version") String version);

}

