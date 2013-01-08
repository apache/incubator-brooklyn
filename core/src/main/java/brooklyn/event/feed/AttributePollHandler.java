package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Function;

/**
 * Handler for when polling an entity's attribute. On each poll result the entity's attribute is set.
 * 
 * Calls to onSuccess and onError will happen sequentially, but may be called from different threads 
 * each time. Note that no guarantees of a synchronized block exist, so additional synchronization 
 * would be required for the Java memory model's "happens before" relationship.
 * 
 * @author aled
 */
public class AttributePollHandler<V> implements PollHandler<V> {

    public static final Logger log = LoggerFactory.getLogger(AttributePollHandler.class);

    private final FeedConfig<V,?,?> config;
    private final EntityLocal entity;
    private final AttributeSensor sensor;
    private final AbstractFeed feed;
    private volatile boolean lastWasFailure = false;
    
    public AttributePollHandler(FeedConfig config, EntityLocal entity, AbstractFeed feed) {
        this.config = checkNotNull(config, "config");
        this.entity = checkNotNull(entity, "entity");
        this.sensor = checkNotNull(config.getSensor(), "sensor");
        this.feed = checkNotNull(feed, "feed");
    }
    
    @Override
    public void onSuccess(V val) {
        lastWasFailure = false;
        
        if (log.isTraceEnabled()) log.trace("poll for {}->{} got: {}", new Object[] {entity, sensor, val});
        
        try {
            Object v = transformValue(val);
            if (v != PollConfig.UNSET) {
                entity.setAttribute(sensor, v);
            }
        } catch (Exception e) {
            if (feed.isConnected()) {
                log.warn("unable to compute "+entity+"->"+sensor+"; on val="+val, e);
            } else {
                if (log.isDebugEnabled()) log.debug("unable to compute "+entity+" ->"+sensor+"; val="+val+" (when deactive)", e);
            }
        }
    }

    @Override
    public void onError(Exception error) {
        if (!feed.isConnected()) {
            if (log.isDebugEnabled()) log.debug("error reading {} from {} (while not connected or not yet connected): {}", new Object[] {this, entity, error});
        } else if (lastWasFailure) {
            if (log.isDebugEnabled()) log.debug("recurring error reading "+this+" from "+entity, error);
        } else {
            log.warn("error reading "+entity+"->"+sensor, error);
        }
        lastWasFailure = true;
        
        if (hasErrorHandler()) {
            try {
                Object v = transformError(error);
                if (v != PollConfig.UNSET) {
                    entity.setAttribute(sensor, v);
                }
            } catch (Exception e) {
                if (feed.isConnected()) {
                    log.warn("unable to compute "+entity+"->"+sensor+"; on error="+error, e);
                } else {
                    if (log.isDebugEnabled()) log.debug("unable to compute "+entity+" ->"+sensor+"; error="+error+" (when deactive)", e);
                }
            }
        }
    }
    
    /**
     * Does post-processing on the result of the actual poll, to convert it to the attribute's new value.
     * Or returns PollConfig.UNSET if the post-processing indicates that the attribute should not be changed.
     */
    protected Object transformValue(Object val) {
        Function<? super V,?> f = config.getOnSuccess();
        Object v;
        if (f != null) {
            v = f.apply((V)val);
        } else {
            v = val;
        }
        
        if (v != PollConfig.UNSET) {
            return TypeCoercions.coerce(v, sensor.getType());
        } else {
            return v;
        }
    }
    
    protected boolean hasErrorHandler() {
        return (config.getOnError() != null);
    }
    
    /**
     * Does post-processing on a poll error, to convert it to the attribute's new value.
     * Or returns PollConfig.UNSET if the post-processing indicates that the attribute should not be changed.
     */
    protected Object transformError(Exception error) throws Exception {
        Function<? super Exception,?> f = config.getOnError();
        if (f == null) throw new IllegalStateException("Attribute poll handler has no error handler, but attempted to transform error", error);
        
        Object v = f.apply(error);
        
        if (v != PollConfig.UNSET) {
            return TypeCoercions.coerce(v, sensor.getType());
        } else {
            return v;
        }
    }
}
