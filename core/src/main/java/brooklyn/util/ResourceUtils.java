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
package brooklyn.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.internal.BasicBrooklynCatalog.BrooklynLoaderTracker;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.Threads;
import brooklyn.util.net.Networking;
import brooklyn.util.net.Urls;
import brooklyn.util.os.Os;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.DataUriSchemeParser;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

public class ResourceUtils {
    
    private static final Logger log = LoggerFactory.getLogger(ResourceUtils.class);
    private static final List<Function<Object,BrooklynClassLoadingContext>> classLoaderProviders = Lists.newCopyOnWriteArrayList();

    private BrooklynClassLoadingContext loader = null;
    private String context = null;
    private Object contextObject = null;
    
    static { Networking.init(); }
    
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
        this(new JavaBrooklynClassLoadingContext(null, loader), contextObject, contextMessage);
    }
    
    public ResourceUtils(BrooklynClassLoadingContext loader, Object contextObject, String contextMessage) {
        this.loader = loader;
        this.contextObject = contextObject;
        this.context = contextMessage;
    }

    public ResourceUtils(Object contextObject, String contextMessage) {
        this(contextObject==null ? null : getClassLoadingContextForObject(contextObject), contextObject, contextMessage);
    }

    public ResourceUtils(Object contextObject) {
        this(contextObject, Strings.toString(contextObject));
    }
    
    /** used to register custom mechanisms for getting classloaders given an object */
    public static void addClassLoaderProvider(Function<Object,BrooklynClassLoadingContext> provider) {
        classLoaderProviders.add(provider);
    }
    
    public static BrooklynClassLoadingContext getClassLoadingContextForObject(Object contextObject) {
        if (contextObject instanceof BrooklynClassLoadingContext)
            return (BrooklynClassLoadingContext) contextObject;
        
        for (Function<Object,BrooklynClassLoadingContext> provider: classLoaderProviders) {
            BrooklynClassLoadingContext result = provider.apply(contextObject);
            if (result!=null) return result;
        }
        
        ClassLoader cl = contextObject instanceof Class ? ((Class<?>)contextObject).getClassLoader() : 
            contextObject instanceof ClassLoader ? ((ClassLoader)contextObject) : 
                contextObject.getClass().getClassLoader();
        return getClassLoadingContextForClassLoader(cl);
    }
    
    protected static BrooklynClassLoadingContext getClassLoadingContextForClassLoader(ClassLoader loader) {
        ManagementContext mgmt = null;
        BrooklynClassLoadingContext bl = BrooklynLoaderTracker.getLoader();
        if (bl!=null) mgmt = bl.getManagementContext();
        return new JavaBrooklynClassLoadingContext(mgmt, loader);
    }
    
    public BrooklynClassLoadingContext getLoader() {
        return (loader!=null ? loader : getClassLoadingContextForClassLoader(getClass().getClassLoader()));
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
            if (url==null) throw new NullPointerException("Cannot read from null");
            if (url=="") throw new NullPointerException("Cannot read from empty string");
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
        if (!urlOut.equals(in) && log.isDebugEnabled()) {
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
    
    /** takes {@link #getResourceFromUrl(String)} and reads fully, into a string */
    public String getResourceAsString(String url) {
        try {
            return readFullyString(getResourceFromUrl(url));
        } catch (Exception e) {
            log.debug("ResourceUtils got error reading "+url+(context==null?"":" "+context)+" (rethrowing): "+e);
            throw Throwables.propagate(e);
        }
    }

    /** allows failing-fast if URL cannot be read */
    public String checkUrlExists(String url) {
        if (url==null) throw new NullPointerException("URL must not be null");
        InputStream s;
        try {
            s = getResourceFromUrl(url);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalArgumentException("Unable to access URL "+url, e);
        }
        Streams.closeQuietly(s); 
        return url;
    }

    /** tests whether the url exists, returning true or false */
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
        //Switching from manual parsing of jar: and file: URLs to java provided functionality.
        //The old code was breaking on any Windows path and instead of fixing it, using
        //the provided Java APIs seemed like the better option since they are already tested
        //on multiple platforms.
        boolean isJar = "jar".equals(url.getProtocol());
        if(isJar) {
            try {
                //let java handle the parsing of jar URL, no network connection is established.
                //Strips the jar protocol:
                //  jar:file:/<path to jar>!<resourceInThatDir>
                //  becomes
                //  file:/<path to jar>
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                url = connection.getJarFileURL();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            //Remove the trailing resouceInThatDir path from the URL, thus getting the parent folder.
            String path = url.toString();
            int i = path.indexOf(resourceInThatDir);
            if (i==-1) throw new IllegalStateException("Resource path ("+resourceInThatDir+") not in url substring ("+url+")");
            String parent = path.substring(0, i);
            try {
                url = new URL(parent);
            } catch (MalformedURLException e) {
                throw new IllegalStateException("Resource ("+resourceInThatDir+") found at invalid URL parent (" + parent + ")", e);
            }
        }
        return url;
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
