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
/**
 * Renders details information about an application (sensors, summary, effectors, etc.).
 * 
 * Options preselectTab (e.g. 'activities') and preselectTabDetails ('subtasks/1234') can be set
 * before a render to cause the given tab / details to be opened.
 * 
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
        getEntityHref: function() {
            return $("#app-tree .entity_tree_node_wrapper.active a").attr("href");
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
            var entityHref = this.getEntityHref();
            if (entityHref) {
                $("a[data-toggle='tab'").each(function(i,a) {
                    $(a).attr('href',entityHref+"/"+$(a).attr("data-target").slice(1));
                });
            } else {
                log("could not find entity href for tab");
            }
            if (this.options.preselectTab) {
                var tabLink = this.$('a[data-target="#'+this.options.preselectTab+'"]');
                var showFn = function() { tabLink.tab('show'); };
                if (optionalParent) showFn();
                else _.defer(showFn);
            }
            return this;
        },
        tabSelected: function(event) {
            // TODO: the bootstrap JS code still prevents shift-click from working
            // have to add the following logic to bootstrap tab click handler also
//            if (event.metaKey || event.shiftKey)
//                // trying to open in a new tab, do not act on it here!
//                return;
            event.preventDefault();
            
            var tabName = $(event.currentTarget).attr("data-target").slice(1);
            var route = this.getTab(tabName);
            if (route) {
                if (route[0]=='#') route = route.substring(1);
                Backbone.history.navigate(route);
            }
            // caller will ensure tab is shown
        },
        getTab: function(tabName, entityId, entityHref) {
            if (!entityHref) {
                if (entityId) {
                    entityHref = this.getEntityHref();
                    if (!entityHref.endsWith(entityId)) {
                        lastSlash = entityHref.lastIndexOf('/');
                        if (lastSlash>=0) {
                            entityHref = entityHref.substring(0, lastSlash+1) + '/' + entityId;
                        } else {
                            log("malformed entityHref when opening tab: "+entityHref)
                            entityHref = this.getEntityHref();
                        }
                    }
                } else {
                    entityHref = this.getEntityHref();
                }
            }
            if (entityHref && tabName)                
                return entityHref+"/"+tabName;
            return null;
        },
        /** for tabs to redirect to other tabs; entityId and entityHref are optional (can supply either, or null to use current entity); 
         * tabPath is e.g. 'sensors' or 'activities/subtask/1234' */ 
        openTab: function(tabPath, entityId, entityHref) {
            var route = this.getTab(tabPath, entityId, entityHref);
            if (!route) return;
            if (route[0]=='#') route = route.substring(1);
            Backbone.history.navigate(route);
                
            tabPaths = tabPath.split('/');
            if (!tabPaths) return;
            var tabName = tabPaths.shift();
            if (!tabName)
                // ignore leading /
                tabName = tabPaths.shift();
            if (!tabName) return;

            this.options.preselectTab = tabName;
            if (tabPaths)
                this.options.preselectTabDetails = tabPaths.join('/');
            this.render();
        }
    });
    return EntityDetailsView;
});