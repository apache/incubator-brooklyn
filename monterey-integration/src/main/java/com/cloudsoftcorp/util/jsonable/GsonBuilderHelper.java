/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package com.cloudsoftcorp.util.jsonable;

import java.util.Map;

import com.cloudsoftcorp.util.jsonable.internal.EnumTypeAdapterThatReturnsFromValue;
import com.google.gson.CloudsoftGsonPackageAccessor;
import com.google.gson.GsonBuilder;
import com.google.gson.ObjectMapTypeAdapter;

/**
 * Contributes adapters for serializing/deserializing json maps elegantly when no type is supplied.
 * Instead of treating it as an Object and falling over, it will produce a nested map reflecting 
 * the structure of the underlying json.
 * 
 * @author aled
 */
public class GsonBuilderHelper {

    @SuppressWarnings("rawtypes")
    public static GsonBuilder contributeAdapters(GsonBuilder builder) {
        CloudsoftGsonPackageAccessor.registerTypeHierarchyAdapter(builder, Enum.class, new EnumTypeAdapterThatReturnsFromValue());
        CloudsoftGsonPackageAccessor.registerTypeHierarchyAdapter(builder, Map.class, new ObjectMapTypeAdapter());
        return builder;
    }
}
