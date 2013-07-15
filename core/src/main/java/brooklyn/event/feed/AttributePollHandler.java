package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.time.Duration;

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
    public boolean checkSuccess(V val) {
        // Always true if no checkSuccess predicate was configured.
        return !config.hasCheckSuccessHandler() || config.getCheckSuccess().apply(val);
    }

    @Override
    public void onSuccess(V val) {
        if (lastWasFailure) {
            lastWasFailure = false;
            log.info("Success (following previous failure) reading "+entity+"->"+sensor);
        }
        
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
                if (log.isDebugEnabled()) log.debug("unable to compute "+entity+" ->"+sensor+"; val="+val+" (when inactive)", e);
            }
        }
    }

    @Override
    public void onFailure(V val) {
        if (!config.hasFailureHandler()) {
            onException(new Exception("checkSuccess of "+this+" from "+entity+" was false but poller has no failure handler"));
        } else {
            if (lastWasFailure) {
                if (log.isDebugEnabled())
                    log.debug("Recurring failure reading " + this + " from " + entity + ". Got: " + val);
            } else {
                log.warn("Read of " + entity + "->" + sensor + " failed. Got: " + val);
                lastWasFailure = true;
            }

            try {
                Object v = coerce(config.getOnFailure().apply(val));
                if (v != PollConfig.UNSET) {
                    entity.setAttribute(sensor, v);
                }
            } catch (Exception e) {
                if (feed.isConnected()) {
                    log.warn("unable to compute " + entity + "->" + sensor + "; on val=" + val, e);
                } else {
                    if (log.isDebugEnabled())
                        log.debug("unable to compute " + entity + " ->" + sensor + "; val=" + val + " (when inactive)", e);
                }
            }
        }
    }

    /**
     * @deprecated since 0.6; use {@link #onException(Exception)}
     */
    @Override
    public void onError(Exception error) {
        onException(error);
    }

    @Override
    public void onException(Exception exception) {
        if (!feed.isConnected()) {
            if (log.isDebugEnabled()) log.debug("exception reading {} from {} (while not connected or not yet connected): {}", new Object[] {this, entity, exception});
        } else if (lastWasFailure) {
            if (log.isDebugEnabled()) log.debug("recurring exception reading "+this+" from "+entity, exception);
        } else {
            // if we see an error once it is up, log it as a warning the first time until it corrects itself
            log.warn("Exception reading "+entity+"->"+sensor+": "+exception);
            if (log.isDebugEnabled())
                log.debug("details for exception reading "+entity+"->"+sensor+": "+exception, exception);
        }
        lastWasFailure = true;

        if (config.hasExceptionHandler()) {
            try {
                Object v = transformError(exception);
                if (v != PollConfig.UNSET) {
                    entity.setAttribute(sensor, v);
                }
            } catch (Exception e) {
                if (feed.isConnected()) {
                    log.warn("unable to compute "+entity+"->"+sensor+"; on exception="+exception, e);
                } else {
                    if (log.isDebugEnabled()) log.debug("unable to compute "+entity+" ->"+sensor+"; exception="+exception+" (when inactive)", e);
                }
            }
        }
    }

    /**
     * Does post-processing on the result of the actual poll, to convert it to the attribute's new value.
     * Or returns PollConfig.UNSET if the post-processing indicates that the attribute should not be changed.
     */
    protected Object transformValue(Object val) {
        if (config.hasSuccessHandler()) {
            return coerce(config.getOnSuccess().apply((V)val));
        } else {
            return coerce(val);
        }
    }
    
    /**
     * Does post-processing on a poll error, to convert it to the attribute's new value.
     * Or returns PollConfig.UNSET if the post-processing indicates that the attribute should not be changed.
     */
    protected Object transformError(Exception error) throws Exception {
        if (!config.hasExceptionHandler())
            throw new IllegalStateException("Attribute poll handler has no error handler, but attempted to transform error", error);
        return coerce(config.getOnException().apply(error));
    }

    private Object coerce(Object v) {
        if (v != PollConfig.UNSET) {
            return TypeCoercions.coerce(v, sensor.getType());
        } else {
            return v;
        }
    }
    
    @Override
    public String toString() {
        return super.toString()+"["+sensor+" @ "+entity+" <- "+feed+"]";
    }
}
