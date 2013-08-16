package brooklyn.util.javalang;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.scanners.Scanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.text.Strings;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;

public class ReflectionScanner extends Reflections {

    private static final Logger log = LoggerFactory.getLogger(ReflectionScanner.class);
    
    protected final ClassLoader classLoaderForLoading;

    /** scanner which will look in given "search" classloader for classes starting with given prefix, 
     * loading the class with the given "load" classloader,
     * selecting for the given scanners.
     * any or all arguments can be null to accept all.
     **/
    public ReflectionScanner(final ClassLoader classLoaderToSearch, final ClassLoader classLoaderForLoading, 
            final Iterable<URL> urlsToScan, 
            final String prefix, final Scanner... scanners) {
        super(new ConfigurationBuilder() {
            {
                final Predicate<String> filter = 
                        Strings.isNonEmpty(prefix) ? new FilterBuilder.Include(FilterBuilder.prefix(prefix)) : null;

                setUrls(urlsToScan != null ? ImmutableSet.copyOf(urlsToScan) :
                    ClasspathHelper.forPackage(prefix != null && prefix.length()>0 ? prefix : "", 
                        asClassLoaderVarArgs(classLoaderToSearch)));
                if (filter!=null) filterInputsBy(filter);

                if (scanners != null && scanners.length != 0) {
                    for (Scanner scanner : scanners) {
                        if (filter!=null)
                            scanner.filterResultsBy(filter);
                    }
                    setScanners(scanners);
                } else {
                    Scanner typeScanner = new TypeAnnotationsScanner();
                    if (filter!=null) typeScanner = typeScanner.filterResultsBy(filter);
                    Scanner subTypeScanner = new SubTypesScanner();
                    if (filter!=null) subTypeScanner = subTypeScanner.filterResultsBy(filter);
                    setScanners(typeScanner, subTypeScanner);
                }
            }
        });
        this.classLoaderForLoading = classLoaderForLoading;
    }

    private static ClassLoader[] asClassLoaderVarArgs(final ClassLoader classLoaderToSearch) {
        return classLoaderToSearch==null ? new ClassLoader[0] : new ClassLoader[] { classLoaderToSearch };
    }

    /** overrides super so as to log rather than throw exception if a class cannot be loaded */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Set<Class<? extends T>> getSubTypesOf(final Class<T> type) {
        Set<String> subTypes = getStore().getSubTypesOf(type.getName());
        List<Class<? extends T>> result = new ArrayList<Class<? extends T>>();
        for (String className : subTypes) {
            //noinspection unchecked
            try {
                Class<? extends T> subClazz = (Class<? extends T>) loadClass(className);
                if (subClazz != null) {
                    result.add(subClazz);
                } else {
                    log.warn("Unable to instantiate '"+className+"' (sub-type of "+type+")");
                }
            } catch (Throwable e) {
                log.warn("Unable to instantiate '"+className+"' (sub-type of "+type+"): "+e);
            }
        }
        return ImmutableSet.copyOf(result);
    }

    protected Class<?> loadClass(String className) {
        return ReflectionUtils.forName(className, asClassLoaderVarArgs(classLoaderForLoading));
    }

}
