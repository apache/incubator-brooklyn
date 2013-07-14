package brooklyn.entity.webapp.jetty;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.AddingEnricher;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.java.JavaAppUtils;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import brooklyn.entity.webapp.tomcat.TomcatServer;
import brooklyn.event.Sensor;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.time.Duration;

import com.google.common.base.Functions;
import com.google.common.base.Predicates;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Jetty instance.
 */
public class Jetty6ServerImpl extends JavaWebAppSoftwareProcessImpl implements Jetty6Server {

    private static final Logger log = LoggerFactory.getLogger(Jetty6ServerImpl.class);

    private volatile JmxFeed jmxFeed;

    @Override
    public void connectSensors() {
        super.connectSensors();
        
        if (getDriver().isJmxEnabled()) {
            String serverMbeanName = "org.mortbay.jetty:type=server,id=0";
            String statsMbeanName = "org.mortbay.jetty.handler:type=atomicstatisticshandler,id=0";

            jmxFeed = JmxFeed.builder()
                    .entity(this)
                    .period(500, TimeUnit.MILLISECONDS)
                    .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
                            .objectName(serverMbeanName)
                            .attributeName("running")
                            .onSuccess(Functions.forPredicate(Predicates.<Object>equalTo(true)))
                            .onException(Functions.constant(false)))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(REQUEST_COUNT)
                            .objectName(statsMbeanName)
                            .attributeName("requests"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(RESPONSES_4XX_COUNT)
                            .objectName(statsMbeanName)
                            .attributeName("responses4xx"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(RESPONSES_5XX_COUNT)
                            .objectName(statsMbeanName)
                            .attributeName("responses5xx"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(TOTAL_PROCESSING_TIME)
                            .objectName(statsMbeanName)
                            .attributeName("requestTimeTotal"))
                    .pollAttribute(new JmxAttributePollConfig<Integer>(MAX_PROCESSING_TIME)
                            .objectName(statsMbeanName)
                            .attributeName("requestTimeMax"))
                    // NB: requestsActive may be useful
                    .build();
            
            addEnricher(new AddingEnricher(new Sensor[] { RESPONSES_4XX_COUNT, RESPONSES_5XX_COUNT },
                    ERROR_COUNT));

            JavaAppUtils.connectMXBeanSensors(this);
            JavaAppUtils.connectJavaAppServerPolicies(this);
        } else {
            // if not using JMX
            LOG.warn("Jetty running without JMX monitoring; limited visibility of service available");
            // TODO we could at least check the http/s is up
        }
    }

    @Override
    public void waitForServiceUp() {
        // Increases wait-time by overriding this
        LOG.info("Waiting for {} up, via {}", this, jmxFeed == null ? "" : jmxFeed.getJmxUri());
        waitForServiceUp(Duration.of(getConfig(TomcatServer.START_TIMEOUT), TimeUnit.SECONDS));
    }

    @Override
    public Class getDriverInterface() {
        return Jetty6Driver.class;
    }
    
    @Override
    public String getShortName() {
        return "Jetty";
    }
    
    @Override
    @Effector(description = "Deploys the given artifact, from a source URL, to a given deployment filename/context")
    public void deploy(@EffectorParam(name = "url", description = "URL of WAR file") String url,
            @EffectorParam(name = "targetName", description = "context path where WAR should be deployed (/ for ROOT)") String targetName) {
        super.deploy(url, targetName);
        restartIfRunning();
    }
    
    protected void restartIfRunning() {
        // TODO for now we simply restart jetty to achieve "hot deployment"; should use the config mechanisms
        Lifecycle serviceState = getAttribute(SERVICE_STATE);
        if (serviceState == Lifecycle.RUNNING)
            restart();
        // may need a restart also if deploy effector is done in parallel to starting
        // but note this routine is used by initialDeployWars so just being in starting state is not enough!
    }

    @Override
    @Effector(description = "Undeploys the given context/artifact")
    public void undeploy(@EffectorParam(name = "targetName") String targetName) {
        super.undeploy(targetName);
        restartIfRunning();
    }

}

