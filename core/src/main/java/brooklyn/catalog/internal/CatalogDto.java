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
import java.io.StringReader;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.PropagatedRuntimeException;
import brooklyn.util.stream.Streams;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;

@Beta
public class CatalogDto {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogDto.class);

    String id;
    /** e.g. url */
    String url;
    
    String contents;
    String contentsDescription;
    String name;
    String description;
    CatalogClasspathDto classpath;
    List<CatalogItemDtoAbstract<?,?>> entries = null;
    
    // for thread-safety, any dynamic additions to this should be handled by a method 
    // in this class which does copy-on-write
    List<CatalogDto> catalogs = null;

    public static CatalogDto newDefaultLocalScanningDto(CatalogClasspathDo.CatalogScanningModes scanMode) {
        CatalogDo result = new CatalogDo(
                newNamedInstance("Local Scanned Catalog", "All annotated Brooklyn entities detected in the default classpath", "scanning-local-classpath") );
        result.setClasspathScanForEntities(scanMode);
        return result.dto;
    }

    public static CatalogDto newDtoFromUrl(String url) {
        if (LOG.isDebugEnabled()) LOG.debug("Retrieving catalog from: {}", url);
        try {
            InputStream source = ResourceUtils.create().getResourceFromUrl(url);
            String contents = Streams.readFullyString(source);
            return newDtoFromXmlContents(contents, url);
        } catch (Throwable t) {
            Exceptions.propagateIfFatal(t);
            throw new PropagatedRuntimeException("Unable to retrieve catalog from " + url + ": " + t, t);
        }
    }

    public static CatalogDto newDtoFromXmlContents(String xmlContents, String originDescription) {
        CatalogDto result = (CatalogDto) new CatalogXmlSerializer().deserialize(new StringReader(xmlContents));
        result.contentsDescription = originDescription;
        
        if (LOG.isDebugEnabled()) LOG.debug("Retrieved catalog from: {}", originDescription);
        return result;
    }

    /**
     * Creates a DTO.
     * <p>
     * The way contents is treated may change; thus this (and much of catalog) should be treated as beta.
     * 
     * @param name
     * @param description
     * @param optionalContentsDescription optional description of contents; if null, we normally expect source 'contents' to be set later;
     *   if the DTO has no 'contents' (ie XML source) then a description should be supplied so we know who is populating it
     *   (e.g. manual additions); without this, warnings may be generated
     *   
     * @return a new Catalog DTO
     */
    public static CatalogDto newNamedInstance(String name, String description, String optionalContentsDescription) {
        CatalogDto result = new CatalogDto();
        result.name = name;
        result.description = description;
        if (optionalContentsDescription!=null) result.contentsDescription = optionalContentsDescription;
        return result;
    }

    public static CatalogDto newLinkedInstance(String url) {
        CatalogDto result = new CatalogDto();
        result.contentsDescription = url;
        result.contents = ResourceUtils.create().getResourceAsString(url);
        return result;
    }
    
    void populate() {
        if (contents==null) {
            if (url != null) {
                contents = ResourceUtils.create().getResourceAsString(url);
                contentsDescription = url;
            } else if (contentsDescription==null) {
                LOG.warn("Catalog DTO has no contents and no description; ignoring call to populate it. Description should be set to suppress this warning.");
                return;
            } else {
                LOG.debug("Nothing needs doing (no contents or URL) for catalog with contents described as "+contentsDescription+".");
                return;
            }
        }
        
        CatalogDto remoteDto = newDtoFromXmlContents(contents, contentsDescription);
        try {
            copyFrom(remoteDto, true);
        } catch (Exception e) {
            Exceptions.propagate(e);
        }
    }        

    /**
     * @throws NullPointerException If source is null (and !skipNulls)
     */
    void copyFrom(CatalogDto source, boolean skipNulls) throws IllegalAccessException {
        if (source==null) {
            if (skipNulls) return;
            throw new NullPointerException("source DTO is null, when copying to "+this);
        }
        
        if (!skipNulls || source.id != null) id = source.id;
        if (!skipNulls || source.contentsDescription != null) contentsDescription = source.contentsDescription;
        if (!skipNulls || source.contents != null) contents = source.contents;
        if (!skipNulls || source.name != null) name = source.name;
        if (!skipNulls || source.description != null) description = source.description;
        if (!skipNulls || source.classpath != null) classpath = source.classpath;
        if (!skipNulls || source.entries != null) entries = source.entries;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .omitNullValues()
                .add("name", name)
                .add("id", id)
                .add("contentsDescription", contentsDescription)
                .toString();
    }

}
