package brooklyn.rest;

import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.resources.CatalogResource;

import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;

public class BrooklynRestApiForTesting {

    private static final Logger log = LoggerFactory.getLogger(BrooklynRestApiForTesting.class);
    
    public static void main(String[] args) throws Exception {

        // WORKING!!!
        
//        ResourceConfig config = new DefaultResourceConfig();
//        config.getSingletons().add(new CatalogResource());
//        config.getSingletons().add(new JacksonJsonProvider());
////        config.getFeatures().put(ResourceConfig.FEATURE_DISABLE_WADL, Boolean.TRUE);
//        
//        Server server = new Server(8098);
//        
//        ServletContextHandler root = new ServletContextHandler(ServletContextHandler.SESSIONS);
////        Context root = new Context(server,"/",Context.SESSIONS);
//        root.setContextPath("/");
//        
//        
//        ServletHolder sh = new ServletHolder(new ServletContainer(
//                config
////                new PackagesResourceConfig("brooklyn.rest.resources")
//                ));
////        sh.setInitParameter("com.sun.jersey.api.json.POJOMappingFeature", "true");
//        root.addServlet(sh, "/*");
//        server.setHandler(root);
//        
//        server.start();
        
        
        ManagementContext managementContext = new LocalManagementContext();

        ResourceConfig config = new DefaultResourceConfig();
        config.getSingletons().add(new CatalogResource());
        config.getSingletons().add(new JacksonJsonProvider());
        

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext);
        // TODO other attrs
        ServletHolder servletHolder = new ServletHolder(new ServletContainer(config));
        context.addServlet(servletHolder, "/*");
        context.setContextPath("/");
        
        Server server = new Server(8098);
        server.setHandler(context);
        server.start();
        
//        context.setWar("./target/classes");
        
////        context.setParentLoaderPriority(true);
//
////        // TODO swappable CHC
////        ContextHandlerCollection handlers = new ContextHandlerCollection();
////        handlers.addHandler(context);
//////        handlers.updateHandler(context);
////        
//        server.setHandler(handlers);
//        server.start();
        
        log.info("Press Ctrl-C to Quit");
    }

    /*

  <servlet>
    <servlet-name>Jersey REST Service</servlet-name>
    <servlet-class>com.sun.jersey.spi.container.servlet.ServletContainer</servlet-class>
    <init-param>
      <param-name>com.sun.jersey.config.property.packages</param-name>
      <param-value>de.vogella.jersey.jaxb</param-value>
    </init-param>
    <load-on-startup>1</load-on-startup>
  </servlet>
  <servlet-mapping>
    <servlet-name>Jersey REST Service</servlet-name>
    <url-pattern>/rest/*</url-pattern>
  </servlet-mapping>
  
     */
}
//