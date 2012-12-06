/**
 * Render the application/entity summary tab.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils",
    "text!tpl/apps/summary.html", "formatJson", "brooklyn-utils"
], function (_, $, Backbone, ViewUtils, SummaryHtml) {

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
            ViewUtils.updateTextareaWithData($(".for-textarea", this.$el), ej, true, 150, 400)
            ViewUtils.attachToggler(this.$el)
            that.callPeriodically("entity-summary-sensors", 
                    function() { that.updateSensorsNow(that) }, 3000)
            that.updateSensorsNow(that)
        },
        render:function () {
            return this
        },
        revealIfHasValue: function(that, sensor, $div) {
            $.ajax({
                url: that.model.getLinkByName("sensors")+"/"+sensor,
                contentType:"application/json",
                success:function (data) {
                    $(".value", $div).html(_.escape(data))
                    $div.show()
                }})            
        },
        updateSensorsNow: function(that) {
            that.revealIfHasValue(that, "service.isUp", that.$(".serviceUp"))
            that.revealIfHasValue(that, "service.state", that.$(".status"))
        }
    })

    return EntitySummaryView
})