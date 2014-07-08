/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.event.feed.http.HttpPollValue;
import brooklyn.util.guava.Maybe;
import brooklyn.util.stream.Streams;
import brooklyn.util.time.Time;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

public class HttpToolResponse implements HttpPollValue {

    private static final Logger log = LoggerFactory.getLogger(HttpToolResponse.class);
    
    private final Object mutex = new Object();
    private final HttpResponse response;
    private final long startTime;
    private final long durationMillisOfFirstResponse;
    private final long durationMillisOfFullContent;
    private int responseCode;
    private String reasonPhrase;
    private Map<String,List<String>> headerLists;
    private byte[] content;


    public HttpToolResponse(HttpResponse response, long startTime) {
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
                log.trace("HttpPollValue latency "+Time.makeTimeStringRounded(durationMillisOfFirstResponse)+" / "+Time.makeTimeStringRounded(durationMillisOfFullContent)+", content size "+content.length);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }        
    }

    public HttpToolResponse(int responseCode, Map<String,List<String>> headers, byte[] content,
            long startTime, long durationMillisOfFirstResponse, long durationMillisOfFullContent) {
        this.response = null;
        this.responseCode = responseCode;
        this.headerLists = ImmutableMap.copyOf(headers);
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

    public String getReasonPhrase() {
        synchronized (mutex) {
            if (reasonPhrase == null) {
                reasonPhrase = response.getStatusLine().getReasonPhrase();
            }
        }
        return reasonPhrase;
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
                    Streams.closeQuietly(in);
                }
            }
        }
        return content;
    }

    public String getContentAsString() {
        return new String(getContent());
    }
    
    public Maybe<HttpResponse> getResponse() {
        return Maybe.fromNullable(response);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(getClass())
                .add("responseCode", responseCode)
                .toString();
    }

}
