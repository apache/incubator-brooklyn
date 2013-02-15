/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.couchdb;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.adapter.FunctionSensorAdapter;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.util.MutableMap;

/**
 * Implementation of {@link CouchDBNode}.
 */
public class CouchDBNodeImpl extends SoftwareProcessImpl implements CouchDBNode {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    /** serialVersionUID */

    private static final Logger log = LoggerFactory.getLogger(CouchDBNodeImpl.class);

    public CouchDBNodeImpl() {
        this(MutableMap.of(), null);
    }
    public CouchDBNodeImpl(Map<?, ?> properties) {
        this(properties, null);
    }
    public CouchDBNodeImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public CouchDBNodeImpl(Map<?, ?> properties, Entity parent) {
        super(properties, parent);
    }

    public Integer getHttpPort() { return getAttribute(CouchDBNode.HTTP_PORT); }
    public Integer getHttpsPort() { return getAttribute(CouchDBNode.HTTPS_PORT); }
    public String getClusterName() { return getAttribute(CouchDBNode.CLUSTER_NAME); }

    @Override
    public Class<CouchDBNodeDriver> getDriverInterface() {
        return CouchDBNodeDriver.class;
    }

    private volatile HttpFeed httpFeed;
    private volatile FunctionFeed serviceUp;

    @Override 
    protected void connectSensors() {
        super.connectSensors();

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .build();
 
        serviceUp = FunctionFeed.builder()
                .entity(this)
                .period(5000)
                .poll(new FunctionPollConfig<Boolean, Boolean>(SERVICE_UP)
                    .callable(new Callable<Boolean>() {
                        public Boolean call() {
                            return getDriver().isRunning();
                        }
                    }))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        if (httpFeed != null && httpFeed.isActivated()) httpFeed.stop();
        if (serviceUp != null && serviceUp.isActivated()) serviceUp.stop();
    }
}
