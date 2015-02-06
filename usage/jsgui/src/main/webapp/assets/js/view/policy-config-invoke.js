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
    "underscore", "jquery", "backbone",
    "text!tpl/apps/policy-parameter-config.html",
    "bootstrap"
], function (_, $, Backbone, PolicyParameterConfigHtml) {

    var PolicyConfigInvokeView = Backbone.View.extend({
        template: _.template(PolicyParameterConfigHtml),

        initialize: function () {
            _.bindAll(this);
        },

        render: function () {
            this.$el.html(this.template({
                name: this.model.get("name"),
                description: this.model.get("description"),
                type: this.model.get("type"),
                value: this.options.currentValue || "",
                policyName: this.options.policy.get("name")
            }));
            return this;
        },

        onSubmit: function () {
            var that = this,
                url = that.model.getLinkByName("self"),
                val = that.$("#policy-config-value").val();
            try {
                JSON.parse(val);
            } catch (e) {
                // ignore error, it's just unparseable, so put it in a string
                val = JSON.stringify(val);
            }
            return $.ajax({
                type: "POST",
                url: url,
                contentType:"application/json",
                data: val
            }).fail(function(response) {
                var message = JSON.parse(response.responseText).message;
                that.showError(message);
            });
        },

        showError: function (message) {
            this.$(".policy-add-error-container").removeClass("hide");
            this.$(".policy-add-error-message").html(message);
        },

        title: function () {
            return "Reconfigure " + this.options.policy.get("name");
        }
    });
    return PolicyConfigInvokeView;
});
