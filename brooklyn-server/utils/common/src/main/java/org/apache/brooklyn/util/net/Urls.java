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
package org.apache.brooklyn.util.net;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.io.BaseEncoding;
import com.google.common.net.MediaType;

public class Urls {

    public static Function<String,URI> stringToUriFunction() {
        return StringToUri.INSTANCE;
    }
    
    public static Function<String,URL> stringToUrlFunction() {
        return StringToUrl.INSTANCE;
    }
    
    private static enum StringToUri implements Function<String,URI> {
        INSTANCE;
        @Override public URI apply(@Nullable String input) {
            return toUri(input);
        }
        @Override
        public String toString() {
            return "StringToUri";
        }
    }

    private static enum StringToUrl implements Function<String,URL> {
        INSTANCE;
        @Override public URL apply(@Nullable String input) {
            return toUrl(input);
        }
        @Override
        public String toString() {
            return "StringToUrl";
        }
    }

    /** creates a URL, preserving null and propagating exceptions *unchecked* */
    public static final URL toUrl(@Nullable String url) {
        if (url==null) return null;
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            // FOAD
            throw Throwables.propagate(e);
        }
    }
    
    /** creates a URL, preserving null and propagating exceptions *unchecked* */
    public static final URL toUrl(@Nullable URI uri) {
        if (uri==null) return null;
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            // FOAD
            throw Throwables.propagate(e);
        }
    }

    /** creates a URI, preserving null and propagating exceptions *unchecked* */
    public static final URI toUri(@Nullable String uri) {
        if (uri==null) return null;
        return URI.create(uri);
    }
    
    /** creates a URI, preserving null and propagating exceptions *unchecked* */
    public static final URI toUri(@Nullable URL url) {
        if (url==null) return null;
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            // FOAD
            throw Throwables.propagate(e);
        }
    }

    /** returns true if the string begins with a non-empty string of letters followed by a colon,
     * i.e. "protocol:" returns true, but "/" returns false */
    public static boolean isUrlWithProtocol(String x) {
        if (x==null) return false;
        for (int i=0; i<x.length(); i++) {
            char c = x.charAt(i);
            if (c==':') return i>0;
            if (!Character.isLetter(c)) return false; 
        }
        return false;
    }
    
    /** returns the items with exactly one "/" between items (whether or not the individual items start or end with /),
     * except where character before the / is a : (url syntax) in which case it will permit multiple (will not remove any).
     * Throws a NullPointerException if any elements of 'items' is null.
     *  */
    public static String mergePaths(String ...items) {
        List<String> parts = Arrays.asList(items);

        if (parts.contains(null)) {
            throw new NullPointerException(String.format("Unable to reliably merge path from parts: %s; input contains null values", parts));
        }

        StringBuilder result = new StringBuilder();
        for (String part: parts) {
            boolean trimThisMerge = result.length()>0 && !result.toString().endsWith("://") && !result.toString().endsWith(":///") && !result.toString().endsWith(":");
            if (trimThisMerge) {
                while (result.length()>0 && result.charAt(result.length()-1)=='/')
                    result.deleteCharAt(result.length()-1);
                result.append('/');
            }
            int i = result.length();
            result.append(part);
            if (trimThisMerge) {
                while (result.length()>i && result.charAt(i)=='/')
                    result.deleteCharAt(i);
            }
        }
        return result.toString();
    }

    /** encodes the string suitable for use in a URL, using default character set
     * (non-deprecated version of URLEncoder.encode) */
    @SuppressWarnings("deprecation")
    public static String encode(String text) {
        return URLEncoder.encode(text);
    }
    /** As {@link #encode(String)} */
    @SuppressWarnings("deprecation")
    public static String decode(String text) {
        return URLDecoder.decode(text);
    }

    /** returns the protocol (e.g. http) if one appears to be specified, or else null;
     * 'protocol' here should consist of 2 or more _letters_ only followed by a colon
     * (2 required to prevent {@code c:\xxx} being treated as a url)
     */
    public static String getProtocol(String url) {
        if (url==null) return null;
        int i=0;
        StringBuilder result = new StringBuilder();
        while (true) {
            if (url.length()<=i) return null;
            char c = url.charAt(i);
            if (Character.isLetter(c)) result.append(c);
            else if (c==':') {
                if (i>=2) return result.toString().toLowerCase();
                return null;
            } else return null;
            i++;
        }
    }

    /** return the last segment of the given url before any '?', e.g. the filename or last directory name in the case of directories
     * (cf unix `basename`) */
    public static String getBasename(String url) {
        if (url==null) return null;
        if (getProtocol(url)!=null) {
            int firstQ = url.indexOf('?');
            if (firstQ>=0)
                url = url.substring(0, firstQ);
        }
        url = Strings.removeAllFromEnd(url, "/");
        return url.substring(url.lastIndexOf('/')+1);
    }

    public static boolean isDirectory(String fileUrl) {
        File file;
        if (isUrlWithProtocol(fileUrl)) {
            if (getProtocol(fileUrl).equals("file")) {
                file = new File(URI.create(fileUrl));
            } else {
                return false;
            }
        } else {
            file = new File(fileUrl);
        }
        return file.isDirectory();
    }

    public static File toFile(String fileUrl) {
        if (isUrlWithProtocol(fileUrl)) {
            if (getProtocol(fileUrl).equals("file")) {
                return new File(URI.create(fileUrl));
            } else {
                throw new IllegalArgumentException("Not a file protocol URL: " + fileUrl);
            }
        } else {
            return new File(fileUrl);
        }
    }

    /** as {@link #asDataUrlBase64(String)} with plain text */
    public static String asDataUrlBase64(String data) {
        return asDataUrlBase64(MediaType.PLAIN_TEXT_UTF_8, data.getBytes());
    }
    
    /** 
     * Creates a "data:..." scheme URL for use with supported parsers, using Base64 encoding.
     * (But note, by default Java's URL is not one of them, although Brooklyn's ResourceUtils does support it.)
     * <p>
     * It is not necessary (at least for Brookyn's routines) to base64 encode it, but recommended as that is likely more
     * portable and easier to work with if odd characters are included.
     * <p>
     * It is worth noting that Base64 uses '+' which can be replaced by ' ' in some URL parsing.  
     * But in practice it does not seem to cause issues.
     * An alternative is to use {@link BaseEncoding#base64Url()} but it is not clear how widely that is supported
     * (nor what parameter should be given to indicate that type of encoding, as the spec calls for 'base64'!)
     * <p>
     * null type means no type info will be included in the URL. */
    public static String asDataUrlBase64(MediaType type, byte[] bytes) {
        return "data:"+(type!=null ? type.withoutParameters().toString() : "")+";base64,"+new String(BaseEncoding.base64().encode(bytes));
    }

}
