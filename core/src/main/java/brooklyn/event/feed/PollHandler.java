package brooklyn.event.feed;

/**
 * Notified by the Poller of the result for each job, on each poll.
 * 
 * @author aled
 */
public interface PollHandler<V> {

    public void onSuccess(V val);

    public void onError(Exception error);
}
