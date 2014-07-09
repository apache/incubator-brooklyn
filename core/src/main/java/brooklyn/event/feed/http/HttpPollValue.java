package brooklyn.event.feed.http;

import java.util.List;
import java.util.Map;

import brooklyn.util.http.HttpToolResponse;

/** @deprecated since 0.7.0, use {@link HttpToolResponse}.
 * the old {@link HttpPollValue} concrete class has been renamed {@link HttpToolResponse}
 * because it has nothing specific to polls. this is now just a transitional interface. */
@Deprecated
public interface HttpPollValue {

    public int getResponseCode();
    public String getReasonPhrase();
    public long getStartTime();
    public long getLatencyFullContent();
    public long getLatencyFirstResponse();
    public Map<String, List<String>> getHeaderLists();
    public byte[] getContent();
    
}
