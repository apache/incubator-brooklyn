/*
 * Copyright (C) 2010 Cloud Conscious, LLC. <info@cloudconscious.com>
 * Copied from jclouds-core 31/1/2011, org.jclouds.json.internal.EnumTypeAdapterThatReturnsFromValue
 * 
 * @author Adrian Cole
 */

package com.cloudsoftcorp.util.jsonable.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.MapMaker;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * @author Adrian Cole
 */
@SuppressWarnings("unchecked")
public class EnumTypeAdapterThatReturnsFromValue<T extends Enum<T>> implements JsonSerializer<T>, JsonDeserializer<T> {
   public JsonElement serialize(T src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(src.name());
   }

   @SuppressWarnings("cast")
   public T deserialize(JsonElement json, Type classOfT, JsonDeserializationContext context) throws JsonParseException {
      try {
         return (T) Enum.valueOf((Class<T>) classOfT, json.getAsString());
      } catch (IllegalArgumentException e) {
         Method converter = classToConvert.get(classOfT);
         if (converter != null)
            try {
               return (T) converter.invoke(null, json.getAsString());
            } catch (Exception e1) {
               throw e;
            }
         else
            throw e;
      }
   }

   private final static Map<Class<?>, Method> classToConvert = new MapMaker()
         .makeComputingMap(new Function<Class<?>, Method>() {

            @Override
            public Method apply(Class<?> from) {
               try {
                  Method method = from.getMethod("fromValue", String.class);
                  method.setAccessible(true);
                  return method;
               } catch (Exception e) {
                  return null;
               }
            }

         });

   @Override
   public String toString() {
      return EnumTypeAdapterThatReturnsFromValue.class.getSimpleName();
   }
}
