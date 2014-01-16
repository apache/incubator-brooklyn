/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import java.util.concurrent.TimeUnit;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;

import com.google.common.base.Functions;

/**
 * Implementation of {@link SolrServer}.
 */
public class SolrServerImpl extends SoftwareProcessImpl implements SolrServer {

    @Override
    public Integer getSolrPort() {
        return getAttribute(SolrServer.SOLR_PORT);
    }

    @Override
    public Class<SolrServerDriver> getDriverInterface() {
        return SolrServerDriver.class;
    }

    private volatile HttpFeed httpFeed;

    @Override 
    protected void connectSensors() {
        super.connectSensors();

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(String.format("http://%s:%d/solr", getAttribute(HOSTNAME), getSolrPort()))
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .onFailureOrException(Functions.constant(false)))
                .build();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();

        if (httpFeed != null) httpFeed.stop();
    }
}
