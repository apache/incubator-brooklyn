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
package org.apache.brooklyn.rest.util.json;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.*;

/** a visibility checker which disables getters, but allows private access,
 * unless {@link BidiSerialization#isStrictSerialization()} is enabled in which case public fields or annotations must be used.
 * <p>
 * the reason for this change to visibility
 * is that getters might generate a copy, resulting in infinite loops, whereas field access should never do so.
 * (see e.g. test in {@link BrooklynJacksonSerializerTest} which uses a sensor+config object whose getTypeToken
 * causes infinite recursion)
 **/
public class PossiblyStrictPreferringFieldsVisibilityChecker implements VisibilityChecker<PossiblyStrictPreferringFieldsVisibilityChecker> {
    VisibilityChecker<?>
        vizDefault = new VisibilityChecker.Std(Visibility.NONE, Visibility.NONE, Visibility.NONE, Visibility.ANY, Visibility.ANY),
        vizStrict = new VisibilityChecker.Std(Visibility.NONE, Visibility.NONE, Visibility.NONE, Visibility.PUBLIC_ONLY, Visibility.PUBLIC_ONLY);
    
    @Override public PossiblyStrictPreferringFieldsVisibilityChecker with(JsonAutoDetect ann) { throw new UnsupportedOperationException(); }
    @Override public PossiblyStrictPreferringFieldsVisibilityChecker with(Visibility v) { throw new UnsupportedOperationException(); }
    @Override public PossiblyStrictPreferringFieldsVisibilityChecker withVisibility(PropertyAccessor method, Visibility v) { throw new UnsupportedOperationException(); }
    @Override public PossiblyStrictPreferringFieldsVisibilityChecker withGetterVisibility(Visibility v) { throw new UnsupportedOperationException(); }
    @Override public PossiblyStrictPreferringFieldsVisibilityChecker withIsGetterVisibility(Visibility v) { throw new UnsupportedOperationException(); }
    @Override public PossiblyStrictPreferringFieldsVisibilityChecker withSetterVisibility(Visibility v) { throw new UnsupportedOperationException(); }
    @Override public PossiblyStrictPreferringFieldsVisibilityChecker withCreatorVisibility(Visibility v) { throw new UnsupportedOperationException(); }
    @Override public PossiblyStrictPreferringFieldsVisibilityChecker withFieldVisibility(Visibility v) { throw new UnsupportedOperationException(); }
    
    protected VisibilityChecker<?> viz() {
        return BidiSerialization.isStrictSerialization() ? vizStrict : vizDefault;
    }
    
    @Override public boolean isGetterVisible(Method m) { 
        return viz().isGetterVisible(m);
    }

    @Override
    public boolean isGetterVisible(AnnotatedMethod m) {
        return isGetterVisible(m.getAnnotated());
    }

    @Override
    public boolean isIsGetterVisible(Method m) {
        return viz().isIsGetterVisible(m);
    }

    @Override
    public boolean isIsGetterVisible(AnnotatedMethod m) {
        return isIsGetterVisible(m.getAnnotated());
    }

    @Override
    public boolean isSetterVisible(Method m) {
        return viz().isSetterVisible(m);
    }

    @Override
    public boolean isSetterVisible(AnnotatedMethod m) {
        return isSetterVisible(m.getAnnotated());
    }

    @Override
    public boolean isCreatorVisible(Member m) {
        return viz().isCreatorVisible(m);
    }

    @Override
    public boolean isCreatorVisible(AnnotatedMember m) {
        return isCreatorVisible(m.getMember());
    }

    @Override
    public boolean isFieldVisible(Field f) {
        return viz().isFieldVisible(f);
    }

    @Override
    public boolean isFieldVisible(AnnotatedField f) {
        return isFieldVisible(f.getAnnotated());
    }
}
