/**
 * Render the application/entity summary tab.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils",
    "text!tpl/apps/summary.html", "formatJson"
], function (_, $, Backbone, ViewUtils, SummaryHtml, FormatJSON) {

    var EntitySummaryView = Backbone.View.extend({
        template:_.template(SummaryHtml),
        initialize: function() {
            _.bindAll(this)
            var that = this
            var ej = FormatJSON(this.model.toJSON());
            this.$el.html(this.template({
                entity:this.model,
                application:this.options.application,
                entityJson:ej,
                applicationJson:FormatJSON(this.options.application.toJSON())
            }))
            ViewUtils.updateTextareaWithData($(".for-textarea", this.$el), ej, true, false, 150, 400)
            ViewUtils.attachToggler(this.$el)

            // TODO we should have a backbone object exported from the sensors view which we can listen to here
            // (currently we just take the URL from that view) - and do the same for active tasks;
            if (this.options.sensors) {
                ViewUtils.getRepeatedlyWithDelay(this, this.options.sensors.getSensorUpdateUrl(),
                    function(data) { that.updateWithData(data) });
            } else {
                // e.g. in tests
                log("no sensors available to EntitySummaryView")
            }
            

            // however if we only use external objects we must either subscribe to their errors also
            // or do our own polling against the server, so we know when to disable ourselves
//            ViewUtils.fetchRepeatedlyWithDelay(this, this.model, { period: 10*1000 })
        },
        render:function () {
            return this
        },
        revealIfHasValue: function(sensor, $div, renderer, values) {
            var that = this;
            if (!renderer) renderer = function(data) { return _.escape(data); }
            
            if (values) {
                var data = values[sensor]
                if (data || data===false) {
                    $(".value", $div).html(renderer(data))
                    $div.show()
                } else {
                    $div.hide();
                }
                that.updateStatusIcon();
            } else {
              // direct ajax call not used anymore - but left just in case
              $.ajax({
                url: that.model.getLinkByName("sensors")+"/"+sensor,
                contentType:"application/json",
                success:function (data) {
                    if (data || data===false) {
                        $(".value", $div).html(renderer(data))
                        $div.show()
                    } else {
                        $div.hide();
                    }
                    that.updateStatusIcon();
                }})
            }
        },
        updateWithData: function (data) {
            this.revealIfHasValue("service.isUp", this.$(".serviceUp"), null, data)
            this.revealIfHasValue("service.state", this.$(".status"), null, data)
            this.revealIfHasValue("webapp.url", this.$(".url"),
                    function(data) { return "<a href='"+_.escape(data)+"'>"+_.escape(data)+"</img>" }, data)
        },
        updateSensorsNow: function() {
            this.updateWithData();
        },
        
        updateStatusIcon: function() {
            var statusIconUrl = ViewUtils.computeStatusIcon(this.$(".serviceUp .value:visible").html(), this.$(".status .value:visible").html());
            if (statusIconUrl) {
                this.$('#status-icon').html('<img src="'+statusIconUrl+'" '+
                        'style="max-width: 64px; max-height: 64px;"/>')
            } else {
                this.$('#status-icon').html('');
            }
        }
    })

    return EntitySummaryView
})
