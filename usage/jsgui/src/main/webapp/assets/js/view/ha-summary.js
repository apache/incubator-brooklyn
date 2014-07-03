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
    "jquery", "underscore", "backbone", "moment",
    "model/ha",
    "text!tpl/home/ha-summary.html"
], function ($, _, Backbone, moment, ha, HASummaryHtml) {

    var template = _.template(HASummaryHtml);
    var nodeRowTemplate = _.template(
        "<tr>" +
            "<td>" +
                "<% if (nodeUri && !isTerminated) { %><a href='<%= nodeUri %>'><%= nodeId %></a><% } else { %><%= nodeId %><%    } %>" +
                "<% if (isSelf) { %><span class='pull-right badge badge-success'>this</span><% } %>" +
            "</td>" +
            "<td><%= status %></td>" +
            "<td><span class='timestamp' data-timestamp='<%= timestamp %>'><%= timestampDisplay %><span></td>" +
        "</tr>");
    var noServers = "<tr><td colspan='3'><i>Failed to load servers!</i></td></tr>";

    var HASummaryView = Backbone.View.extend({
        initialize: function() {
            _.bindAll(this);
            this.updateTimestampCallback = setInterval(this.updateTimestamps, 1000);
            this.listenTo(ha, "change", this.renderNodeStatus);
        },
        beforeClose: function() {
            clearInterval(this.updateTimestampCallback);
            this.stopListening();
        },
        render: function() {
            this.$el.html(template());
            if (ha.loaded) {
                this.renderNodeStatus();
            }
            return this;
        },
        renderNodeStatus: function() {
            var master = ha.get("masterId"),
                self = ha.get("ownId"),
                nodes = ha.get("nodes"),
                $target = this.$(".ha-summary-table-body");
            $target.empty();
            // undefined check just in case server returns something odd
            if (nodes == undefined || _.isEmpty(nodes)) {
                $target.html(noServers)
            } else {
                _.each(nodes, function (n) {
                    var node = _.clone(n);
                    if (node['remoteTimestamp']) {
                        node.timestamp = node.remoteTimestamp;
                        node.timestampDisplay = moment(node.remoteTimestamp).fromNow();
                    } else {
                        node.timestamp = node.localTimestamp;
                        node.timestampDisplay = moment(node.localTimestamp).fromNow()+" (local)";
                    }
                    node.isSelf = node.nodeId == self;
                    node.isMaster = self == master;
                    node.isTerminated = node.status == "TERMINATED";
                    $target.append(nodeRowTemplate(node));
                })
            }
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