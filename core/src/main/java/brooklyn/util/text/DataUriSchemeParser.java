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
package brooklyn.util.text;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bouncycastle.util.encoders.Base64;

import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
//import com.sun.jersey.core.util.Base64;

/** implementation (currently hokey) of RFC-2397 data: URI scheme.
 * see: http://stackoverflow.com/questions/12353552/any-rfc-2397-data-uri-parser-for-java */
public class DataUriSchemeParser {

    public static final String PROTOCOL_PREFIX = "data:";
    public static final String DEFAULT_MIME_TYPE = "text/plain";
    public static final String DEFAULT_CHARSET = "US-ASCII";
    
    private final String url;
    private int parseIndex = 0;
    private boolean isParsed = false;
    private boolean allowMissingComma = false;
    private boolean allowSlashesAfterColon = false;
    
    private String mimeType;
    private byte[] data;
    private Map<String,String> parameters = new LinkedHashMap<String,String>();

    public DataUriSchemeParser(String url) {
        this.url = Preconditions.checkNotNull(url, "url");
    }

    // ---- static conveniences -----
    
    public static String toString(String url) {
        return new DataUriSchemeParser(url).lax().parse().getDataAsString();
    }

    public static byte[] toBytes(String url) {
        return new DataUriSchemeParser(url).lax().parse().getData();
    }

    // ---- accessors (once it is parsed) -----------
    
    public String getCharset() {
        String charset = parameters.get("charset");
        if (charset!=null) return charset;
        return DEFAULT_CHARSET;
    }

    public String getMimeType() {
        assertParsed();
        if (mimeType!=null) return mimeType;
        return DEFAULT_MIME_TYPE;
    }
    
    public Map<String, String> getParameters() {
        return ImmutableMap.<String, String>copyOf(parameters);
    }

    public byte[] getData() {
        assertParsed();
        return data;
    }
    
    public ByteArrayInputStream getDataAsInputStream() {
        return new ByteArrayInputStream(getData());
    }

    public String getDataAsString() {
        return new String(getData(), Charset.forName(getCharset()));
    }

    // ---- config ------------------
    
    public synchronized DataUriSchemeParser lax() {
        return allowMissingComma(true).allowSlashesAfterColon(true);
    }
        
    public synchronized DataUriSchemeParser allowMissingComma(boolean allowMissingComma) {
        assertNotParsed();
        this.allowMissingComma = allowMissingComma;
        return this;
    }
    
    public synchronized DataUriSchemeParser allowSlashesAfterColon(boolean allowSlashesAfterColon) {
        assertNotParsed();
        this.allowSlashesAfterColon = allowSlashesAfterColon;
        return this;
    }
    
    private void assertNotParsed() {
        if (isParsed) throw new IllegalStateException("Operation not permitted after parsing");
    }

    private void assertParsed() {
        if (!isParsed) throw new IllegalStateException("Operation not permitted before parsing");
    }

    public synchronized DataUriSchemeParser parse() {
        try {
            return parseChecked();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public synchronized DataUriSchemeParser parseChecked() throws UnsupportedEncodingException, MalformedURLException {
        if (isParsed) return this;
        
        skipOptional(PROTOCOL_PREFIX);
        if (allowSlashesAfterColon)
            while (skipOptional("/")) ;
        
        if (allowMissingComma && remainder().indexOf(',')==-1) {
            mimeType = DEFAULT_MIME_TYPE;
            parameters.put("charset", DEFAULT_CHARSET);
        } else {        
            parseMediaType();
            parseParameterOrParameterValues();
            skipRequired(",");
        }
        
        parseData();
        
        isParsed = true;
        return this;
    }

    private void parseMediaType() throws MalformedURLException {
        if (remainder().startsWith(";") || remainder().startsWith(","))
            return;
        int slash = remainder().indexOf("/");
        if (slash==-1) throw new MalformedURLException("Missing required '/' in MIME type of data: URL");
        String type = read(slash);
        skipRequired("/");
        int next = nextSemiOrComma();
        String subtype = read(next);
        mimeType = type+"/"+subtype;
    }

    private String read(int next) {
        String result = remainder().substring(0, next);
        parseIndex += next;
        return result;
    }

    private int nextSemiOrComma() throws MalformedURLException {
        int semi = remainder().indexOf(';');
        int comma = remainder().indexOf(',');
        if (semi<0 && comma<0) throw new MalformedURLException("Missing required ',' in data: URL");
        if (semi<0) return comma;
        if (comma<0) return semi;
        return Math.min(semi, comma);
    }

    private void parseParameterOrParameterValues() throws MalformedURLException {
        while (true) {
            if (!remainder().startsWith(";")) return;
            parseIndex++;
            int eq = remainder().indexOf('=');
            String word, value;
            int nextSemiOrComma = nextSemiOrComma();
            if (eq==-1 || eq>nextSemiOrComma) {
                word = read(nextSemiOrComma);
                value = null;
            } else {
                word = read(eq);
                if (remainder().startsWith("\"")) {
                    // is quoted
                    parseIndex++;
                    int nextUnescapedQuote = nextUnescapedQuote();
                    value = "\"" + read(nextUnescapedQuote);
                } else {
                    value = read(nextSemiOrComma());
                }
            }
            parameters.put(word, value);
        }
    }

    private int nextUnescapedQuote() throws MalformedURLException {
        int i=0;
        String r = remainder();
        boolean escaped = false;
        while (i<r.length()) {
            if (escaped) {
                escaped = false;
            } else {
                if (r.charAt(i)=='"') return i;
                if (r.charAt(i)=='\\') escaped = true;
            }
            i++;
        }
        throw new MalformedURLException("Unclosed double-quote in data: URL");
    }

    private void parseData() throws UnsupportedEncodingException, MalformedURLException {
        if (parameters.containsKey("base64")) {
            String base64value = parameters.get("base64");
            if (base64value!=null)
                throw new MalformedURLException("base64 parameter must not take a value ("+base64value+") in data: URL");
            data = Base64.decode(remainder());
        } else {
            data = URLDecoder.decode(remainder(), getCharset()).getBytes(Charset.forName(getCharset()));
        }
    }

    private String remainder() {
        return url.substring(parseIndex);
    }

    private boolean skipOptional(String word) {
        if (remainder().startsWith(word)) {
            parseIndex += word.length();
            return true;
        }
        return false;
    }

    private void skipRequired(String word) throws MalformedURLException {
        if (!remainder().startsWith(word))
            throw new MalformedURLException("Missing required '"+word+"' at position "+parseIndex+" of data: URL");
        parseIndex += word.length();
    }

}
