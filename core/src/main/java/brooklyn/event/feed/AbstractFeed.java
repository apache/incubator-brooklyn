package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityLocal;

/** 
 * Captures common fields and processes for sensor feeds.
 * These generally poll or subscribe to get sensor values for an entity.
 * They make it easy to poll over http, jmx, etc.
 */
public abstract class AbstractFeed {

	private static final Logger log = LoggerFactory.getLogger(AbstractFeed.class);
	
	protected final EntityLocal entity;
	protected final Poller<?> poller;
	private volatile boolean running = true;

	public AbstractFeed(EntityLocal entity) {
	    this.entity = checkNotNull(entity, "entity");;
        this.poller = new Poller<Object>(entity);
	}
	
	public boolean isActivated() {
	    return running;
	}
	
	public EntityLocal getEntity() {
	    return entity;
	}
	
    protected boolean isConnected() {
        // TODO Default impl will result in multiple logs for same error if becomes unreachable
        // (e.g. if ssh gets NoRouteToHostException, then every AttributePollHandler for that
        // feed will log.warn - so if polling for 10 sensors/attributes will get 10 log messages).
        // Would be nice if reduced this logging duplication.
        return isActivated();
    }

    protected void start() {
        if (log.isDebugEnabled()) log.debug("Starting feed {} for {}", this, entity);
        preStart();
        poller.start();
    }

	public void stop() {
		if (log.isDebugEnabled()) log.debug("stopping feed {} for {}", this, entity);
		running = false;
		preStop();
		poller.stop();
		postStop();
	}

    /**
     * For overriding.
     */
    protected void preStart() {
    }
    
    /**
     * For overriding.
     */
    protected void preStop() {
    }
    
	/**
	 * For overriding.
	 */
    protected void postStop() {
    }
}
