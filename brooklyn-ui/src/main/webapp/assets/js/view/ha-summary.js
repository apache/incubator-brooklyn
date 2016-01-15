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
    "jquery", "underscore", "backbone", "moment", "view/viewutils",
    "model/server-extended-status",
    "text!tpl/home/ha-summary.html"
], function ($, _, Backbone, moment, ViewUtils, serverStatus, HASummaryHtml) {

    var template = _.template(HASummaryHtml);
    var nodeRowTemplate = _.template(
        "<tr>" +
            "<td>" +
                "<% if (nodeUri && !isTerminated) { %><a href='<%= nodeUri %>'><%= nodeId %></a><% } else { %><%= nodeId %><%    } %>" +
                "<% if (isSelf) { %><span class='pull-right badge badge-success'>this</span><% } %>" +
            "</td>" +
            "<td><% if (isPretendMaster) {%>EX-MASTER<%} else {%><%= status %><%} if (isStale) { %> (stale)<% } %></td>" +
            "<td><%= timestampDisplayPrefix %><span class='timestamp' data-timestamp='<%= timestamp %>'><%= timestampDisplay %><span><%= timestampDisplaySuffix %></td>" +
        "</tr>");
    var noServers = "<tr><td colspan='3'><i>Failed to load data of servers</i></td></tr>";
    var waitingServers = "<tr><td colspan='3'><i>Waiting on detail for servers...</i></td></tr>";

    var HASummaryView = Backbone.View.extend({
        initialize: function() {
            _.bindAll(this);
            this.updateTimestampCallback = setInterval(this.updateTimestamps, 1000);
            this.listenTo(serverStatus, "change", this.renderNodeStatus);
        },
        beforeClose: function() {
            clearInterval(this.updateTimestampCallback);
            this.stopListening();
        },
        updateNow: function() {
            serverStatus.fetch();
        },
        render: function() {
            this.$el.html(template());
            this.renderNodeStatus();
            return this;
        },
        renderNodeStatus: function() {
            var $target = this.$(".ha-summary-table-body");
            if (!serverStatus.loaded) {
                $target.html(waitingServers);
                return;
            }
            
            var serverHa = serverStatus.get("ha") || {};
            var master = serverHa.masterId,
                self = serverHa.ownId,
                nodes = serverHa.nodes;
                
            // undefined check just in case server returns something odd
            if (nodes == undefined || _.isEmpty(nodes)) {
                $target.html(noServers);
                return;
            }
            
            $target.empty();
            var masterTimestamp;
            _.each(nodes, function (n) {
                    if (n.nodeId == master && n.remoteTimestamp) {
                        masterTimestamp = n.remoteTimestamp;
                    }
                });
            
            _.each(nodes, function (n) {
                var node = _.clone(n);
                node.timestampDisplayPrefix = "";
                node.timestampDisplaySuffix = "";
                if (node['remoteTimestamp']) {
                    node.timestamp = node.remoteTimestamp;
                } else {
                    node.timestamp = node.localTimestamp;
                    node.timestampDisplaySuffix = " (local)";
                }
                if (node.timestamp >= moment().utc() + 10*1000) {
                    // if server reports time significantly in future, report this, with no timestampe
                    node.timestampDisplayPrefix = "server clock in future by "+
                        moment.duration(moment(node.timestamp).diff(moment())).humanize();
                    node.timestamp = "";
                    node.timestampDisplay = "";
                } else {
                    // else use timestamp
                    if (node.timestamp >= moment().utc()) {
                        // but if just a little bit in future, backdate to show "a few seconds ago"
                        node.timestamp = moment().utc()-1;
                    }
                    node.timestampDisplay = moment(node.timestamp).fromNow();
                }
                
                node.isSelf = node.nodeId == self;
                node.isMaster = self == master;
                if (node.status == "TERMINATED") {
                    node.isTerminated = true;
                    node.isPretendMaster = false;
                    node.isStale = false;
                } else {
                    node.isTerminated = false;
                    node.isPretendMaster = (!node.isMaster && node.status == "MASTER" && master != node.nodeId);
                    node.isStale = (masterTimestamp && node.timestamp + 30*1000 < masterTimestamp);
                }
                 
                $target.append(nodeRowTemplate(node));
            });
        },
        updateTimestamps: function() {
            this.$(".timestamp").each(function(index, t) {
                t = $(t);
                var timestamp = t.data("timestamp");
                t.html(moment(timestamp).fromNow());
            });
        }
    });

    return HASummaryView;
});