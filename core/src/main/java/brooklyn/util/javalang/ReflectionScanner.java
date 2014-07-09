package brooklyn.util.javalang;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.Store;
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
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/** Facade on {@link Reflections} which logs warnings for unloadable classes but does not fail */
public class ReflectionScanner {

    private static final Logger log = LoggerFactory.getLogger(ReflectionScanner.class);
    
    protected final ClassLoader[] classLoaders;
    protected final Reflections reflections;

    /** scanner which will look in the given urls 
     * (or if those are null attempt to infer from the first entry in the classloaders,
     * although currently that seems to only pick up directories, not JAR's),
     * optionally filtering for the given prefix;
     * any or all arguments can be null to accept all (and use default classpath for classloading).
     **/
    public ReflectionScanner(
            final Iterable<URL> urlsToScan, 
            final String optionalPrefix,
            final ClassLoader ...classLoaders) {
        reflections = new Reflections(new ConfigurationBuilder() {
            {
                final Predicate<String> filter = 
                        Strings.isNonEmpty(optionalPrefix) ? new FilterBuilder.Include(FilterBuilder.prefix(optionalPrefix)) : null;

                if (urlsToScan!=null)
                    setUrls(ImmutableSet.copyOf(urlsToScan));
                else if (classLoaders.length>0 && classLoaders[0]!=null)
                    setUrls(
                            ClasspathHelper.forPackage(Strings.isNonEmpty(optionalPrefix) ? optionalPrefix : "",
                                    asClassLoaderVarArgs(classLoaders[0])));
                
                if (filter!=null) filterInputsBy(filter);

                Scanner typeScanner = new TypeAnnotationsScanner();
                if (filter!=null) typeScanner = typeScanner.filterResultsBy(filter);
                Scanner subTypeScanner = new SubTypesScanner();
                if (filter!=null) subTypeScanner = subTypeScanner.filterResultsBy(filter);
                setScanners(typeScanner, subTypeScanner);
                
                for (ClassLoader cl: classLoaders)
                    if (cl!=null) addClassLoader(cl);
            }
        });
        this.classLoaders = Iterables.toArray(Iterables.filter(Arrays.asList(classLoaders), Predicates.notNull()), ClassLoader.class);
    }

    private static ClassLoader[] asClassLoaderVarArgs(final ClassLoader classLoaderToSearch) {
        return classLoaderToSearch==null ? new ClassLoader[0] : new ClassLoader[] { classLoaderToSearch };
    }

    public Store getStore() {
        return reflections.getStore();
    }
    
    /** overrides delegate so as to log rather than throw exception if a class cannot be loaded */
    public <T> Set<Class<? extends T>> getSubTypesOf(final Class<T> type) {
        Set<String> subTypes = getStore().getSubTypesOf(type.getName());
        return ImmutableSet.copyOf(this.<T>forNames(subTypes, "sub-type of "+type));
    }
    
    /** overrides delegate so as to log rather than throw exception if a class cannot be loaded */
    public Set<Class<?>> getTypesAnnotatedWith(Class<? extends Annotation> annotation) {
        Set<String> annotatedWith = getStore().getTypesAnnotatedWith(annotation.getName());
        return ImmutableSet.copyOf(this.forNames(annotatedWith, "annotated "+annotation.getName()));
    }

    @SuppressWarnings("unchecked")
    protected <T> List<Class<? extends T>> forNames(Set<String> classNames, final String context) {
        List<Class<? extends T>> result = new ArrayList<Class<? extends T>>();
        for (String className : classNames) {
            //noinspection unchecked
            try {
                Class<? extends T> clazz = (Class<? extends T>) loadClass(className);
                if (clazz != null) {
                    result.add(clazz);
                } else {
                    log.warn("Unable to instantiate '"+className+"' ("+context+")");
                }
            } catch (Throwable e) {
                log.warn("Unable to instantiate '"+className+"' ("+context+"): "+e);
            }
        }
        return result;
    }
    
    protected Class<?> loadClass(String className) {
        return ReflectionUtils.forName(className, classLoaders);
    }

}
