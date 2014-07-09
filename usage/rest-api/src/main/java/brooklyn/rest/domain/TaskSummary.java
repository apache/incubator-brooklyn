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
package brooklyn.rest.domain;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

import brooklyn.util.collections.Jsonya;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TaskSummary implements HasId {

  private final String id;
  private final String displayName;
  private final String entityId;
  private final String entityDisplayName;
  private final String description;
  private final Collection<Object> tags;
  
  private final Long submitTimeUtc;
  private final Long startTimeUtc;
  private final Long endTimeUtc;
  
  private final String currentStatus;
  private final Object result;
  private final boolean isError;
  private final boolean isCancelled; 

  private final List<LinkWithMetadata> children;
  private final LinkWithMetadata submittedByTask;
  
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final LinkWithMetadata blockingTask;
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final String blockingDetails;
  
  private final String detailedStatus;
  
  @JsonSerialize(include=Inclusion.NON_NULL)
  private final Map<String, LinkWithMetadata> streams;
  
  private final Map<String, URI> links;
  
  public TaskSummary(
          @JsonProperty("id") String id, 
          @JsonProperty("displayName") String displayName, 
          @JsonProperty("description") String description, 
          @JsonProperty("entityId") String entityId, 
          @JsonProperty("entityDisplayName") String entityDisplayName, 
          @JsonProperty("tags") Set<Object> tags,
          @JsonProperty("submitTimeUtc") Long submitTimeUtc, 
          @JsonProperty("startTimeUtc") Long startTimeUtc, 
          @JsonProperty("endTimeUtc") Long endTimeUtc, 
          @JsonProperty("currentStatus") String currentStatus, 
          @JsonProperty("result") Object result, 
          @JsonProperty("isError") boolean isError, 
          @JsonProperty("isCancelled") boolean isCancelled, 
          @JsonProperty("children") List<LinkWithMetadata> children,
          @JsonProperty("submittedByTask") LinkWithMetadata submittedByTask,
          @JsonProperty("blockingTask") LinkWithMetadata blockingTask,
          @JsonProperty("blockingDetails") String blockingDetails,
          @JsonProperty("detailedStatus") String detailedStatus,
          @JsonProperty("streams") Map<String, LinkWithMetadata> streams,
          @JsonProperty("links") Map<String, URI> links) {
    this.id = id;
    this.displayName = displayName;
    this.description = description;
    this.entityId = entityId;
    this.entityDisplayName = entityDisplayName;
    this.tags = ImmutableList.<Object>copyOf(tags);
    this.submitTimeUtc = submitTimeUtc;
    this.startTimeUtc = startTimeUtc;
    this.endTimeUtc = endTimeUtc;
    this.currentStatus = currentStatus;
    this.result = result;
    this.isError = isError;
    this.isCancelled = isCancelled;
    this.children = children;
    this.blockingDetails = blockingDetails;
    this.blockingTask = blockingTask;
    this.submittedByTask = submittedByTask;
    this.detailedStatus = detailedStatus;
    this.streams = streams;
    this.links = ImmutableMap.copyOf(links);
}


  @Override
  public String getId() {
      return id;
  }
  
  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }

  public String getEntityId() {
      return entityId;
  }
  
  public String getEntityDisplayName() {
      return entityDisplayName;
  }
  
  public Collection<Object> getTags() {
    List<Object> result = new ArrayList<Object>();
    for (Object t: tags)
        result.add(Jsonya.convertToJsonPrimitive(t));
    return result;
  }

  @JsonIgnore
  public Collection<Object> getRawTags() {
      return tags;
    }

  public Long getSubmitTimeUtc() {
    return submitTimeUtc;
  }

  public Long getStartTimeUtc() {
    return startTimeUtc;
  }

  public Long getEndTimeUtc() {
    return endTimeUtc;
  }

  public String getCurrentStatus() {
    return currentStatus;
  }

  public Object getResult() {
    return result;
  }
  
  public boolean getIsError() {
    return isError;
  }
  
  public boolean getIsCancelled() {
    return isCancelled;
  }
  
  public List<LinkWithMetadata> getChildren() {
      return children;
  }

  public LinkWithMetadata getSubmittedByTask() {
      return submittedByTask;
  }
  
  public LinkWithMetadata getBlockingTask() {
    return blockingTask;
  }
  
  public String getBlockingDetails() {
    return blockingDetails;
  }

  public String getDetailedStatus() {
      return detailedStatus;
  }
  
  public Map<String, LinkWithMetadata> getStreams() {
      return streams;
  }

  public Map<String, URI> getLinks() {
      return links;
  }
  
  @Override
  public String toString() {
    return "TaskSummary{" +
        "id='" + id + '\'' +
        ", displayName='" + displayName + '\'' +
        ", currentStatus='" + currentStatus + '\'' +
        ", startTimeUtc='" + startTimeUtc + '\'' +
        ", endTimeUtc='" + endTimeUtc + '\'' +
        '}';
  }
}
