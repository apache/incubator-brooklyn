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
 * Render as a modal
 */
define([
    "underscore", "jquery", "backbone", "brooklyn", "brooklyn-utils", "view/viewutils",
    "text!tpl/apps/add-child-modal.html"
], function(_, $, Backbone, Brooklyn, Util, ViewUtils, 
        AddChildModalHtml) {
    return Backbone.View.extend({
        template: _.template(AddChildModalHtml),
        initialize: function() {
            this.title = "Add Child to "+this.options.entity.get('name');
        },
        render: function() {
            this.$el.html(this.template(this.options.entity.attributes));
            return this;
        },
        onSubmit: function (event) {
            var self = this;
            var childSpec = this.$("#child-spec").val();
            var start = this.$("#child-autostart").is(":checked");
            var url = this.options.entity.get('links').children + (!start ? "?start=false" : "");
            var ajax = $.ajax({
                type: "POST",
                url: url,
                data: childSpec,
                contentType: "application/yaml",
                success: function() {
                    self.options.target.reload();
                },
                error: function(response) {
                    self.showError(Util.extractError(response, "Error contacting server", url));
                }
            });
            return ajax;
        },
        showError: function (message) {
            this.$(".child-add-error-container").removeClass("hide");
            this.$(".child-add-error-message").html(message);
        }

    });
});
