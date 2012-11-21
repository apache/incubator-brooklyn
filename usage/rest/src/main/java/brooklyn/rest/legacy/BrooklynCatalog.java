package brooklyn.rest.legacy;

import groovy.lang.GroovyClassLoader;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.scanners.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.rest.resources.CatalogResource;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class BrooklynCatalog {

    private static final Logger log = LoggerFactory.getLogger(CatalogResource.class);
    
  private volatile boolean scanNeeded = true;
  private Map<String, Class<? extends AbstractEntity>> scannedEntities = Collections.emptyMap();
  private final Map<String, Class<? extends AbstractEntity>> registeredEntities = Maps.newConcurrentMap();
  private Map<String, Class<? extends AbstractPolicy>> scannedPolicies = Collections.emptyMap();
  private final Map<String, Class<? extends AbstractPolicy>> registeredPolicies = Maps.newConcurrentMap();

  private synchronized void scanIfNeeded() {
      // defer expensive scans, particularly for unit tests
      if (scanNeeded==false) return;
      scanNeeded = false;
      // TODO allow other prefixes to be supplied?
      scannedEntities = buildMapOfSubTypesOf("brooklyn", AbstractEntity.class);
      scannedPolicies = buildMapOfSubTypesOf("brooklyn", AbstractPolicy.class);
  }
  
  private <T> Map<String, Class<? extends T>> buildMapOfSubTypesOf(String prefix, Class<T> clazz) {
    Reflections reflections = new SafeReflections(prefix);
    ImmutableMap.Builder<String, Class<? extends T>> builder = ImmutableMap.builder();
    for (Class<? extends T> candidate : reflections.getSubTypesOf(clazz)) {
      if (!Modifier.isAbstract(candidate.getModifiers()) &&
          !candidate.isInterface() &&
          !candidate.isAnonymousClass()) {
        builder.put(candidate.getName(), candidate);
      }
    }
    return builder.build();
  }

  public boolean containsEntity(String entityName) {
    if (registeredEntities.containsKey(entityName)) return true;
    if (scanNeeded) {
        // test early to avoid scan
        if (forName(entityName, false)!=null) return true;
    }
    scanIfNeeded();
    if (scannedEntities.containsKey(entityName)) return true;
    return false;
  }

  public boolean containsPolicy(String policyName) {
      if (registeredPolicies.containsKey(policyName)) return true;
      if (scanNeeded) {
          // test early to avoid scan
          if (forName(policyName, false)!=null) return true;
      }
      scanIfNeeded();
      if (scannedPolicies.containsKey(policyName)) return true;
      return false;
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<? extends T> forName(String className, boolean required) {
    try {
        return (Class<? extends T>) Class.forName(className);
    } catch (ClassNotFoundException e) {
        if (required) throw Exceptions.propagate(e);
        return null;
    }
  }
  
  public Class<? extends AbstractEntity> getEntityClass(String entityTypeName) {
    Class<? extends AbstractEntity> result = registeredEntities.get(entityTypeName);
    if (result!=null) return result;
    result = forName(entityTypeName, false);
    if (result!=null) return result;
    scanIfNeeded();
    result = scannedEntities.get(entityTypeName);
    if (result!=null) return result;
    throw new NoSuchElementException("No entity class "+entityTypeName);
  }

  public Class<? extends AbstractPolicy> getPolicyClass(String policyTypeName) {
      Class<? extends AbstractPolicy> result = registeredPolicies.get(policyTypeName);
      if (result!=null) return result;
      result = forName(policyTypeName, false);
      if (result!=null) return result;
      scanIfNeeded();
      result = scannedPolicies.get(policyTypeName);
      if (result!=null) return result;
      throw new NoSuchElementException("No policy class "+policyTypeName);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
public Response createFromGroovyCode(String groovyCode) {
    ClassLoader parent = getClass().getClassLoader();
    GroovyClassLoader loader = new GroovyClassLoader(parent);

    Class clazz = loader.parseClass(groovyCode);

    if (AbstractEntity.class.isAssignableFrom(clazz)) {
      registeredEntities.put(clazz.getName(), clazz);
      return Response.created(URI.create("entities/" + clazz.getName())).build();

    } else if (AbstractPolicy.class.isAssignableFrom(clazz)) {
      registeredPolicies.put(clazz.getName(), clazz);
      return Response.created(URI.create("policies/" + clazz.getName())).build();
    }

    throw WebResourceUtils.preconditionFailed("Unsupported type superclass "+clazz.getSuperclass()+"; expects Entity or Policy");
  }
  
  public Iterable<String> listEntitiesMatching(String name, boolean onlyApps) {
      scanIfNeeded();
      List<String> result = new ArrayList<String>();
      result.addAll(registeredEntities.keySet());
      result.addAll(scannedEntities.keySet());
      if (name!=null && !name.isEmpty()) {
        final String normalizedName = name.toLowerCase();
        Iterator<String> ri = result.iterator();
        while (ri.hasNext()) {
            if (!ri.next().toLowerCase().contains(normalizedName)) ri.remove();
        }
      }
      if (onlyApps) {
          Iterator<String> ri = result.iterator();
          while (ri.hasNext()) {
              Class<? extends AbstractEntity> type = getEntityClass(ri.next());
              if (!Application.class.isAssignableFrom(type)) ri.remove();
          }
      }
      Collections.sort(result);
      return result;
  }

  public Iterable<String> listPolicies() {
      scanIfNeeded();
      List<String> result = new ArrayList<String>();
      result.addAll(registeredPolicies.keySet());
      result.addAll(scannedPolicies.keySet());
      Collections.sort(result);
      return result;
  }

  public static class SafeReflections extends Reflections {
      public SafeReflections(final String prefix, final Scanner... scanners) {
          super(prefix, scanners);
      }
      @SuppressWarnings("unchecked")
      public <T> Set<Class<? extends T>> getSubTypesOf(final Class<T> type) {
          Set<String> subTypes = getStore().getSubTypesOf(type.getName());
          List<Class<? extends T>> result = new ArrayList<Class<? extends T>>();
          for (String className : subTypes) {
              //noinspection unchecked
              try {
                  result.add((Class<? extends T>) ReflectionUtils.forName(className));
              } catch (Throwable e) {
                  log.warn("Unable to instantiate '"+className+"': "+e);
              }
          }
          return ImmutableSet.copyOf(result);
      }
  }

}
