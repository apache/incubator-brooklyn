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
define([
    "underscore", "jquery", "backbone", "brooklyn",
    "text!tpl/apps/lifecycle.html"
], function(
    _, $, Backbone, Brooklyn, LifecycleHtml
) {

    var EntityLifecycleView = Backbone.View.extend({
        template: _.template(LifecycleHtml),

        events: {
            "click #lifecycle-expunge": "confirmExpunge",
            "click #lifecycle-unmanage": "confirmUnmanage"
        },

        initialize:function() {
            _.bindAll(this);
            this.$el.html(this.template());
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

        confirmExpunge: function () {
            var entity = this.model.get("name");
            var title = "Confirm the expunging of " + entity;
            var q = "<p>Are you certain you want to expunge this entity?</p>" +
                "<p>When possible, Brooklyn will delete all of its resources.</p>" +
                "<p><span class='label label-important'>Important</span> " +
                "<b>This action is irreversible</b></p>";
            this.unmanageAndOrExpunge(q, title, true);
        },

        unmanageAndOrExpunge: function (question, title, releaseResources) {
            var self = this;
            Brooklyn.view.requestConfirmation(question, title).done(function() {
                return $.ajax({
                    type: "POST",
                    url: self.model.get("links").expunge + "?release=" + releaseResources + "&timeout=0",
                    contentType: "application/json"
                }).done(function() {
                    self.trigger("entity.expunged")
                }).fail(function() {
                    // (would just be connection error -- with timeout=0 we get a task even for invalid input)
                    var msg = self.$(".lifecycle-error-message").removeClass("hide");
                    setTimeout(function() { msg.fadeOut(); }, 4000);
                });
            });
        }

    });

    return EntityLifecycleView;

});