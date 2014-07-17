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
package brooklyn.catalog.internal;

import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;

public class CatalogDtoUtils {

    private static final Logger log = LoggerFactory.getLogger(CatalogDtoUtils.class);
    
    public static CatalogDto newDefaultLocalScanningDto(CatalogScanningModes scanMode) {
        return CatalogDto.newDefaultLocalScanningDto(scanMode);
    }

    /** throws if there are any problems in retrieving or copying */
    public static void populateFromUrl(CatalogDto dto, String url) {
        CatalogDto remoteDto = newDtoFromUrl(url);
        try {
            copyDto(remoteDto, dto, true);
        } catch (Exception e) {
            Exceptions.propagate(e);
        }
    }

    /** does a shallow copy.
     * "skipNulls" means not to copy any fields from the source which are null */ 
    static void copyDto(CatalogDto source, CatalogDto target, boolean skipNulls) throws IllegalArgumentException, IllegalAccessException {
        target.copyFrom(source, skipNulls);
    }

    public static CatalogDto newDtoFromUrl(String url) {
        if (log.isDebugEnabled()) log.debug("Retrieving catalog from: {}", url);
        try {
            InputStream source = ResourceUtils.create().getResourceFromUrl(url);
            CatalogDto result = (CatalogDto) new CatalogXmlSerializer().deserialize(new InputStreamReader(source));
            if (log.isDebugEnabled()) log.debug("Retrieved catalog from: {}", url);
            return result;
        } catch (Throwable t) {
            log.debug("Unable to retrieve catalog from: "+url+" ("+t+")");
            throw Exceptions.propagate(t);
        }
    }
}
