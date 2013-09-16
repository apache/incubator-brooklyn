/**
 * Renders details information about an application (sensors, summary, effectors, etc.).
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "./entity-summary", 
    "./entity-config", "./entity-sensors", "./entity-effectors", "./entity-policies",
    "./entity-activities", "model/task-summary", "text!tpl/apps/details.html"
], function (_, $, Backbone, SummaryView, ConfigView, SensorsView, EffectorsView, PoliciesView, ActivitiesView, TaskSummary, DetailsHtml) {

    var EntityDetailsView = Backbone.View.extend({
        template:_.template(DetailsHtml),
        events:{
            'click .entity-tabs a':'tabSelected'
        },
        initialize:function () {
            this.$el.html(this.template({}))
            this.summaryView = new SummaryView({
                model:this.model,
                application:this.options.application
            })
            this.configView = new ConfigView({
                model:this.model
            })
            this.sensorsView = new SensorsView({
                model:this.model
            })
            this.effectorsView = new EffectorsView({
                model:this.model
            })
            this.policiesView = new PoliciesView({
                model:this.model
            })
            this.activitiesView = new ActivitiesView({
                model:this.model,
                collection:new TaskSummary.Collection
            })
            this.$("#summary").html(this.summaryView.render().el)
            this.$("#config").html(this.configView.render().el)
            this.$("#sensors").html(this.sensorsView.render().el)
            this.$("#effectors").html(this.effectorsView.render().el)
            this.$("#policies").html(this.policiesView.render().el)
            this.$("#activities").html(this.activitiesView.render().el)
        },
        beforeClose:function () {
            this.summaryView.close()
            this.configView.close()
            this.sensorsView.close()
            this.effectorsView.close()
            this.policiesView.close()
            this.activitiesView.close()
        },
        render:function () {
            this.summaryView.render()
            this.configView.render()
            this.sensorsView.render()
            this.effectorsView.render()
            this.policiesView.render()
            this.activitiesView.render()
            return this;
        },
        tabSelected: function(event) {
            var tabName = $(event.currentTarget).attr("href").slice(1)
            var entityId = $("#app-tree span.active").attr("id")
            var entityHref = $("#app-tree span.active a").attr("href")
            if (entityId && entityHref) {                
                window.history.pushState(entityId+"/"+tabName, "", 
                    entityHref+"/"+tabName);
            } else {
                window.history.pushState("notfound", "", "#/v1/applications")
            }
        }
    });
    return EntityDetailsView;
});