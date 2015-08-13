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
package brooklyn.entity.drivers;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.drivers.DriverDependentEntity;
import org.apache.brooklyn.api.entity.drivers.EntityDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.brooklyn.location.Location;
import org.apache.brooklyn.location.basic.SshMachineLocation;
import org.apache.brooklyn.location.paas.PaasLocation;
import org.apache.brooklyn.location.basic.WinRmMachineLocation;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.ReferenceWithError;
import brooklyn.util.text.Strings;

/**
 * Follows a class naming convention: the driver interface typically ends in "Driver", and the implementation 
 * must match the driver interface name but with a suffix like "SshDriver" instead of "Driver".
 * Other rules can be added using {@link #addRule(String, DriverInferenceRule)} or
 * {@link #addClassFullNameMapping(String, String)}.
 * <p>
 * Reflectively instantiates and returns the driver, based on the location passed in,
 * in {@link #build(DriverDependentEntity, Location)}.
 * 
 * @author Peter Veentjer, Alex Heneveld
 */
public class ReflectiveEntityDriverFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ReflectiveEntityDriverFactory.class);

    /** Rules, keyed by a unique identifier.  Executed in order of most-recently added first. */
    protected final Map<String,DriverInferenceRule> rules = MutableMap.of();
    
    public ReflectiveEntityDriverFactory() {
        addRule(DriverInferenceForSshLocation.DEFAULT_IDENTIFIER, new DriverInferenceForSshLocation());
        addRule(DriverInferenceForPaasLocation.DEFAULT_IDENTIFIER, new DriverInferenceForPaasLocation());
        addRule(DriverInferenceForWinRmLocation.DEFAULT_IDENTIFIER, new DriverInferenceForWinRmLocation());
    }
    
    public interface DriverInferenceRule {
        public <D extends EntityDriver> ReferenceWithError<Class<? extends D>> resolve(DriverDependentEntity<D> entity, Class<D> driverInterface, Location location);
    }

    public static abstract class AbstractDriverInferenceRule implements DriverInferenceRule {

        @Override
        public <D extends EntityDriver> ReferenceWithError<Class<? extends D>> resolve(DriverDependentEntity<D> entity, Class<D> driverInterface, Location location) {
            try {
                String newName = inferDriverClassName(entity, driverInterface, location);
                if (newName==null) return null;

                return loadDriverClass(newName, entity, driverInterface);
                
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                return ReferenceWithError.newInstanceThrowingError(null, e);
            }
        }

        public abstract <D extends EntityDriver> String inferDriverClassName(DriverDependentEntity<D> entity, Class<D> driverInterface, Location location);

        protected <D extends EntityDriver> ReferenceWithError<Class<? extends D>> loadDriverClass(String className, DriverDependentEntity<D> entity, Class<D> driverInterface) {
            ReferenceWithError<Class<? extends D>> r1 = loadClass(className, entity.getClass().getClassLoader());
            if (!r1.hasError()) return r1;
            ReferenceWithError<Class<? extends D>> r2 = loadClass(className, driverInterface.getClass().getClassLoader());
            if (!r2.hasError()) return r2;
            return r1;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        protected <D extends EntityDriver> ReferenceWithError<Class<? extends D>> loadClass(String className, ClassLoader classLoader) {
            try {
                return (ReferenceWithError<Class<? extends D>>)(ReferenceWithError) ReferenceWithError.newInstanceWithoutError((Class<? extends EntityDriver>)classLoader.loadClass(className));
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                return ReferenceWithError.newInstanceThrowingError(null, e);
            }
        }
    }
    
    public static abstract class AbstractDriverInferenceRenamingInferenceRule extends AbstractDriverInferenceRule {

        protected final String expectedPattern;
        protected final String replacement;

        public AbstractDriverInferenceRenamingInferenceRule(String expectedPattern, String replacement) {
            this.expectedPattern = expectedPattern;
            this.replacement = replacement;
        }
        
        public String getIdentifier() {
            return getClass().getName()+"["+expectedPattern+"]";
        }
        
        @Override
        public String toString() {
            return getClass().getName()+"["+expectedPattern+"->"+replacement+"]";
        }
    }
    
    public static class DriverInferenceByRenamingClassFullName extends AbstractDriverInferenceRenamingInferenceRule {

        public DriverInferenceByRenamingClassFullName(String expectedClassFullName, String newClassFullName) {
            super(expectedClassFullName, newClassFullName);
        }
        
        @Override
        public <D extends EntityDriver> String inferDriverClassName(DriverDependentEntity<D> entity, Class<D> driverInterface, Location location) {
            if (driverInterface.getName().equals(expectedPattern)) {
                return replacement;
            }
            return null;
        }
    }
    
    public static class DriverInferenceByRenamingClassSimpleName extends AbstractDriverInferenceRenamingInferenceRule {

        public DriverInferenceByRenamingClassSimpleName(String expectedClassSimpleName, String newClassSimpleName) {
            super(expectedClassSimpleName, newClassSimpleName);
        }
        
        @Override
        public <D extends EntityDriver> String inferDriverClassName(DriverDependentEntity<D> entity, Class<D> driverInterface, Location location) {
            if (driverInterface.getSimpleName().equals(expectedPattern)) { 
                // i'd like to do away with drivers altogether, but if people *really* need to use this and suppress the warning,
                // they can use the full class rename
                LOG.warn("Using discouraged driver simple class rename to find "+replacement+" for "+expectedPattern+"; it is recommended to set getDriverInterface() or newDriver() appropriately");
                return Strings.removeFromEnd(driverInterface.getName(), expectedPattern)+replacement;
            }
            return null;
        }
    }
    
    public static class DriverInferenceForSshLocation extends AbstractDriverInferenceRule {

        public static final String DEFAULT_IDENTIFIER = "ssh-location-driver-inference-rule";

        @Override
        public <D extends EntityDriver> String inferDriverClassName(DriverDependentEntity<D> entity, Class<D> driverInterface, Location location) {
            String driverInterfaceName = driverInterface.getName();
            if (!(location instanceof SshMachineLocation)) return null;
            if (!driverInterfaceName.endsWith("Driver")) {
                throw new IllegalArgumentException(String.format("Driver name [%s] doesn't end with 'Driver'; cannot auto-detect SshDriver class name", driverInterfaceName));
            }
            return Strings.removeFromEnd(driverInterfaceName, "Driver")+"SshDriver";
        }
    }

    public static class DriverInferenceForPaasLocation extends AbstractDriverInferenceRule {

        public static final String DEFAULT_IDENTIFIER = "paas-location-driver-inference-rule";

        @Override
        public <D extends EntityDriver> String inferDriverClassName(DriverDependentEntity<D> entity, Class<D> driverInterface, Location location) {
            String driverInterfaceName = driverInterface.getName();
            if (!(location instanceof PaasLocation)) return null;
            if (!driverInterfaceName.endsWith("Driver")) {
                throw new IllegalArgumentException(String.format("Driver name [%s] doesn't end with 'Driver'; cannot auto-detect PaasDriver class name", driverInterfaceName));
            }
            return Strings.removeFromEnd(driverInterfaceName, "Driver") + ((PaasLocation) location).getPaasProviderName() + "Driver";
        }
    }

    public static class DriverInferenceForWinRmLocation extends AbstractDriverInferenceRule {

        public static final String DEFAULT_IDENTIFIER = "winrm-location-driver-inference-rule";

        @Override
        public <D extends EntityDriver> String inferDriverClassName(DriverDependentEntity<D> entity, Class<D> driverInterface, Location location) {
            String driverInterfaceName = driverInterface.getName();
            if (!(location instanceof WinRmMachineLocation)) return null;
            if (!driverInterfaceName.endsWith("Driver")) {
                throw new IllegalArgumentException(String.format("Driver name [%s] doesn't end with 'Driver'; cannot auto-detect WinRmDriver class name", driverInterfaceName));
            }
            return Strings.removeFromEnd(driverInterfaceName, "Driver")+"WinRmDriver";
        }
    }

    /** adds a rule; possibly replacing an old one if one exists with the given identifier. the new rule is added after all previous ones.
     * @return the replaced rule, or null if there was no old rule */
    public DriverInferenceRule addRule(String identifier, DriverInferenceRule rule) {
        DriverInferenceRule oldRule = rules.remove(identifier);
        rules.put(identifier, rule);
        LOG.debug("Added driver mapping rule "+rule);
        return oldRule;
    }

    public DriverInferenceRule addClassFullNameMapping(String expectedClassFullName, String newClassFullName) {
        DriverInferenceByRenamingClassFullName rule = new DriverInferenceByRenamingClassFullName(expectedClassFullName, newClassFullName);
        return addRule(rule.getIdentifier(), rule);
    }

    public DriverInferenceRule addClassSimpleNameMapping(String expectedClassSimpleName, String newClassSimpleName) {
        DriverInferenceByRenamingClassSimpleName rule = new DriverInferenceByRenamingClassSimpleName(expectedClassSimpleName, newClassSimpleName);
        return addRule(rule.getIdentifier(), rule);
    }

    public <D extends EntityDriver> D build(DriverDependentEntity<D> entity, Location location){
        Class<D> driverInterface = entity.getDriverInterface();
        Class<? extends D> driverClass = null;
        List<Throwable> exceptions = MutableList.of();
        if (driverInterface.isInterface()) {
            List<DriverInferenceRule> ruleListInExecutionOrder = MutableList.copyOf(rules.values());
            Collections.reverse(ruleListInExecutionOrder);
            // above puts rules in order with most recently added first
            for (DriverInferenceRule rule: ruleListInExecutionOrder) {
                ReferenceWithError<Class<? extends D>> clazzR = rule.resolve(entity, driverInterface, location);
                if (clazzR!=null) {
                    if (!clazzR.hasError()) {
                        Class<? extends D> clazz = clazzR.get();
                        if (clazz!=null) {
                            driverClass = clazz;
                            break;
                        }
                    } else {
                        exceptions.add(clazzR.getError());
                    }
                }
            }
        } else {
            driverClass = driverInterface;
        }
        LOG.debug("Driver for "+driverInterface.getName()+" in "+location+" is: "+driverClass);

        if (driverClass==null) {
            if (exceptions.isEmpty())
                throw new RuntimeException("No drivers could be found for "+driverInterface.getName()+"; "
                    + "currently only SshMachineLocation is supported for autodetection (location "+location+")");
            else throw Exceptions.create("No drivers could be loaded for "+driverInterface.getName()+" in "+location, exceptions);
        }

        try {
            Constructor<? extends D> constructor = getConstructor(driverClass);
            constructor.setAccessible(true);
            return constructor.newInstance(entity, location);
        } catch (Exception e) {
            LOG.warn("Unable to instantiate "+driverClass+" (rethrowing): "+e);
            throw Exceptions.propagate(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <D extends EntityDriver> Constructor<D> getConstructor(Class<D> driverClass) {
        for (Constructor<?> constructor : driverClass.getConstructors()) {
            if (constructor.getParameterTypes().length == 2) {
                return (Constructor<D>) constructor;
            }
        }

        throw new RuntimeException(String.format("Class [%s] has no constructor with 2 arguments", driverClass.getName()));
    }

}
