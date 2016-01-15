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
package org.apache.brooklyn.util.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import org.apache.brooklyn.core.internal.BrooklynInitialization;
import org.apache.brooklyn.core.mgmt.classloading.JavaBrooklynClassLoadingContext;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpTool.HttpClientBuilder;
import org.apache.brooklyn.util.core.text.DataUriSchemeParser;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.Threads;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import org.apache.brooklyn.util.osgi.OsgiUtils;

public class ResourceUtils {
    
    private static final Logger log = LoggerFactory.getLogger(ResourceUtils.class);
    private static final List<Function<Object,BrooklynClassLoadingContext>> classLoaderProviders = Lists.newCopyOnWriteArrayList();

    private BrooklynClassLoadingContext loader = null;
    private String context = null;
    private Object contextObject = null;
    
    static { BrooklynInitialization.initNetworking(); }
    
    /**
     * Creates a {@link ResourceUtils} object with a specific class loader and context.
     * <p>
     * Use the provided {@link ClassLoader} object for class loading with the
     * {@code contextObject} for context and the {@code contextMessage} string for
     * error messages.
     *
     * @see ResourceUtils#create(Object, String)
     * @see ResourceUtils#create(Object)
     */
    public static final ResourceUtils create(ClassLoader loader, Object contextObject, String contextMessage) {
        return new ResourceUtils(loader, contextObject, contextMessage);
    }

    /**
     * Creates a {@link ResourceUtils} object with a specific class loader and context.
     * <p>
     * Use the provided {@link BrooklynClassLoadingContext} object for class loading with the
     * {@code contextObject} for context and the {@code contextMessage} string for
     * error messages.
     *
     * @see ResourceUtils#create(Object, String)
     * @see ResourceUtils#create(Object)
     */
    public static final ResourceUtils create(BrooklynClassLoadingContext loader, Object contextObject, String contextMessage) {
        return new ResourceUtils(loader, contextObject, contextMessage);
    }

    /**
     * Creates a {@link ResourceUtils} object with the given context.
     * <p>
     * Uses the {@link ClassLoader} of the given {@code contextObject} for class
     * loading and the {@code contextMessage} string for error messages.
     *
     * @see ResourceUtils#create(ClassLoader, Object, String)
     * @see ResourceUtils#create(Object)
     */
    public static final ResourceUtils create(Object contextObject, String contextMessage) {
        return new ResourceUtils(contextObject, contextMessage);
    }

    /**
     * Creates a {@link ResourceUtils} object with the given context.
     * <p>
     * Uses the {@link ClassLoader} of the given {@code contextObject} for class
     * loading and its {@link Object#toString()} (preceded by the word 'for') as
     * the string used in error messages.
     *
     * @see ResourceUtils#create(ClassLoader, Object, String)
     * @see ResourceUtils#create(Object)
     */
    public static final ResourceUtils create(Object contextObject) {
        return new ResourceUtils(contextObject);
    }

    /**
     * Creates a {@link ResourceUtils} object with itself as the context.
     *
     * @see ResourceUtils#create(Object)
     */
    public static final ResourceUtils create() {
        return new ResourceUtils(null);
    }

    public ResourceUtils(ClassLoader loader, Object contextObject, String contextMessage) {
        this(getClassLoadingContextInternal(loader, contextObject), contextObject, contextMessage);
    }
    
    public ResourceUtils(BrooklynClassLoadingContext loader, Object contextObject, String contextMessage) {
        this.loader = loader;
        this.contextObject = contextObject;
        this.context = contextMessage;
    }

    public ResourceUtils(Object contextObject, String contextMessage) {
        this(contextObject==null ? null : getClassLoadingContextInternal(null, contextObject), contextObject, contextMessage);
    }

    public ResourceUtils(Object contextObject) {
        this(contextObject, Strings.toString(contextObject));
    }
    
    /** used to register custom mechanisms for getting classloaders given an object */
    public static void addClassLoaderProvider(Function<Object,BrooklynClassLoadingContext> provider) {
        classLoaderProviders.add(provider);
    }
    
    // TODO rework this class so it accepts but does not require a BCLC ?
    @SuppressWarnings("deprecation")
    protected static BrooklynClassLoadingContext getClassLoadingContextInternal(ClassLoader loader, Object contextObject) {
        if (contextObject instanceof BrooklynClassLoadingContext)
            return (BrooklynClassLoadingContext) contextObject;
        
        for (Function<Object,BrooklynClassLoadingContext> provider: classLoaderProviders) {
            BrooklynClassLoadingContext result = provider.apply(contextObject);
            if (result!=null) return result;
        }

        BrooklynClassLoadingContext bl = BrooklynLoaderTracker.getLoader();
        ManagementContext mgmt = (bl!=null ? bl.getManagementContext() : null);

        ClassLoader cl = loader;
        if (cl==null) cl = contextObject instanceof Class ? ((Class<?>)contextObject).getClassLoader() : 
            contextObject instanceof ClassLoader ? ((ClassLoader)contextObject) : 
                contextObject.getClass().getClassLoader();
            
        return JavaBrooklynClassLoadingContext.create(mgmt, cl);
    }
    
    /** This should not be exposed as it risks it leaking into places where it would be serialized.
     * Better for callers use {@link CatalogUtils#getClassLoadingContext(org.apache.brooklyn.api.entity.Entity)} or similar. }.
     */
    private BrooklynClassLoadingContext getLoader() {
        return (loader!=null ? loader : getClassLoadingContextInternal(null, contextObject!=null ? contextObject : this));
    }

    /**
     * @return all resources in Brooklyn's {@link BrooklynClassLoadingContext} with the given name.
     */
    public Iterable<URL> getResources(String name) {
        return getLoader().getResources(name);
    }
    
    /**
     * Takes a string which is treated as a URL (with some extended "schemes" also expected),
     * or as a path to something either on the classpath (absolute only) or the local filesystem (relative or absolute, depending on leading slash)
     * <p>
     * URLs can be of the form <b>classpath://com/acme/Foo.properties</b>
     * as well as <b>file:///home/...</b> and <b>http://acme.com/...</b>.
     * <p>
     * Throws exception if not found, using the context parameter passed into the constructor.
     * <p>
     * TODO may want OSGi, or typed object; should consider pax url
     * 
     * @return a stream, or throws exception (never returns null)
     */
    public InputStream getResourceFromUrl(String url) {
        try {
            if (Strings.isBlank(url)) throw new IllegalArgumentException("Cannot read from empty string");
            String orig = url;
            String protocol = Urls.getProtocol(url);
            if (protocol!=null) {
                if ("classpath".equals(protocol)) {
                    try {
                        return getResourceViaClasspath(url);
                    } catch (IOException e) {
                        //catch the above because both orig and modified url may be interesting
                        throw new IOException("Error accessing "+orig+": "+e, e);
                    }
                }
                if ("sftp".equals(protocol)) {
                    try {
                        return getResourceViaSftp(url);
                    } catch (IOException e) {
                        throw new IOException("Error accessing "+orig+": "+e, e);
                    }
                }

                if ("file".equals(protocol))
                    url = tidyFileUrl(url);
                
                if ("data".equals(protocol)) {
                    return new DataUriSchemeParser(url).lax().parse().getDataAsInputStream();
                }

                if ("http".equals(protocol) || "https".equals(protocol)) {
                    return getResourceViaHttp(url);
                }

                return new URL(url).openStream();
            }

            try {
                //try as classpath reference, then as file
                try {
                    URL u = getLoader().getResource(url);
                    if (u!=null) return u.openStream();
                } catch (IllegalArgumentException e) {
                    //Felix installs an additional URL to the system classloader
                    //which throws an IllegalArgumentException when passed a
                    //windows path. See ExtensionManager.java static initializer.

                    //ignore, not a classpath resource
                }
                if (url.startsWith("/")) {
                    //some getResource calls fail if argument starts with /
                    String urlNoSlash = url;
                    while (urlNoSlash.startsWith("/")) urlNoSlash = urlNoSlash.substring(1);
                    URL u = getLoader().getResource(urlNoSlash);
                    if (u!=null) return u.openStream();
//                    //Class.getResource can require a /  (else it attempts to be relative) but Class.getClassLoader doesn't
//                    u = getLoader().getResource("/"+urlNoSlash);
//                    if (u!=null) return u.openStream();
                }
                File f;
                // but first, if it starts with tilde, treat specially
                if (url.startsWith("~/")) {
                    f = new File(Os.home(), url.substring(2));
                } else if (url.startsWith("~\\")) {
                    f = new File(Os.home(), url.substring(2));
                } else {
                    f = new File(url);
                }
                if (f.exists()) return new FileInputStream(f);
            } catch (IOException e) {
                //catch the above because both u and modified url will be interesting
                throw new IOException("Error accessing "+orig+": "+e, e);
            }
            throw new IOException("'"+orig+"' not found on classpath or filesystem");
        } catch (Exception e) {
            if (context!=null) {
                throw new RuntimeException("Error getting resource '"+url+"' for "+context+": "+e, e);
            } else {
                throw Exceptions.propagate(e);
            }
        }
    }
    
    private final static Pattern pattern = Pattern.compile("^file:/*~/+(.*)$");

    public static URL tidy(URL url) {
        // File class has helpful methods for URIs but not URLs. So we convert.
        URI in;
        try {
            in = url.toURI();
        } catch (URISyntaxException e) {
            throw Exceptions.propagate(e);
        }
        URI out;

        Matcher matcher = pattern.matcher(in.toString());
        if (matcher.matches()) {
            // home-relative
            File home = new File(Os.home());
            File file = new File(home, matcher.group(1));
            out = file.toURI();
        } else if (in.getScheme().equals("file:")) {
            // some other file, so canonicalize
            File file = new File(in);
            out = file.toURI();
        } else {
            // some other scheme, so no-op
            out = in;
        }

        URL urlOut;
        try {
            urlOut = out.toURL();
        } catch (MalformedURLException e) {
            throw Exceptions.propagate(e);
        }
        if (!urlOut.equals(url) && log.isDebugEnabled()) {
            log.debug("quietly changing " + url + " to " + urlOut);
        }
        return urlOut;
    }
    
    public static String tidyFileUrl(String url) {
        try {
            return tidy(new URL(url)).toString();
        } catch (MalformedURLException e) {
            throw Exceptions.propagate(e);
        }
    }

    /** @deprecated since 0.7.0; use method {@link Os#mergePaths(String...)} */ @Deprecated
    public static String mergeFilePaths(String... items) {
        return Os.mergePaths(items);
    }
    
    /** @deprecated since 0.7.0; use method {@link Os#tidyPath(String)} */ @Deprecated
    public static String tidyFilePath(String path) {
        return Os.tidyPath(path);
    }
    
    /** @deprecated since 0.7.0; use method {@link Urls#getProtocol(String)} */ @Deprecated
    public static String getProtocol(String url) {
        return Urls.getProtocol(url);
    }
    
    private InputStream getResourceViaClasspath(String url) throws IOException {
        assert url.startsWith("classpath:");
        String subUrl = url.substring("classpath:".length());
        while (subUrl.startsWith("/")) subUrl = subUrl.substring(1);
        URL u = getLoader().getResource(subUrl);
        if (u!=null) return u.openStream();
        else throw new IOException(subUrl+" not found on classpath");
    }
    
    private InputStream getResourceViaSftp(String url) throws IOException {
        assert url.startsWith("sftp://");
        String subUrl = url.substring("sftp://".length());
        String user;
        String address;
        String path;
        int atIndex = subUrl.indexOf("@");
        int colonIndex = subUrl.indexOf(":", (atIndex > 0 ? atIndex : 0));
        if (colonIndex <= 0 || colonIndex <= atIndex) {
            throw new IllegalArgumentException("Invalid sftp url ("+url+"); IP or hostname must be specified, such as sftp://localhost:/path/to/file");
        }
        if (subUrl.length() <= (colonIndex+1)) {
            throw new IllegalArgumentException("Invalid sftp url ("+url+"); must specify path of remote file, such as sftp://localhost:/path/to/file");
        }
        if (atIndex >= 0) {
            user = subUrl.substring(0, atIndex);
        } else {
            user = null;
        }
        address = subUrl.substring(atIndex + 1, colonIndex);
        path = subUrl.substring(colonIndex+1);
        
        // TODO messy way to get an SCP session 
        SshMachineLocation machine = new SshMachineLocation(MutableMap.builder()
                .putIfNotNull("user", user)
                .put("address", InetAddress.getByName(address))
                .build());
        try {
            final File tempFile = Os.newTempFile("brooklyn-sftp", "tmp");
            tempFile.setReadable(true, true);
            machine.copyFrom(path, tempFile.getAbsolutePath());
            return new FileInputStream(tempFile) {
                @Override
                public void close() throws IOException {
                    super.close();
                    tempFile.delete();
                }
            };
        } finally {
            Streams.closeQuietly(machine);
        }
    }
    
    //For HTTP(S) targets use HttpClient so
    //we can do authentication
    private InputStream getResourceViaHttp(String resource) throws IOException {
        URI uri = URI.create(resource);
        HttpClientBuilder builder = HttpTool.httpClientBuilder()
                .laxRedirect(true)
                .uri(uri);
        Credentials credentials = getUrlCredentials(uri.getRawUserInfo());
        if (credentials != null) {
            builder.credentials(credentials);
        }
        HttpClient client = builder.build();
        HttpResponse result = client.execute(new HttpGet(resource));
        int statusCode = result.getStatusLine().getStatusCode();
        if (HttpTool.isStatusCodeHealthy(statusCode)) {
            HttpEntity entity = result.getEntity();
            if (entity != null) {
                return entity.getContent();
            } else {
                return new ByteArrayInputStream(new byte[0]);
            }
        } else {
            EntityUtils.consume(result.getEntity());
            throw new IllegalStateException("Invalid response invoking " + resource + ": response code " + statusCode);
        }
    }

    private Credentials getUrlCredentials(String userInfo) {
        if (userInfo != null) {
            String[] arr = userInfo.split(":");
            String username;
            String password = null;
            if (arr.length == 1) {
                username = urlDecode(arr[0]);
            } else if (arr.length == 2) {
                username = urlDecode(arr[0]);
                password = urlDecode(arr[1]);
            } else {
                return null;
            }
            return new UsernamePasswordCredentials(username, password);
        } else {
            return null;
        }
    }

    private String urlDecode(String str) {
        try {
            return URLDecoder.decode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw Exceptions.propagate(e);
        }
    }

    /** takes {@link #getResourceFromUrl(String)} and reads fully, into a string */
    public String getResourceAsString(String url) {
        try {
            return readFullyString(getResourceFromUrl(url));
        } catch (Exception e) {
            log.debug("ResourceUtils got error reading "+url+(context==null?"":" "+context)+" (rethrowing): "+e);
            throw Throwables.propagate(e);
        }
    }

    /** @see #checkUrlExists(String, String) */
    public String checkUrlExists(String url) {
        return checkUrlExists(url, null);
    }

    /**
     * Throws if the given URL cannot be read.
     * @param url The URL to test
     * @param message An optional message to use for the exception thrown.
     * @return The URL argument
     * @see #getResourceFromUrl(String)
     */
    public String checkUrlExists(String url, String message) {
        if (url==null) throw new NullPointerException("URL "+(message!=null ? message+" " : "")+"must not be null");
        InputStream s = null;
        try {
            s = getResourceFromUrl(url);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalArgumentException("Unable to access URL "+(message!=null ? message : "")+": "+url, e);
        } finally {
            Streams.closeQuietly(s);
        }
        return url;
    }

    /**
     * @return True if the given URL can be read, false otherwise.
     * @see #getResourceFromUrl(String)
     */
     public boolean doesUrlExist(String url) {
        InputStream s = null;
        try {
            s = getResourceFromUrl(url);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            Streams.closeQuietly(s);
        }
    }
    
    /** returns the first available URL */
    public Optional<String> firstAvailableUrl(String ...urls) {
        for (String url: urls) {
            if (doesUrlExist(url)) return Optional.of(url);
        }
        return Optional.absent();
    }
    
    /** returns the base directory or JAR from which the context is class-loaded, if possible;
     * throws exception if not found */
    public String getClassLoaderDir() {
        if (contextObject==null) throw new IllegalArgumentException("No suitable context ("+context+") to auto-detect classloader dir");
        Class<?> cc = contextObject instanceof Class ? (Class<?>)contextObject : contextObject.getClass();
        return getClassLoaderDir(cc.getCanonicalName().replace('.', '/')+".class");
    }
    
    public String getClassLoaderDir(String resourceInThatDir) {
        resourceInThatDir = Strings.removeFromStart(resourceInThatDir, "/");
        URL resourceUrl = getLoader().getResource(resourceInThatDir);
        if (resourceUrl==null) throw new NoSuchElementException("Resource ("+resourceInThatDir+") not found");

        URL containerUrl = getContainerUrl(resourceUrl, resourceInThatDir);

        if (!"file".equals(containerUrl.getProtocol())) throw new IllegalStateException("Resource ("+resourceInThatDir+") not on file system (at "+containerUrl+")");

        //convert from file: URL to File
        File file;
        try {
            file = new File(containerUrl.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Resource ("+resourceInThatDir+") found at invalid URI (" + containerUrl + ")", e);
        }
        
        if (!file.exists()) throw new IllegalStateException("Context class url substring ("+containerUrl+") not found on filesystem");
        return file.getPath();
        
    }

    public static URL getContainerUrl(URL url, String resourceInThatDir) {
        return OsgiUtils.getContainerUrl(url, resourceInThatDir);
    }
    
    /** @deprecated since 0.7.0 use {@link Streams#readFullyString(InputStream) */ @Deprecated
    public static String readFullyString(InputStream is) throws IOException {
        return Streams.readFullyString(is);
    }

    /** @deprecated since 0.7.0 use {@link Streams#readFully(InputStream) */ @Deprecated
    public static byte[] readFullyBytes(InputStream is) throws IOException {
        return Streams.readFully(is);
    }
    
    /** @deprecated since 0.7.0 use {@link Streams#copy(InputStream, OutputStream)} */ @Deprecated
    public static void copy(InputStream input, OutputStream output) throws IOException {
        Streams.copy(input, output);
    }

    /** @deprecated since 0.7.0; use same method in {@link Os} */ @Deprecated
    public static File mkdirs(File dir) {
        return Os.mkdirs(dir);
    }

    /** @deprecated since 0.7.0; use same method in {@link Os} */ @Deprecated
    public static File writeToTempFile(InputStream is, String prefix, String suffix) {
        return Os.writeToTempFile(is, prefix, suffix);
    }
    
    /** @deprecated since 0.7.0; use same method in {@link Os} */ @Deprecated
    public static File writeToTempFile(InputStream is, File tempDir, String prefix, String suffix) {
        return Os.writeToTempFile(is, tempDir, prefix, suffix);
    }

    /** @deprecated since 0.7.0; use method {@link Os#writePropertiesToTempFile(Properties, String, String)} */ @Deprecated
    public static File writeToTempFile(Properties props, String prefix, String suffix) {
        return Os.writePropertiesToTempFile(props, prefix, suffix);
    }
    
    /** @deprecated since 0.7.0; use method {@link Os#writePropertiesToTempFile(Properties, File, String, String)} */ @Deprecated
    public static File writeToTempFile(Properties props, File tempDir, String prefix, String suffix) {
        return Os.writePropertiesToTempFile(props, tempDir, prefix, suffix);
    }

    /** @deprecated since 0.7.0; use method {@link Threads#addShutdownHook(Runnable)} */ @Deprecated
    public static Thread addShutdownHook(final Runnable task) {
        return Threads.addShutdownHook(task);
    }
    /** @deprecated since 0.7.0; use method {@link Threads#removeShutdownHook(Thread)} */ @Deprecated
    public static boolean removeShutdownHook(Thread hook) {
        return Threads.removeShutdownHook(hook);
    }

    /** returns the items with exactly one "/" between items (whether or not the individual items start or end with /),
     * except where character before the / is a : (url syntax) in which case it will permit multiple (will not remove any) 
     * @deprecated since 0.7.0 use either {@link Os#mergePathsUnix(String...)} {@link Urls#mergePaths(String...) */ @Deprecated
    public static String mergePaths(String ...items) {
        return Urls.mergePaths(items);
    }
}
