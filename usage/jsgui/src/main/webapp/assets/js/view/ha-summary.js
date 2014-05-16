define([
    "jquery", "underscore", "backbone", "moment",
    "model/ha",
    "text!tpl/home/ha-summary.html"
], function ($, _, Backbone, moment, ha, HASummaryHtml) {

    var template = _.template(HASummaryHtml);
    var nodeRowTemplate = _.template(
        "<tr>" +
            "<td><%= nodeId %><% if (isSelf) { %><span class='pull-right badge badge-success'>this</span><% } %></td>" +
            "<td><%= status %></td>" +
            "<td class='timestamp' data-timestamp='<%= timestampUtc %>'><%= timestamp %></td>" +
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
                    node.timestamp = moment(node.timestampUtc).fromNow();
                    node.isSelf = node.nodeId == self;
                    node.isMaster = self == master;
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