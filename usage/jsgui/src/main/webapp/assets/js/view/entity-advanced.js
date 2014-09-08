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
 * Render entity advanced tab.
 *
 * @type {*}
 */
define(["underscore", "jquery", "backbone", "brooklyn", "brooklyn-utils", "view/viewutils",
    "text!tpl/apps/advanced.html", "view/entity-config", "view/change-name-invoke", "view/add-child-invoke"
], function(_, $, Backbone, Brooklyn, Util, ViewUtils,
        AdvancedHtml, EntityConfigView, ChangeNameInvokeView, AddChildInvokeView) {
    var EntityAdvancedView = Backbone.View.extend({
        events: {
            "click button#change-name": "showChangeNameModal",
            "click button#reset-problems": "doResetProblems",
            "click button#add-child": "showAddChildModal",
            "click button#expunge": "confirmExpunge",
            "click button#unmanage": "confirmUnmanage",
            "click #advanced-tab-error-closer": "closeAdvancedTabError"
        },
        template: _.template(AdvancedHtml),
        initialize:function() {
            _.bindAll(this);
            this.$el.html(this.template());

            this.options.tabView.configView = new EntityConfigView({
                model:this.options.model,
                tabView:this.options.tabView,
            });
            this.$("div#advanced-config").html(this.options.tabView.configView.render().el);

            this.model.on('change', this.modelChange, this);
            this.modelChange();
            
            ViewUtils.attachToggler(this.$el);
        },
        modelChange: function() {
            this.$('#entity-name').html(Util.toDisplayString(this.model.get("name")));
        },
        reload: function() {
            this.model.fetch();
        },
        
        showModel: function(modal) {
            if (this.activeModal)
                this.activeModal.$el.html("");
            // not sure why opacity is needed, but it seems to be, else jumping around makes it opacity 0.5
            modal.render().$el.modal("show").css('opacity', 1.0);
            this.activeModal = modal;
        },
        showChangeNameModal: function() {
            this.showModel(new ChangeNameInvokeView({
                el:"#change-name-modal",
                model:this.model.attributes,
                target:this
            }));
        },
        showAddChildModal: function() {
            this.showModel(new AddChildInvokeView({
                el:"#add-child-modal",
                model:this.model.attributes,
                target:this
            }));
        },
        doResetProblems: function() {
            this.post(this.model.get('links').sensors+"/"+"service.notUp.indicators", {});
            this.post(this.model.get('links').sensors+"/"+"service.problems", {});
        },
        post: function(url, data) {
            var self = this;
            
            $.ajax({
                type: "POST",
                url: url,
                data: JSON.stringify(data),
                contentType: "application/json",
                success: function() {
                    self.reload();
                },
                error: function(data) {
                    self.showAdvancedTabError("Error connecting to Brooklyn server");

                    log("ERROR invoking operation");
                    log(data);
                }
            });
        },
        
        confirmExpunge: function () {
            var entity = this.model.get("name");
            var title = "Confirm the expunging of " + entity;
            var q = "<p>Are you certain you want to expunge this entity?</p>" +
                "<p>When possible, Brooklyn will delete all of its resources.</p>" +
                "<p><span class='label label-important'>Important</span> " +
                "<b>This action is irreversible</b></p>";
            this.unmanageAndOrExpunge(q, title, true);
        },
        confirmUnmanage: function () {
            var entity = this.model.get("name");
            var title = "Confirm the unmanagement of " + entity;
            var q = "<p>Are you certain you want to unmanage this entity?</p>" +
            "<p>Its resources will be left running.</p>" +
            "<p><span class='label label-important'>Important</span> " +
            "<b>This action is irreversible</b></p>";
            this.unmanageAndOrExpunge(q, title, false);
        },
        unmanageAndOrExpunge: function (question, title, releaseResources) {
            var self = this;
            Brooklyn.view.requestConfirmation(question, title).done(function() {
                return $.ajax({
                    type: "POST",
                    url: self.model.get("links").expunge + "?release=" + releaseResources + "&timeout=0",
                    contentType: "application/json"
                }).done(function() {
                    self.trigger("entity.expunged");
                }).fail(function() {
                    // (would just be connection error -- with timeout=0 we get a task even for invalid input)
                    self.showAdvancedTabError("Error connecting to Brooklyn server");
                    
                    log("ERROR unmanaging/expunging");
                    log(data);
                });
            });
        },

        showAdvancedTabError: function(errorMessage) {
            self.$("#advanced-tab-error-message").html(_.escape(errorMessage));
            self.$("#advanced-tab-error-section").removeClass("hide");
        },
        closeAdvancedTabError: function() {
            log("close")
            self.$("#advanced-tab-error-section").addClass("hide");
        },
        
        beforeClose:function() {
            if (this.activeModal)
                this.activeModal.close();
            this.options.tabView.configView.close();
        }
    });
    return EntityAdvancedView;
});