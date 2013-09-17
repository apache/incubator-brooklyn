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
            that.callPeriodically("entity-summary-sensors", 
                    function() { that.updateSensorsNow(that) }, 3000)
            that.updateSensorsNow(that)
        },
        render:function () {
            return this
        },
        revealIfHasValue: function(that, sensor, $div, renderer) {
            var that = this;
            if (!renderer) renderer = function(data) { return _.escape(data); }
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
        },
        updateSensorsNow: function(that) {
            <!-- hard-coded values for most commonly used sensors -->
            
            that.revealIfHasValue(that, "service.isUp", that.$(".serviceUp"))
            that.revealIfHasValue(that, "service.state", that.$(".status"))
            
            that.revealIfHasValue(that, "webapp.url", that.$(".url"),
                    function(data) { return "<a href='"+_.escape(data)+"'>"+_.escape(data)+"</img>" })
        },
        
        updateStatusIcon: function() {
            // currently we use the string values from the page; messy, but it works
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
