/**
 * Renders details information about an application (sensors, summary, effectors, etc.).
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "./entity-summary", "./entity-sensors", "./entity-effectors",
    "./entity-activities", "model/task-summary", "text!tpl/apps/details.html"
], function (_, $, Backbone, SummaryView, SensorsView, EffectorsView, ActivitiesView, TaskSummary, DetailsHtml) {

    var EntityDetailsView = Backbone.View.extend({
        template:_.template(DetailsHtml),
        initialize:function () {
            this.$el.html(this.template({}))
            this.summaryView = new SummaryView({
                model:this.model,
                application:this.options.application
            })
            this.sensorsView = new SensorsView({
                model:this.model
            })
            this.effectorsView = new EffectorsView({
                model:this.model
            })
            this.activitiesView = new ActivitiesView({
                model:this.model,
                collection:new TaskSummary.Collection
            })
            this.$("#summary").html(this.summaryView.render().el)
            this.$("#sensors").html(this.sensorsView.render().el)
            this.$("#effectors").html(this.effectorsView.render().el)
            this.$("#activities").html(this.activitiesView.render().el)
        },
        beforeClose:function () {
            this.summaryView.close()
            this.sensorsView.close()
            this.effectorsView.close()
            this.activitiesView.close()
        },
        render:function () {
            this.summaryView.render()
            this.sensorsView.render()
            this.effectorsView.render()
            this.activitiesView.render()
            return this;
        }
    });
    return EntityDetailsView;
});