/**
 * Render the application/entity summary tab.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "text!tpl/apps/summary.html", "formatJson"
], function (_, $, Backbone, SummaryHtml) {

    var EntitySummaryView = Backbone.View.extend({
        template:_.template(SummaryHtml),
        render:function () {
            this.$el.html(this.template({
                entity:FormatJSON(this.model.toJSON()),
                application:FormatJSON(this.options.application.toJSON())
            }))
            return this
        }
    })

    return EntitySummaryView
})