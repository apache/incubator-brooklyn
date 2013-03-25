package brooklyn.event.feed.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

public class HttpPollValue {

    private static final Logger log = LoggerFactory.getLogger(HttpPollValue.class);
    
    private final Object mutex = new Object();
    private final HttpResponse response;
    private final Long startTime;
    private final Long durationMillisOfFirstResponse;
    private final Long durationMillisOfFullContent;
    private int responseCode;
    private Map<String,List<String>> headerLists;
    private byte[] content;
    
    public HttpPollValue(HttpResponse response) {
        this(response, null);
    }

    public HttpPollValue(HttpResponse response, Long startTime) {
        this.response = response;
        this.startTime = startTime; 
        
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (this.startTime!=null) {
                response.getEntity().getContentLength();
                // TODO is this useful?  if so should expose. if usu same as full content then just expose that.
                durationMillisOfFirstResponse = System.currentTimeMillis() - startTime;
            } else {
                durationMillisOfFirstResponse = null;
            }
            
            ByteStreams.copy(response.getEntity().getContent(), out);
            content = out.toByteArray();
            
            if (this.startTime!=null) {
                response.getEntity().getContentLength();
                durationMillisOfFullContent = System.currentTimeMillis() - startTime;
                // TODO trace
                if (log.isDebugEnabled())
                    log.debug("latency detector detected "+durationMillisOfFirstResponse+" / "+durationMillisOfFullContent+" latency");
            } else {
                durationMillisOfFullContent = null;
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }        
    }

    public HttpPollValue(int responseCode, Map<String,List<String>> headers, byte[] content) {
        this.response = null;
        this.responseCode = responseCode;
        this.headerLists.putAll(headers);
        this.content = content;
        this.startTime = null;
        durationMillisOfFirstResponse = null;
        durationMillisOfFullContent = null;
    }
    
    public int getResponseCode() {
        synchronized (mutex) {
            if (responseCode == 0) {
                responseCode = response.getStatusLine().getStatusCode();
            }
        }
        return responseCode;
    }

    @Nullable
    /** returns latency, in milliseconds, if value was initialized with a start time */
    public Long getLatencyFullContent() {
        if (startTime==null || durationMillisOfFullContent==null) return null;
        return durationMillisOfFullContent;
    }
    
    @Nullable
    /** returns latency, in milliseconds, if value was initialized with a start time */
    public Long getLatencyFirstResponse() {
        if (startTime==null || durationMillisOfFirstResponse==null) return null;
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
}
