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
 * This should render the main content in the Application Explorer page.
 * Components on this page should be rendered as sub-views.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils", 
    "./application-add-wizard", "model/application", "model/entity-summary", "model/app-tree", "./application-tree",  "./entity-details",
    "text!tpl/apps/details.html", "text!tpl/apps/entity-not-found.html", "text!tpl/apps/page.html"
], function (_, $, Backbone, ViewUtils,
        AppAddWizard, Application, EntitySummary, AppTree, ApplicationTreeView, EntityDetailsView,
        EntityDetailsEmptyHtml, EntityNotFoundHtml, PageHtml) {

    var ApplicationExplorerView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        id:'application-explorer',
        template:_.template(PageHtml),
        notFoundTemplate: _.template(EntityNotFoundHtml),
        events:{
            'click .application-tree-refresh': 'refreshApplicationsInPlace',
            'click #add-new-application':'createApplication',
            'click .delete':'deleteApplication'
        },
        initialize: function () {
            this.$el.html(this.template({}))
            $(".nav1").removeClass("active");
            $(".nav1_apps").addClass("active");

            this.treeView = new ApplicationTreeView({
                collection:this.collection,
                appRouter:this.options.appRouter
            })
            this.treeView.on('entitySelected', function(e) {
               this.displayEntityId(e.id, e.get('applicationId'), false);
            }, this);
            this.$('div#app-tree').html(this.treeView.renderFull().el)
            this.$('div#details').html(EntityDetailsEmptyHtml);

            ViewUtils.fetchRepeatedlyWithDelay(this, this.collection)
        },
        refreshApplicationsInPlace: function() {
            // fetch without reset sets of change events, which now get handled correctly
            // (not a full visual recompute, which reset does - both in application-tree.js)
            this.collection.fetch();
        },
        beforeClose: function () {
            this.collection.off("reset", this.render);
            this.treeView.close();
            if (this.detailsView)
                this.detailsView.close();
        },
        show: function(entityId) {
            var tab = "";
            var tabDetails = "";
            if (entityId) {
                if (entityId[0]=='/') entityId = entityId.substring(1);
                var slash = entityId.indexOf('/');
                if (slash>0) {
                    tab = entityId.substring(slash+1)
                    entityId = entityId.substring(0, slash);
                }
            }
            if (tab) {
                var slash = tab.indexOf('/');
                if (slash>0) {
                    tabDetails = tab.substring(slash+1)
                    tab = tab.substring(0, slash);
                }
                this.preselectTab(tab, tabDetails);
            }
            this.treeView.selectEntity(entityId)
        },
        createApplication:function () {
            var that = this;
            if (this._modal) {
                this._modal.close()
            }
            var wizard = new AppAddWizard({
                appRouter:that.options.appRouter,
                callback:function() { that.refreshApplicationsInPlace() }
            })
            this._modal = wizard
            this.$(".add-app #modal-container").html(wizard.render().el)
            this.$(".add-app #modal-container .modal")
                .on("hidden",function () {
                    wizard.close()
                }).modal('show')
        },
        deleteApplication:function (event) {
            // call Backbone destroy() which does HTTP DELETE on the model
            this.collection.get(event.currentTarget['id']).destroy({wait:true})
        },
        /**
         * Causes the tab with the given name to be selected automatically when
         * the view is next rendered.
         */
        preselectTab: function(tab, tabDetails) {
            this.currentTab = tab;
            this.currentTabDetails = tabDetails;
        },
        showDetails: function(app, entitySummary) {
            var that = this;
            ViewUtils.cancelFadeOnceLoaded($("div#details"));

            var whichTab = this.currentTab;
            if (!whichTab) {
                whichTab = "summary";
                if (this.detailsView) {
                    whichTab = this.detailsView.$el.find(".tab-pane.active").attr("id");
                    this.detailsView.close();
                }
            }
            if (this.detailsView) {
                this.detailsView.close();
            }
            this.detailsView = new EntityDetailsView({
                model: entitySummary,
                application: app,
                appRouter: this.options.appRouter,
                preselectTab: whichTab,
                preselectTabDetails: this.currentTabDetails,
            });

            this.detailsView.on("entity.expunged", function() {
                that.preselectTab("summary");
                var id = that.selectedEntityId;
                var model = that.collection.get(id);
                if (model && model.get("parentId")) {
                    that.displayEntityId(model.get("parentId"));
                } else if (that.collection) {
                    that.displayEntityId(that.collection.first().id);
                } else if (id) {
                    that.displayEntityNotFound(id);
                } else {
                    that.displayEntityNotFound("?");
                }
                that.collection.fetch();
            });
            this.detailsView.render( $("div#details") );
        },
        displayEntityId: function (id, appName, afterLoad) {
            var that = this;
            var entityLoadFailed = function() {
                return that.displayEntityNotFound(id);
            };
            if (appName === undefined) {
                if (!afterLoad) {
                    // try a reload if given an ID we don't recognise
                    this.collection.includeEntities([id]);
                    this.collection.fetch({
                        success: function() { _.defer(function() { that.displayEntityId(id, appName, true); }); },
                        error: function() { _.defer(function() { that.displayEntityId(id, appName, true); }); }
                    });
                    ViewUtils.fadeToIndicateInitialLoad($("div#details"))
                    return;
                } else {
                    // no such app
                    entityLoadFailed();
                    return; 
                }
            }

            var app = new Application.Model();
            var entitySummary = new EntitySummary.Model;

            app.url = "/v1/applications/" + appName;
            entitySummary.url = "/v1/applications/" + appName + "/entities/" + id;

            // in case the server response time is low, fade out while it refreshes
            // (since we can't show updated details until we've retrieved app + entity details)
            ViewUtils.fadeToIndicateInitialLoad($("div#details"));

            $.when(app.fetch(), entitySummary.fetch())
                .done(function() {
                    that.showDetails(app, entitySummary);
                })
                .fail(entityLoadFailed);
        },
        displayEntityNotFound: function(id) {
            $("div#details").html(this.notFoundTemplate({"id": id}));
            ViewUtils.cancelFadeOnceLoaded($("div#details"))
        },
    })

    return ApplicationExplorerView
})
