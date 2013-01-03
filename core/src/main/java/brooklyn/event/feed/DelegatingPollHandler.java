package brooklyn.event.feed;

import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * A poll handler that delegates each call to a set of poll handlers.
 * 
 * @author aled
 */
public class DelegatingPollHandler<V> implements PollHandler<V> {

    private final List<AttributePollHandler<? super V>> delegates;

    public DelegatingPollHandler(Iterable<AttributePollHandler<? super V>> delegates) {
        super();
        this.delegates = ImmutableList.copyOf(delegates);
    }
    
    @Override
    public void onSuccess(V val) {
        for (AttributePollHandler<? super V> delegate : delegates) {
            delegate.onSuccess(val);
        }
    }
    
    public void onError(Exception error) {
        for (AttributePollHandler<? super V> delegate : delegates) {
            delegate.onError(error);
        }
    }
}
