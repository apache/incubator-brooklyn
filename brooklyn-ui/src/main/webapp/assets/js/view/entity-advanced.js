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
    "text!tpl/apps/advanced.html", "view/change-name-invoke", "view/add-child-invoke", "view/policy-new"
], function(_, $, Backbone, Brooklyn, Util, ViewUtils,
        AdvancedHtml, ChangeNameInvokeView, AddChildInvokeView, NewPolicyView) {
    var EntityAdvancedView = Backbone.View.extend({
        events: {
            "click button#change-name": "showChangeNameModal",
            "click button#add-child": "showAddChildModal",
            "click button#add-new-policy": "showNewPolicyModal",
            "click button#reset-problems": "confirmResetProblems",
            "click button#expunge": "confirmExpunge",
            "click button#unmanage": "confirmUnmanage",
            "click #advanced-tab-error-closer": "closeAdvancedTabError"
        },
        template: _.template(AdvancedHtml),
        initialize:function() {
            _.bindAll(this);
            this.$el.html(this.template());

            this.model.on('change', this.modelChange, this);
            this.modelChange();
            
            ViewUtils.getRepeatedlyWithDelay(this, this.model.get('links').locations, this.renderLocationData);
            ViewUtils.get(this, this.model.get('links').tags, this.renderTags);
            
            ViewUtils.attachToggler(this.$el);
        },
        modelChange: function() {
            this.$('#entity-name').html(Util.toDisplayString(this.model.get("name")));
            ViewUtils.updateTextareaWithData($("#advanced-entity-json", this.$el), Util.toTextAreaString(this.model), true, false, 250, 600);
        },
        renderLocationData: function(data) {
            ViewUtils.updateTextareaWithData($("#advanced-locations", this.$el), Util.toTextAreaString(data), true, false, 250, 600);
        },
        renderTags: function(data) {
            var list = "";
            for (tag in data)
                list += "<div class='activity-tag-giftlabel'>"+Util.toDisplayString(data[tag])+"</div>";
            if (!list) list = "No tags";
            this.$('#advanced-entity-tags').html(list);
        },
        reload: function() {
            this.model.fetch();
        },
        
        showModal: function(modal) {
            if (this.activeModal)
                this.activeModal.close();
            this.activeModal = modal;
            Brooklyn.view.showModalWith(modal);
        },
        showChangeNameModal: function() {
            this.showModal(new ChangeNameInvokeView({
                entity: this.model,
                target:this
            }));
        },
        showAddChildModal: function() {
            this.showModal(new AddChildInvokeView({
                entity: this.model,
                target:this
            }));
        },
        showNewPolicyModal: function () {
            this.showModal(new NewPolicyView({
                entity: this.model,
            }));
        },
        
        confirmResetProblems: function () {
            var entity = this.model.get("name");
            var title = "Confirm the reset of problem indicators in " + entity;
            var q = "<p>Are you sure you want to reset the problem indicators for this entity?</p>" +
                "<p>If a problem has been fixed externally, but the fix is not being detected, this will clear problems. " +
                "If the problem is not actually fixed, many feeds and enrichers will re-detect it, but note that some may not, " +
                "and the entity may show as healthy when it is not." +
                "</p>";
            Brooklyn.view.requestConfirmation(q, title).done(this.doResetProblems);
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
                error: function(response) {
                    self.showAdvancedTabError(Util.extractError(response, "Error contacting server", url));
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
            self.$("#advanced-tab-error-section").addClass("hide");
        },
        
        beforeClose:function() {
            if (this.activeModal)
                this.activeModal.close();
            this.options.tabView.configView.close();
            this.model.off();
        }
    });
    return EntityAdvancedView;
});