package brooklyn.entity.brooklynnode;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;

import com.google.common.base.Functions;

public class BrooklynNodeImpl extends SoftwareProcessImpl implements BrooklynNode {

    private static final Logger log = LoggerFactory.getLogger(BrooklynNodeImpl.class);

    static {
        RendererHints.register(WEB_CONSOLE_URI, new RendererHints.NamedActionWithUrl("Open"));
    }

    private HttpFeed httpFeed;
    
    public BrooklynNodeImpl() {
        super();
    }

    public BrooklynNodeImpl(Entity parent) {
        super(parent);
    }
    
    @Override
    public Class getDriverInterface() {
        return BrooklynNodeDriver.class;
    }

    public List<String> getClasspath() {
        return getConfig(CLASSPATH);
    }
    
    protected List<String> getEnabledHttpProtocols() {
        return getAttribute(ENABLED_HTTP_PROTOCOLS);
    }
    
    protected boolean isHttpProtocolEnabled(String protocol) {
        List<String> protocols = getAttribute(ENABLED_HTTP_PROTOCOLS);
        for (String contender : protocols) {
            if (protocol.equalsIgnoreCase(contender)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        
        // TODO what sensors should we poll?
        ConfigToAttributes.apply(this);

        String host;
        if (getAttribute(NO_WEB_CONSOLE_AUTHENTICATION)) {
            host = "localhost"; // Because of --noConsoleSecurity option
        } else {
            host = getAttribute(HOSTNAME);
        }

        URI webConsoleUri;
        if (isHttpProtocolEnabled("http")) {
            int port = getConfig(PORT_MAPPER).apply(getAttribute(HTTP_PORT));
            webConsoleUri = URI.create(String.format("http://%s:%s", host, port));
            setAttribute(WEB_CONSOLE_URI, webConsoleUri);
        } else if (isHttpProtocolEnabled("https")) {
            int port = getConfig(PORT_MAPPER).apply(getAttribute(HTTPS_PORT));
            webConsoleUri = URI.create(String.format("https://%s:%s", host, port));
            setAttribute(WEB_CONSOLE_URI, webConsoleUri);
        } else {
            // web-console is not enabled
            setAttribute(WEB_CONSOLE_URI, null);
            webConsoleUri = null;
        }

        if (webConsoleUri != null) {
            httpFeed = HttpFeed.builder()
                    .entity(this)
                    .period(200)
                    .baseUri(webConsoleUri)
                    .credentialsIfNotNull(getConfig(MANAGEMENT_USER), getConfig(MANAGEMENT_PASSWORD))
                    .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                            .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                            .setOnFailureOrException(false))
                    .build();

        } else {
            setAttribute(SERVICE_UP, true);
        }
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        
        if (httpFeed != null) httpFeed.stop();
    }
}
