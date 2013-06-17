package brooklyn.event.feed.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Objects;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.time.Time;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

public class HttpPollValue {

    private static final Logger log = LoggerFactory.getLogger(HttpPollValue.class);
    
    private final Object mutex = new Object();
    private final HttpResponse response;
    private final long startTime;
    private final long durationMillisOfFirstResponse;
    private final long durationMillisOfFullContent;
    private int responseCode;
    private Map<String,List<String>> headerLists;
    private byte[] content;

    /** @deprecated since 0.5.0 caller should supply start time for accurate measurement */
    @Deprecated
    public HttpPollValue(HttpResponse response) {
        this(response, System.currentTimeMillis());
    }

    public HttpPollValue(HttpResponse response, long startTime) {
        this.response = response;
        this.startTime = startTime; 
        
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            response.getEntity().getContentLength();
            durationMillisOfFirstResponse = System.currentTimeMillis() - startTime;
            
            ByteStreams.copy(response.getEntity().getContent(), out);
            content = out.toByteArray();
            
            response.getEntity().getContentLength();
            durationMillisOfFullContent = System.currentTimeMillis() - startTime;
            if (log.isTraceEnabled())
                log.trace("HttpPollValue latency "+Time.makeTimeString(durationMillisOfFirstResponse)+" / "+Time.makeTimeString(durationMillisOfFullContent)+", content size "+content.length);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }        
    }

    /** @deprecated since 0.5.0 caller should supply start time for accurate measurement */
    public HttpPollValue(int responseCode, Map<String,List<String>> headers, byte[] content) {
        this(responseCode, headers, content,
                System.currentTimeMillis(), -1, -1);
    }
    public HttpPollValue(int responseCode, Map<String,List<String>> headers, byte[] content,
            long startTime, long durationMillisOfFirstResponse, long durationMillisOfFullContent) {
        this.response = null;
        this.responseCode = responseCode;
        this.headerLists.putAll(headers);
        this.content = content;
        this.startTime = startTime;
        this.durationMillisOfFirstResponse = durationMillisOfFirstResponse;
        this.durationMillisOfFullContent = durationMillisOfFullContent;
    }
    
    public int getResponseCode() {
        synchronized (mutex) {
            if (responseCode == 0) {
                responseCode = response.getStatusLine().getStatusCode();
            }
        }
        return responseCode;
    }

    /** returns the timestamp (millis since 1970) when this request was started */ 
    public long getStartTime() {
        return startTime;
    }
    
    /** returns latency, in milliseconds, if value was initialized with a start time */
    public long getLatencyFullContent() {
        return durationMillisOfFullContent;
    }
    
    /** returns latency, in milliseconds, before response started coming in */
    public long getLatencyFirstResponse() {
        return durationMillisOfFirstResponse;
    }
    
    public Map<String, List<String>> getHeaderLists() {
        synchronized (mutex) {
            if (headerLists == null) {
                Map<String, List<String>> headerListsMutable = Maps.newLinkedHashMap();
                for (Header header : response.getAllHeaders()) {
                    List<String> vals = headerListsMutable.get(header.getName());
                    if (vals == null) {
                        vals = new ArrayList<String>();
                        headerListsMutable.put(header.getName(), vals);
                    }
                    vals.add(header.getValue());
                }
                headerLists = Collections.unmodifiableMap(headerListsMutable);
            }
        }
        return headerLists;
    }
    
    public byte[] getContent() {
        synchronized (mutex) {
            if (content == null) {
                InputStream in = null;
                try {
                    in = response.getEntity().getContent();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ByteStreams.copy(in, out);
                    content = out.toByteArray();
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                } finally {
                    Closeables.closeQuietly(in);
                }
            }
        }
        return content;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(getClass())
                .add("responseCode", responseCode)
                .toString();
    }
}
