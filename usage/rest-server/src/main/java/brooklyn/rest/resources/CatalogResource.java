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

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.catalog.internal.CatalogDto;
import brooklyn.catalog.internal.CatalogItemComparator;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.rest.api.CatalogApi;
import brooklyn.rest.domain.ApiError;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;
import brooklyn.rest.transform.CatalogTransformer;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.StringPredicates;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.sun.jersey.core.header.FormDataContentDisposition;

public class CatalogResource extends AbstractBrooklynRestResource implements CatalogApi {

    private static final Logger log = LoggerFactory.getLogger(CatalogResource.class);
    
    @SuppressWarnings("rawtypes")
    private final Function<CatalogItem, CatalogItemSummary> TO_CATALOG_ITEM_SUMMARY = new Function<CatalogItem, CatalogItemSummary>() {
        @Override
        public CatalogItemSummary apply(@Nullable CatalogItem input) {
            return CatalogTransformer.catalogItemSummary(brooklyn(), input);
        }
    };

    @Override
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response createFromMultipart(InputStream uploadedInputStream, FormDataContentDisposition fileDetail) {
      return create(Streams.readFullyString(uploadedInputStream));
    }

    static Set<String> missingIcons = MutableSet.of();
    
    @SuppressWarnings("unchecked")
    @Override
    public Response create(String yaml) {
        CatalogItem<?,?> item;
        try {
            item = brooklyn().getCatalog().addItem(yaml);
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ApiError.of(e))
                    .build();
        }
        String itemId = item.getId();
        log.info("REST created catalog item: "+item);

        // FIXME configurations/ not supported
        switch (item.getCatalogItemType()) {
        case TEMPLATE:
            return Response.created(URI.create("applications/" + itemId + "/" + item.getVersion()))
                    .entity(CatalogTransformer.catalogEntitySummary(brooklyn(), (CatalogItem<? extends Entity, EntitySpec<?>>) item))
                    .build();
        case ENTITY:
            return Response.created(URI.create("entities/" + itemId + "/" + item.getVersion()))
                    .entity(CatalogTransformer.catalogEntitySummary(brooklyn(), (CatalogItem<? extends Entity, EntitySpec<?>>) item))
                    .build();
        case POLICY:
            return Response.created(URI.create("policies/" + itemId))
                    .entity(CatalogTransformer.catalogPolicySummary(brooklyn(), (CatalogItem<? extends Policy, PolicySpec<?>>) item))
                    .build();
        default:
            throw new IllegalStateException("Unsupported catalog item type "+item.getCatalogItemType()+": "+item);
        }
    }

    @Override
    public Response resetXml(String xml) {
        ((BasicBrooklynCatalog)mgmt().getCatalog()).reset(CatalogDto.newDtoFromXmlContents(xml, "REST reset"));
        return Response.ok().build();
    }
    
    @Override
    @Deprecated
    public void deleteEntity(String entityId) throws Exception {
        try {
            CatalogItem<?, ?> item = CatalogUtils.getCatalogItemOptionalVersion(mgmt(), entityId);
            if (item==null)
                throw WebResourceUtils.notFound("Entity with id '%s' not found", entityId);
            brooklyn().getCatalog().deleteCatalogItem(item.getSymbolicName(), item.getVersion());
        } catch (NoSuchElementException e) {
            throw WebResourceUtils.notFound("Entity with id '%s' not found", entityId);
        }
    }

    @Override
    public void deleteEntity(String entityId, String version) throws Exception {
      try {
          brooklyn().getCatalog().deleteCatalogItem(entityId, version);
      } catch (NoSuchElementException e) {
          throw WebResourceUtils.notFound("Entity with id '%s:%s' not found", entityId, version);
      }
    }

    @Override
    public List<CatalogItemSummary> listEntities(String regex, String fragment) {
        return getCatalogItemSummariesMatchingRegexFragment(CatalogPredicates.IS_ENTITY, regex, fragment);
    }

    @Override
    public List<CatalogItemSummary> listApplications(String regex, String fragment) {
        return getCatalogItemSummariesMatchingRegexFragment(CatalogPredicates.IS_TEMPLATE, regex, fragment);
    }

    
    @Override
    @Deprecated
    public CatalogEntitySummary getEntity(String entityId) {
        CatalogItem<? extends Entity,EntitySpec<?>> result =
                CatalogUtils.getCatalogItemOptionalVersion(mgmt(), Entity.class, entityId);

        if (result==null) {
            throw WebResourceUtils.notFound("Entity with id '%s' not found", entityId);
        }

        return CatalogTransformer.catalogEntitySummary(brooklyn(), result);
    }
    
    @Override
    public CatalogEntitySummary getEntity(String entityId, String version) {
        //TODO These casts are not pretty, we could just provide separate get methods for the different types?
        //Or we could provide asEntity/asPolicy cast methods on the CataloItem doing a safety check internally
        @SuppressWarnings("unchecked")
        CatalogItem<? extends Entity, EntitySpec<?>> result =
              (CatalogItem<? extends Entity, EntitySpec<?>>) brooklyn().getCatalog().getCatalogItem(entityId, version);

        if (result==null) {
            throw WebResourceUtils.notFound("Entity with id '%s:%s' not found", entityId, version);
        }

        return CatalogTransformer.catalogEntitySummary(brooklyn(), result);
    }

    @Override
    @Deprecated
    public CatalogEntitySummary getApplication(String entityId) throws Exception {
        return getEntity(entityId);
    }

    @Override
    public CatalogEntitySummary getApplication(String applicationId, String version) {
        return getEntity(applicationId, version);
    }

    @Override
    public List<CatalogItemSummary> listPolicies(String regex, String fragment) {
        return getCatalogItemSummariesMatchingRegexFragment(CatalogPredicates.IS_POLICY, regex, fragment);
    }
    
    @Override
    @Deprecated
    public CatalogItemSummary getPolicy(String policyId) {
        CatalogItem<? extends Policy, PolicySpec<?>> result =
            CatalogUtils.getCatalogItemOptionalVersion(mgmt(), Policy.class, policyId);

        if (result==null) {
            throw WebResourceUtils.notFound("Policy with id '%s' not found", policyId);
        }

        return CatalogTransformer.catalogPolicySummary(brooklyn(), result);
    }

    @Override
    public CatalogItemSummary getPolicy(String policyId, String version) throws Exception {
        @SuppressWarnings("unchecked")
        CatalogItem<? extends Policy, PolicySpec<?>> result =
                (CatalogItem<? extends Policy, PolicySpec<?>>)brooklyn().getCatalog().getCatalogItem(policyId, version);

        if (result==null) {
          throw WebResourceUtils.notFound("Policy with id '%s:%s' not found", policyId, version);
        }

        return CatalogTransformer.catalogPolicySummary(brooklyn(), result);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T,SpecT> List<CatalogItemSummary> getCatalogItemSummariesMatchingRegexFragment(Predicate<CatalogItem<T,SpecT>> type, String regex, String fragment) {
        List filters = new ArrayList();
        filters.add(type);
        if (Strings.isNonEmpty(regex))
            filters.add(CatalogPredicates.xml(StringPredicates.containsRegex(regex)));
        if (Strings.isNonEmpty(fragment))
            filters.add(CatalogPredicates.xml(StringPredicates.containsLiteralIgnoreCase(fragment)));

        ImmutableList<CatalogItem<Object, Object>> sortedItems =
                FluentIterable.from(brooklyn().getCatalog().getCatalogItems())
                    .filter(Predicates.and(filters))
                    .toSortedList(CatalogItemComparator.INSTANCE);
        return Lists.transform(sortedItems, TO_CATALOG_ITEM_SUMMARY);
    }

    @Override
    @Deprecated
    public Response getIcon(String itemId) {
        CatalogItem<?,?> result = CatalogUtils.getCatalogItemOptionalVersion(mgmt(), itemId);
        return getCatalogItemIcon(result);
    }

    @Override
    public Response getIcon(String itemId, String version) {
        CatalogItem<?,?> result = brooklyn().getCatalog().getCatalogItem(itemId, version);
        return getCatalogItemIcon(result);
    }

    private Response getCatalogItemIcon(CatalogItem<?, ?> result) {
        String url = result.getIconUrl();
        if (url==null) {
            log.debug("No icon available for "+result+"; returning "+Status.NO_CONTENT);
            return Response.status(Status.NO_CONTENT).build();
        }
        
        if (brooklyn().isUrlServerSideAndSafe(url)) {
            // classpath URL's we will serve IF they end with a recognised image format;
            // paths (ie non-protocol) and 
            // NB, for security, file URL's are NOT served
            log.debug("Loading and returning "+url+" as icon for "+result);
            
            MediaType mime = WebResourceUtils.getImageMediaTypeFromExtension(Files.getFileExtension(url));
            try {
                Object content = ResourceUtils.create(CatalogUtils.newClassLoadingContext(mgmt(), result)).getResourceFromUrl(url);
                return Response.ok(content, mime).build();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                synchronized (missingIcons) {
                    if (missingIcons.add(url)) {
                        // note: this can be quite common when running from an IDE, as resources may not be copied;
                        // a mvn build should sort it out (the IDE will then find the resources, until you clean or maybe refresh...)
                        log.warn("Missing icon data for "+result.getId()+", expected at: "+url+" (subsequent messages will log debug only)");
                        log.debug("Trace for missing icon data at "+url+": "+e, e);
                    } else {
                        log.debug("Missing icon data for "+result.getId()+", expected at: "+url+" (already logged WARN and error details)");
                    }
                }
                throw WebResourceUtils.notFound("Icon unavailable for %s", result.getId());
            }
        }
        
        log.debug("Returning redirect to "+url+" as icon for "+result);
        
        // for anything else we do a redirect (e.g. http / https; perhaps ftp)
        return Response.temporaryRedirect(URI.create(url)).build();
    }

}
