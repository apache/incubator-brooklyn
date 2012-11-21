package brooklyn.rest.apidoc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.sun.jersey.api.core.ResourceConfig;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.Documentation;
import com.wordnik.swagger.core.DocumentationEndPoint;
import com.wordnik.swagger.jaxrs.ConfigReader;
import com.wordnik.swagger.jaxrs.HelpApi;
import com.wordnik.swagger.jaxrs.JaxrsApiReader;
import com.wordnik.swagger.jaxrs.JaxrsApiSpecParser;

@Produces({"application/json"})
/** like Swagger ApiListing (and based on that) but:
 * supports singletons as well as classes;
 * supports simpler Apidoc annotation (doesn't repeat path, in common case);
 * doesn't support listingPath/Class that swagger does (but describes in under /apidoc/name.of.Class
 * does not support auth filters
 */
abstract public class ApidocResource {

    static ConfigReader configReader;
    static {
        JaxrsApiReader.setFormatString("");
    }
    
    protected boolean isSupportedMediaType(String type) {
        if ("application/json".equals(type)) return true;
        if ("application/xml".equals(type)) return true;
        return false;
    }
    
    protected boolean isIncludedForDocumentation(Class<?> resource) {
        // TODO currently only support @Produces, not Contenty-type header, or Accept header (which original ApiListing does support)
        Produces produces = resource.getAnnotation(Produces.class);
        if (produces==null) return false;
        for (String type: produces.value()) 
            if (isSupportedMediaType(type))
                return true;
        return false;
    }

    
    protected String getLinkFor(String path, Class<?> resource) {
        return getClass().getAnnotation(Path.class).value()+"/"+getLinkWordFor(resource);
    }
    
    protected String getLinkWordFor(Class<?> resource) {
        if (resource.getCanonicalName()!=null) 
            return resource.getCanonicalName();        
        else 
            return Integer.toHexString(resource.hashCode());
    }

    protected Class<?> getResourceOfLink(ResourceConfig rc, String link) {
        for (Class<?> r: getResourceClasses(rc)) {
            if (getLinkWordFor(r).equals(link))
                return r;
        }
        return null;
    }
    
    @GET
    @ApiOperation(value = "Returns list of all available API resource endpoints", responseClass = "DocumentationEndPoint", multiValueResponse = true)
    public Response getAllApis(
            @Context ResourceConfig rc,
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo) {

        String apiVersion = getConfigReader().getApiVersion();
        String swaggerVersion = getConfigReader().getSwaggerVersion();
        String basePath = getConfigReader().getBasePath();

        Set<Class<?>> resources = getResourceClasses(rc);

        Documentation allApiDoc = new Documentation();

        List<ApidocEndpoint> endpoints = new ArrayList<ApidocEndpoint>();
        for (Class<?> resource : resources) {
            if (!isIncludedForDocumentation(resource))
                continue;
            
            Apidoc apidoc = resource.getAnnotation(Apidoc.class);
            Api apidocX = resource.getAnnotation(Api.class);
            Path rsPath = resource.getAnnotation(Path.class);

            if (apidoc==null && apidocX == null) continue;
            String path = rsPath.value();
            String name = null;
            String description;

            if (apidoc!=null) {
                name = apidoc.value();
                description = apidoc.description();
            } else {
                path = apidocX.value();
                description = apidocX.description();
            }

            endpoints.add(new ApidocEndpoint(name, path, description, getLinkFor(path, resource)));
        }
        
        Collections.sort(endpoints, ApidocEndpoint.COMPARATOR);
        
        for (ApidocEndpoint api: endpoints) {
            if (!isApiAdded(allApiDoc, api)) {
                allApiDoc.addApi(api);
            }
        }
        allApiDoc.setSwaggerVersion(swaggerVersion);
        allApiDoc.setBasePath(basePath);
        allApiDoc.setApiVersion(apiVersion);

        return Response.ok().entity(allApiDoc).build();
    }

    protected Set<Class<?>> getResourceClasses(ResourceConfig rc) {
        Set<Class<?>> resourceClasses = rc.getRootResourceClasses();
        Set<Object> resourceObjects = rc.getRootResourceSingletons();
        
        Set<Class<?>> resources = new LinkedHashSet<Class<?>>();
        // @Path should always be set, right? unless something is oddd
        for (Class<?> r: resourceClasses)
            if (r.getAnnotation(Path.class)!=null) resources.add(r);
        for (Object r: resourceObjects) 
            if (r.getClass().getAnnotation(Path.class)!=null) resources.add(r.getClass());
        
        return resources;
    }

    private boolean isApiAdded(Documentation allApiDoc, DocumentationEndPoint endpoint) {
        boolean isAdded = false;
        if (allApiDoc.getApis() != null) {
            for (DocumentationEndPoint addedApi : allApiDoc.getApis()) {
                if (endpoint.getPath().equals(addedApi.getPath())) isAdded = true;
            }
        }
        return isAdded;
    }

    @GET
    @Path("/{resource}")
    public Response details(
            @Context ResourceConfig rc, 
            @Context HttpHeaders headers, 
            @Context UriInfo uriInfo,
            @PathParam("resource") String resource) {
        Class<?> target = getResourceOfLink(rc, resource);
        if (target==null) return Response.status(Response.Status.NOT_FOUND).build();
        
        // roughly duplicates JavaHelp
        String apiVersion = getConfigReader().getApiVersion();
        String swaggerVersion = getConfigReader().getSwaggerVersion();
        String basePath = getConfigReader().getBasePath();

        String apiFilterClassName = getConfigReader().getApiFilterClassName();

        Apidoc apidoc = target.getAnnotation(Apidoc.class);
        Api apidocX = target.getAnnotation(Api.class);
        Path rsPath = target.getAnnotation(Path.class);

        if ((apidoc==null && apidocX==null) || rsPath==null)
            return Response.status(Response.Status.NOT_FOUND).build();

        String apiPath = apidoc!=null ? rsPath.value() : apidocX.value();

        HelpApi helpApi = new HelpApi(apiFilterClassName);
        Documentation doc = read(target, apiVersion, swaggerVersion, basePath, apiPath);
        Documentation docs = helpApi.filterDocs(doc, headers, uriInfo, apiPath, apiPath);
        return Response.ok().entity(docs).build();
    }


    
    
    // items below here simply override the swagger Jaxrs* classes/behaviour so we can use @Path/@Apidoc instead of @Api
    
    protected ConfigReader getConfigReader() {
        if (configReader==null) configReader = new ConfigReader(null);
        return configReader;
    }

    static protected Map<Class<?>,Documentation> endpointsCache = new LinkedHashMap<Class<?>, Documentation>();
            
    protected Documentation read(Class<?> target, String apiVersion, String swaggerVersion, String basePath, String apiPath) {
        Documentation result = endpointsCache.get(target);
        if (result!=null) return result;
        JaxrsApiSpecParser parser = new ApidocJaxrsSpecParser(target, apiVersion, swaggerVersion, basePath, apiPath);
        result = parser.parse();
        endpointsCache.put(target, result);
        return result;
    }

    @Api("ignored")
    static class ApidocJaxrsSpecParser extends JaxrsApiSpecParser {
        public ApidocJaxrsSpecParser(Class<?> target, String apiVersion, String swaggerVersion, String basePath, String apiPath) {
            super(target, apiVersion, swaggerVersion, basePath, apiPath);
        }
        @Override
        public Api apiEndpoint() {
            // return an ignored item; all clients do is check it isn't null
            return ApidocJaxrsSpecParser.class.getAnnotation(Api.class);
        }
        public String getPath(Method method) {
            Path cwsPath = hostClass().getAnnotation(Path.class);
            Path mwsPath = method.getAnnotation(Path.class);
            if (cwsPath==null) return null;
            return cwsPath.value() + (mwsPath!=null ? mwsPath.value() : "");
        }
    }
    
}