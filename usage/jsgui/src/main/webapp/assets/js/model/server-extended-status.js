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
define(["backbone", "brooklyn", "view/viewutils"], function (Backbone, Brooklyn, ViewUtils) {

    var ServerExtendedStatus = Backbone.Model.extend({
        callbacks: [],
        loaded: false,
        url: "/v1/server/up/extended",
        onError: function(thiz,xhr,modelish) {
            log("ServerExtendedStatus: error contacting Brooklyn server");
            log(xhr);
            if (xhr.readyState==0) {
                // server not contactable
                this.loaded = false;
            } else {
                // server error
                log(xhr.responseText);
                // simply set unhealthy
                this.set("healthy", false);
            }
            this.applyCallbacks();
        },
        whenUp: function(f) {
            var that = this;
            if (this.isUp()) {
                f();
            } else {
                this.addCallback(function() { that.whenUp(f); });
            }
        },
        onLoad: function(f) {
            if (this.loaded) {
                f();
            } else {
                this.addCallback(f);
            }
        },
        addCallback: function(f) {
            this.callbacks.push(f);
        },
        autoUpdate: function() {
            var that = this;
            // to debug:
//            serverExtendedStatus.onLoad(function() { log("loaded server status:"); log(that.attributes); })
            ViewUtils.fetchModelRepeatedlyWithDelay(this, { doitnow: true });
        },

        isUp: function() { return this.get("up") },
        isShuttingDown: function() { return this.get("shuttingDown") },
        isHealthy: function() { return this.get("healthy") },
        isMaster: function() {
            ha = this.get("ha") || {};
            ownId = ha.ownId;
            if (!ownId) return null;
            return ha.masterId == ownId;
        },
        getMasterUri: function() {
            // Might be undefined if first fetch hasn't completed
            ha = this.get("ha") || {};
            states = ha.states || {};
            if (!states) return null;
            
            var nodes = this.get("nodes") || {};
            var master = nodes[this.get("masterId")];
            // defensive, if this happens something more serious has gone wrong!
            if (!master) {
                return null;
            } else {
                return master.nodeUri;
            }
        },
        applyCallbacks: function() {
            var currentCallbacks = this.callbacks;
            this.callbacks = [];
            _.invoke(currentCallbacks, "apply");
        },
    });

    var serverExtendedStatus = new ServerExtendedStatus();
    serverExtendedStatus.on("sync", function() {
        serverExtendedStatus.loaded = true;
        serverExtendedStatus.applyCallbacks();
    });
    serverExtendedStatus.on("error", serverExtendedStatus.onError);

    // Will returning the instance rather than the object be confusing?
    // It breaks the pattern used by all the other models.
    return serverExtendedStatus;

});