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
package org.apache.brooklyn.core.entity.lifecycle;

import java.io.Serializable;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.config.render.RendererHints;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.text.StringFunctions;

import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * An enumeration representing the status of an {@link org.apache.brooklyn.api.entity.Entity}.
 */
public enum Lifecycle {
    /**
     * The entity has just been created.
     *
     * This stage encompasses the contruction. Once this stage is
     * complete, the basic set of sensors will be available, apart from any that require the entity to be active or
     * deployed to a {@link Location}.
     */
    CREATED,

    /**
     * The entity is starting.
     * <p>
     * This stage is typically entered when the {@link Startable#START} effector 
     * is called, to undertake the startup operations from the management plane.
     * When this completes the entity will normally transition to 
     * {@link Lifecycle#RUNNING}. 
     */
    STARTING,

    /**
     * The entity service is expected to be running. In healthy operation, {@link Attributes#SERVICE_UP} will be true,
     * or will shortly be true if all service start actions have been completed and we are merely waiting for it to be running. 
     */
    RUNNING,

    /**
     * The entity is stopping.
     *
     * This stage is activated when the 
     * {@link Startable#STOP} effector is called. The entity service is stopped. 
     * Sensors that provide data from the running entity may be cleared and subscriptions cancelled.
     */
    STOPPING,

    /**
     * The entity is not expected to be active.
     *
     * This stage is entered when an entity is stopped, or may be entered when an entity is 
     * fully created but not started. It may or may not be removed from the location(s) it was assigned,
     * and it will typically not be providing new sensor data apart.
     */
    STOPPED,

    /**
     * The entity is destroyed.
     *
     * The entity will be unmanaged and removed from any groups and from its parent.
     */
    DESTROYED,

    /**
     * Entity error state.
     *
     * This stage is reachable from any other stage if an error occurs or an exception is thrown.
     */
    ON_FIRE;

    /**
     * The text representation of the {@link #name()}.
     *
     * This is formatted as lower case characters, with hyphens instead of spaces.
     */
    public String value() {
       return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, name());
    }

    /** @see #value() */
    @Override
    public String toString() { return value(); }

    /**
     * Creates a {@link Lifecycle} from a text representation.
     *
     * This accepts the text representations output by the {@link #value()} method for each entry.
     *
     * @see #value()
     */
    public static Lifecycle fromValue(String v) {
       try {
          return valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, v));
       } catch (IllegalArgumentException iae) {
          return ON_FIRE;
       }
    }
    
    public static class Transition implements Serializable {
        private static final long serialVersionUID = 603419184398753502L;
        
        final Lifecycle state;
        final long timestampUtc;
        
        public Transition(Lifecycle state, Date timestamp) {
            this.state = Preconditions.checkNotNull(state, "state");
            this.timestampUtc = Preconditions.checkNotNull(timestamp, "timestamp").getTime();
        }
        
        public Lifecycle getState() {
            return state;
        }
        public Date getTimestamp() {
            return new Date(timestampUtc);
        }
        
        @Override
        public int hashCode() {
            return Objects.hashCode(state, timestampUtc);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Transition)) return false;
            if (!state.equals(((Transition)obj).getState())) return false;
            if (timestampUtc != ((Transition)obj).timestampUtc) return false;
            return true;
        }
        
        @Override
        public String toString() {
            return state+" @ "+timestampUtc+" / "+new Date(timestampUtc);
        }
    }
    
    protected static class TransitionCoalesceFunction implements Function<String, Transition> {
        private static final Pattern TRANSITION_PATTERN = Pattern.compile("^([\\w-]+)\\s+@\\s+(\\d+).*");

        @Override
        public Transition apply(final String input) {
            if (input != null) {
                Matcher m = TRANSITION_PATTERN.matcher(input);
                if (m.matches()) {
                    Lifecycle state = Lifecycle.valueOf(m.group(1).toUpperCase().replace('-', '_'));
                    long time = Long.parseLong(m.group(2));
                    return new Transition(state, new Date(time));
                } else {
                    throw new IllegalStateException("Serialized Lifecycle.Transition can't be parsed: " + input);
                }
            } else {
                return null;
            }
        }
    }

    static {
        TypeCoercions.registerAdapter(String.class, Transition.class, new TransitionCoalesceFunction());
        RendererHints.register(Transition.class, RendererHints.displayValue(StringFunctions.toStringFunction()));
    }
}
