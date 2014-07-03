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
 * Render entity lifecycle tab.
 *
 * @type {*}
 */
define(["underscore", "jquery", "backbone",
    "text!tpl/apps/lifecycle.html", "view/expunge-invoke"
], function(_, $, Backbone, LifecycleHtml, ExpungeInvokeView) {
    var EntityLifecycleView = Backbone.View.extend({
        template: _.template(LifecycleHtml),
        events: {
            "click #expunge": "showExpungeModal"
        },
        initialize:function() {
            _.bindAll(this);
            this.$el.html(this.template());
        },
        showExpungeModal: function() {
            var self = this;
            var modal = new ExpungeInvokeView({
                el:"#expunge-modal",
                model:this.model.attributes
            });
            modal.on("entity.expunged", function() {
                self.trigger("entity.expunged");
            });
            modal.render().$el.modal("show");
            this.expungeModal = modal;
        },
        beforeClose: function() {
            if (this.expungeModal)
                this.expungeModal.close();
        }
    });
    return EntityLifecycleView;
});