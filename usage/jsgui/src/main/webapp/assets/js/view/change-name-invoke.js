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
 * Render entity expungement as a modal
 */
define([
    "underscore", "jquery", "backbone",
    "text!tpl/apps/change-name-modal.html"
], function(_, $, Backbone, ChangeNameModalHtml) {
    return Backbone.View.extend({
        template: _.template(ChangeNameModalHtml),
        events: {
            "click .invoke-operation": "invokeOperation",
            "hide": "hide"
        },
        render: function() {
            this.$el.html(this.template(this.model));
            return this;
        },
        invokeOperation: function() {
            var self = this;
            var newName = this.$("#new-name").val();
            var url = this.model.links.rename + "?name=" + encodeURIComponent(newName);
            $.ajax({
                type: "POST",
                url: url,
                contentType: "application/json",
                success: function() {
                    self.options.target.reload();
                },
                error: function(data) {
                    self.$el.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                    // TODO render the error better than poor-man's flashing
                    // (would just be connection error -- with timeout=0 we get a task even for invalid input)

                    log("ERROR invoking operation");
                    log(data);
                }
            });
            this.$el.fadeTo(500, 0.5);
            this.$el.modal("hide");
        },
        hide: function() {
            this.undelegateEvents();
        }
    });
});
