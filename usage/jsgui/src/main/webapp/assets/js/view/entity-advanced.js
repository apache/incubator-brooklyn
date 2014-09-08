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
define(["underscore", "jquery", "backbone", "view/viewutils",
    "text!tpl/apps/advanced.html", "view/entity-config", "view/entity-lifecycle"
], function(_, $, Backbone, ViewUtils,
        AdvancedHtml, EntityConfigView, EntityLifecycleView) {
    var EntityAdvancedView = Backbone.View.extend({
        template: _.template(AdvancedHtml),
        initialize:function() {
            _.bindAll(this);
            this.$el.html(this.template());

            this.options.tabView.configView = new EntityConfigView({
                model:this.options.model,
                tabView:this.options.tabView,
            });
            this.$("div#advanced-config").html(this.options.tabView.configView.render().el);

            this.options.tabView.lifecycleView = new EntityLifecycleView({
                model: this.options.model,
                tabView:this.options.tabView,
                application:this.options.application
            });
            this.$("div#advanced-lifecycle").html(this.options.tabView.lifecycleView.render().el);

            ViewUtils.attachToggler(this.$el);
        },
        beforeClose:function() {
            this.options.tabView.configView.close();
            this.options.tabView.lifecycleView.close();
        }
    });
    return EntityAdvancedView;
});