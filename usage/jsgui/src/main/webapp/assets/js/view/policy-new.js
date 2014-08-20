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
 * Render a policy configuration key as a modal for reconfiguring.
 */
define([
    "jquery", "underscore", "backbone", "brooklyn",
    "text!tpl/apps/policy-new.html"
], function ($, _, Backbone, Brooklyn, NewPolicyHtml) {

    return Backbone.View.extend({
        template: _.template(NewPolicyHtml),

        initialize: function () {
            if (!this.options.entity) {
                throw new Error("NewPolicy view requires entity to know where to post result");
            }
        },

        render: function() {
            this.$el.html(this.template);
            this.configKeyView = new Brooklyn.view.ConfigKeyInputPairList();
            this.$(".policy-add-config-keys").html(this.configKeyView.render().$el);
            return this;
        },

        beforeClose: function() {
            if (this.configKeyView) {
                this.configKeyView.close();
            }
        },

        onSubmit: function (event) {
            var type = this.$("#policy-add-type").val();
            var config = this.configKeyView.getConfigKeys();
            console.log("type", type, "config", config);
            // Required because request isn't handled correctly if the map is empty.
            // See comments on PolicyApi.addPolicy for details.
            if (_.isEmpty(config)) {
                config["___d_dummy"] = "dummyval";
            }
            var url = this.options.entity.get("links").policies + "/?type=" + type;
            var self = this;
            return $.ajax({
                url: url,
                type: "post",
                data: JSON.stringify(config),
                contentType: "application/json"
            }).fail(function (response) {
                var message = JSON.parse(response.responseText).message;
                self.showError(message);
            });
        },

        showError: function (message) {
            this.$(".policy-add-error-container").removeClass("hide");
            this.$(".policy-add-error-message").html(message);
        }
    });

});