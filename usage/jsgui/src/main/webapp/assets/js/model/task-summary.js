/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
define([
    "underscore", "backbone"
], function (_, Backbone) {

    var TaskSummary = {};

    TaskSummary.Model = Backbone.Model.extend({
        defaults:function () {
            return {
                id:"",
                links:{},
                displayName:"",
                description:"",
                entityId:"",
                entityDisplayName:"",
                tags:{},
                submitTimeUtc:null,
                startTimeUtc:null,
                endTimeUtc:null,
                currentStatus:"",
                result:null,
                isError:null,
                isCancelled:null,
                children:[],
                detailedStatus:"",
                blockingTask:null,
                blockingDetails:null,
                // missing some from TaskSummary (e.g. streams, isError), 
                // but that's fine, worst case they come back null / undefined
            };
        },
        getTagByName:function (name) {
            if (name) return this.get("tags")[name];
        },
        isError: function() { return this.attributes.isError==true; },
        isGlobalTopLevel: function() {
            return this.attributes.submittedByTask == null;
        },
        isLocalTopLevel: function() {
            var submitter = this.attributes.submittedByTask;
            return (submitter==null ||
                    (submitter.metadata && submitter.metadata.id != this.id)); 
        },
        
        // added from https://github.com/jashkenas/backbone/issues/1069#issuecomment-17511573
        // to clear attributes locally if they aren't in the server-side function
        parse: function(resp) {
            _.keys(this.attributes).forEach(function(key) {
              if (resp[key] === undefined) {
                resp[key] = null;
              }
            });

            return resp;
        }
    });

    TaskSummary.Collection = Backbone.Collection.extend({
        model:TaskSummary.Model
    });

    return TaskSummary;
});
