package brooklyn.event.feed.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

public class HttpPollValue {

    private final HttpResponse response;
    private int responseCode;
    private Map<String,List<String>> headerLists;
    private byte[] content;

    public HttpPollValue(HttpResponse response) {
        this.response = response;
        
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteStreams.copy(response.getEntity().getContent(), out);
            content = out.toByteArray();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
    
    public HttpPollValue(int responseCode, Map<String,List<String>> headers, byte[] content) {
        this.response = null;
        this.responseCode = responseCode;
        this.headerLists.putAll(headers);
        this.content = content;
    }
    
    public int getResponseCode() {
        if (responseCode == 0) {
            responseCode = response.getStatusLine().getStatusCode();
        }
        return responseCode;
    }
    
    public Map<String, List<String>> getHeaderLists() {
        if (headerLists == null) {
            headerLists = Maps.newLinkedHashMap();
            for (Header header : response.getAllHeaders()) {
                List<String> vals = headerLists.get(header.getName());
                if (vals == null) {
                    vals = new ArrayList<String>();
                    headerLists.put(header.getName(), vals);
                }
                vals.add(header.getValue());
            }
            headerLists = Collections.unmodifiableMap(headerLists);
        }
        return headerLists;
    }
    
    public byte[] getContent() {
        if (content == null) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ByteStreams.copy(response.getEntity().getContent(), out);
                content = out.toByteArray();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        return content;
    }
}
