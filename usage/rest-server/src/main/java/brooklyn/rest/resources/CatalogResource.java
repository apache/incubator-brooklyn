package brooklyn.rest.resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.entity.Entity;
import brooklyn.rest.api.CatalogApi;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;
import brooklyn.rest.transform.CatalogTransformer;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.text.StringPredicates;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.sun.jersey.core.header.FormDataContentDisposition;

public class CatalogResource extends AbstractBrooklynRestResource implements CatalogApi {

    @SuppressWarnings("rawtypes")
    private final Function<CatalogItem, CatalogItemSummary> TO_CATALOG_ITEM_SUMMARY = new Function<CatalogItem, CatalogItemSummary>() {
        @Override
        public CatalogItemSummary apply(@Nullable CatalogItem input) {
            return CatalogTransformer.catalogItemSummary(brooklyn(), input);
        }
    };

    @Override
    public Response createFromMultipart(InputStream uploadedInputStream, FormDataContentDisposition fileDetail) throws IOException {
      return brooklyn().createCatalogEntryFromGroovyCode(CharStreams.toString(new InputStreamReader(uploadedInputStream, Charsets.UTF_8)));
    }

    @Override
    public Response create(String groovyCode ) {
        return brooklyn().createCatalogEntryFromGroovyCode(groovyCode);
    }

    @Override
    public List<CatalogItemSummary> listEntities(
        final String regex,
        final String fragment
    ) {
        return getCatalogItemSummariesMatchingRegexFragment(CatalogPredicates.IS_ENTITY, regex, fragment);
    }

    @Override
    public List<CatalogItemSummary> listApplications(
            final String regex,
            final  String fragment
    ) {
        return getCatalogItemSummariesMatchingRegexFragment(CatalogPredicates.IS_TEMPLATE, regex, fragment);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CatalogEntitySummary getEntity(String entityId) throws Exception {
      CatalogItem<?> result = brooklyn().getCatalog().getCatalogItem(entityId);
      if (result==null) {
        throw WebResourceUtils.notFound("Entity with id '%s' not found", entityId);
      }

      return CatalogTransformer.catalogEntitySummary(brooklyn(), (CatalogItem<? extends Entity>) result);
    }

    @Override
    public List<CatalogItemSummary> listPolicies(
            final String regex,
            final String fragment
    ) {
        return getCatalogItemSummariesMatchingRegexFragment(CatalogPredicates.IS_POLICY, regex, fragment);
    }
    
    @Override
    public CatalogItemSummary getPolicy(
        String policyId) throws Exception {
        CatalogItem<?> result = brooklyn().getCatalog().getCatalogItem(policyId);
        if (result==null) {
          throw WebResourceUtils.notFound("Policy with id '%s' not found", policyId);
        }

        return CatalogTransformer.catalogItemSummary(brooklyn(), result);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T> List<CatalogItemSummary> getCatalogItemSummariesMatchingRegexFragment(Predicate<CatalogItem<T>> type, String regex, String fragment) {
        List filters = new ArrayList();
        filters.add(type);
        if (Strings.isNonEmpty(regex))
            filters.add(CatalogPredicates.xml(StringPredicates.containsRegex(regex)));
        if (Strings.isNonEmpty(fragment))
            filters.add(CatalogPredicates.xml(StringPredicates.containsLiteralCaseInsensitive(fragment)));
        return ImmutableList.copyOf(Iterables.transform(
                brooklyn().getCatalog().getCatalogItems(Predicates.and(filters)),
                TO_CATALOG_ITEM_SUMMARY));        
    }

    @Override
    public Response getIcon(String itemId) {
        CatalogItem<?> result = brooklyn().getCatalog().getCatalogItem(itemId);
        String url = result.getIconUrl();
        if (url==null)
            return Response.status(Status.NO_CONTENT).build();
        
        if (brooklyn().isUrlServerSideAndSafe(url)) {
            // classpath URL's we will serve IF they end with a recognised image format;
            // paths (ie non-protocol) and 
            // NB, for security, file URL's are NOT served
            MediaType mime = WebResourceUtils.getImageMediaTypeFromExtension(Files.getFileExtension(url));
            Object content = new ResourceUtils(brooklyn().getCatalog().getRootClassLoader()).getResourceFromUrl(url);
            return Response.ok(content, mime).build();
        }
        
        // for anything else we do a redirect (e.g. http / https; perhaps ftp)
        return Response.temporaryRedirect(URI.create(url)).build();
    }

}
