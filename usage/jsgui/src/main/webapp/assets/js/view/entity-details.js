/**
 * Renders details information about an application (sensors, summary, effectors, etc.).
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "./entity-summary", 
    "./entity-config", "./entity-sensors", "./entity-effectors", "./entity-policies",
    "./entity-activities", "./entity-lifecycle", "model/task-summary", "text!tpl/apps/details.html"
], function (_, $, Backbone, SummaryView, ConfigView, SensorsView, EffectorsView, PoliciesView, ActivitiesView, LifecycleView, TaskSummary, DetailsHtml) {

    var EntityDetailsView = Backbone.View.extend({
        template:_.template(DetailsHtml),
        events:{
            'click .entity-tabs a':'tabSelected'
        },
        initialize:function () {
            var self = this;
            var tasks = new TaskSummary.Collection;
            
            this.$el.html(this.template({}))
            this.configView = new ConfigView({
                model:this.model,
                tabView:this,
            })
            this.sensorsView = new SensorsView({
                model:this.model,
                tabView:this,
            })
            this.effectorsView = new EffectorsView({
                model:this.model,
                tabView:this,
            })
            this.policiesView = new PoliciesView({
                model:this.model,
                tabView:this,
            })
            this.activitiesView = new ActivitiesView({
                model:this.model,
                tabView:this,
                collection:tasks
            })
            this.summaryView = new SummaryView({
                model:this.model,
                tabView:this,
                application:this.options.application,
                tasks:tasks,
            })
            this.lifecycleView = new LifecycleView({
                model: this.model,
                tabView:this,
                application:this.options.application
            });
            this.lifecycleView.on("entity.expunged", function() {
                self.trigger("entity.expunged");
            });
            this.$("#summary").html(this.summaryView.render().el);
            this.$("#lifecycle").html(this.lifecycleView.render().el);
            this.$("#config").html(this.configView.render().el);
            this.$("#sensors").html(this.sensorsView.render().el);
            this.$("#effectors").html(this.effectorsView.render().el);
            this.$("#policies").html(this.policiesView.render().el);
            this.$("#activities").html(this.activitiesView.render().el);
        },
        beforeClose:function () {
            this.summaryView.close();
            this.configView.close();
            this.sensorsView.close();
            this.effectorsView.close();
            this.policiesView.close();
            this.activitiesView.close();
            this.lifecycleView.close();
        },
        render: function(optionalParent) {
            this.summaryView.render()
            this.configView.render()
            this.sensorsView.render()
            this.effectorsView.render()
            this.policiesView.render()
            this.activitiesView.render()
            if (optionalParent) {
                optionalParent.html(this.el)
            }
            if (this.options.preselectTab) {
                var tabLink = this.$('a[href="#'+this.options.preselectTab+'"]');
                var showFn = function() { tabLink.tab('show'); }
                if (optionalParent) showFn();
                else _.defer(showFn);
            }
            return this;
        },
        tabSelected: function(event) {
            var tabName = $(event.currentTarget).attr("href").slice(1);
            var entityId = $("#app-tree .entity_tree_node_wrapper.active").attr("id");
            var route = this.getTab(entityId, tabName);
            if (route) {
                if (route[0]=='#') route = route.substring(1);
                Backbone.history.navigate(route);
            }
        },
        getTab: function(entityId, tabName, entityHref) {
            if (!entityHref)
                entityHref = $("#app-tree .entity_tree_node_wrapper.active a").attr("href");
            
            var route = "#v1/applications";
            var stateName = "notfound"
            if (entityId && entityHref) {                
                route = entityHref+"/"+tabName;
                stateName = entityId+"/"+tabName;
            }
            return route;
        },
        /** for tabs to redirect to other tabs; entityHref is optional; tabName is e.g. 'acitvities' */ 
        openTab: function(entityId, tabName, entityHref) {
            var route = this.getTab(entityId, tabName, entityHref);
            if (route) {
                if (route[0]=='#') route = route.substring(1);
                Backbone.history.navigate(route);
                Backbone.history.loadUrl(route);
            }
        }
    });
    return EntityDetailsView;
});