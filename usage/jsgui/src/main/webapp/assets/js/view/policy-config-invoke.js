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
    "text!tpl/apps/policy-config-modal.html",
    "bootstrap"
], function (_, $, Backbone, PolicyConfigModalHtml) {

    var PolicyConfigInvokeView = Backbone.View.extend({
        template: _.template(PolicyConfigModalHtml),
        events:{
            "click .save-policy-config":"savePolicyConfig",
            "shown": "onShow",
            "hide": "onHide"
        },

        onShow: function() {
            this.delegateEvents();
            this.$el.fadeTo(500,1);
        },

        onHide: function() {
            this.undelegateEvents();
        },

        render:function () {
            var that = this,
                configUrl = that.model.getLinkByName("self");
            $.get(configUrl, function (data) { 
                that.$el.html(that.template({
                    name:that.model.get("name"),
                    description:that.model.get("description"),
                    type:that.model.get("type"),
                    value:data,
                    policyName:that.options.policy.get("name")
                }));
            });
            that.model = this.model;
            return that;
        },

        savePolicyConfig:function () {
            var that = this,
                url = that.model.getLinkByName("self") + "/set",
                val = that.$("#policy-config-value").val();
            that.$el.fadeTo(500,0.5);
            $.ajax({
                type:"POST",
                url:url+"?value="+val,
                success:function (data) {
                    that.$el.modal("hide");
                    that.$el.fadeTo(500,1);
                },
                error:function(data) {
                    that.$el.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                    // TODO render the error better than poor-man's flashing
                    console.error("ERROR setting config");
                    console.debug(data);
                }});
            // un-delegate events
            that.undelegateEvents();
        }
    });
    return PolicyConfigInvokeView;
});
