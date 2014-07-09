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
    "./application-add-wizard", "model/app-tree", "./application-tree", 
    "text!tpl/apps/page.html"
], function (_, $, Backbone, ViewUtils, AppAddWizard, AppTree, ApplicationTreeView, PageHtml) {

    var ApplicationExplorerView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        id:'application-explorer',
        template:_.template(PageHtml),
        events:{
            'click .application-tree-refresh': 'refreshApplicationsInPlace',
            'click #add-new-application':'createApplication',
            'click .delete':'deleteApplication'
        },
        initialize:function () {
            this.$el.html(this.template({}))
            $(".nav1").removeClass("active");
            $(".nav1_apps").addClass("active");

            this.treeView = new ApplicationTreeView({
                collection:this.collection,
                appRouter:this.options.appRouter
            })
            this.$('div#app-tree').html(this.treeView.renderFull().el)
            ViewUtils.fetchRepeatedlyWithDelay(this, this.collection)
        },
        refreshApplicationsInPlace: function() {
            // fetch without reset sets of change events, which now get handled correctly
            // (not a full visual recompute, which reset does - both in application-tree.js)
            this.collection.fetch();
        },
        beforeClose:function () {
            this.collection.off("reset", this.render)
            this.treeView.close()
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
            this.treeView.displayEntityId(entityId)
        },
        preselectTab: function(tab, tabDetails) {
            this.treeView.preselectTab(tab, tabDetails)
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
        }
    })

    return ApplicationExplorerView
})
