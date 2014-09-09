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
        initialize: function() {
            this.title = "Change Name of "+this.options.entity.get('name');
        },
        render: function() {
            this.$el.html(this.template({ name: this.options.entity.get('name') }));
            return this;
        },
        onSubmit: function() {
            var self = this;
            var newName = this.$("#new-name").val();
            var url = this.options.entity.get('links').rename + "?name=" + encodeURIComponent(newName);
            var ajax = $.ajax({
                type: "POST",
                url: url,
                contentType: "application/json",
                success: function() {
                    self.options.target.reload();
                },
                error: function(response) {
                    var message = "Error contacting server";
                    try {
                        message = JSON.parse(response.responseText).message;
                    } catch (e) {
                        log("UNPARSEABLE RESPONSE");
                        log(response);
                    }
                    self.showError(message);
                }
            });
            return ajax;
        },
        showError: function (message) {
            this.$(".change-name-error-container").removeClass("hide");
            this.$(".change-name-error-message").html(message);
        }
    });
});
