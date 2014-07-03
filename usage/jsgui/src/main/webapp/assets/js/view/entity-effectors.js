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
 * Render the effectors tab.You must supply the model and optionally the element
 * on which the view binds itself.
 *
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils", "model/effector-summary",
    "view/effector-invoke", "text!tpl/apps/effector.html", "text!tpl/apps/effector-row.html", "bootstrap"
], function (_, $, Backbone, ViewUtils, EffectorSummary, EffectorInvokeView, EffectorHtml, EffectorRowHtml) {

    var EntityEffectorsView = Backbone.View.extend({
        template:_.template(EffectorHtml),
        effectorRow:_.template(EffectorRowHtml),
        events:{
            "click .show-effector-modal":"showEffectorModal"
        },
        initialize:function () {
            this.$el.html(this.template({}))
            var that = this
            this._effectors = new EffectorSummary.Collection()
            // fetch the list of effectors and create a view for each one
            this._effectors.url = this.model.getLinkByName("effectors")
            that.loadedData = false;
            ViewUtils.fadeToIndicateInitialLoad(this.$('#effectors-table'));
            this.$(".has-no-effectors").hide();
            
            this._effectors.fetch({success:function () {
                that.loadedData = true;
                that.render()
                ViewUtils.cancelFadeOnceLoaded(that.$('#effectors-table'));
            }})
            // attach a fetch simply to fade this tab when not available
            // (the table is statically rendered)
            ViewUtils.fetchRepeatedlyWithDelay(this, this._effectors, { period: 10*1000 })
        },
        render:function () {
            if (this.viewIsClosed)
                return;
            var that = this
            var $tableBody = this.$('#effectors-table tbody').empty()
            if (this._effectors.length==0) {
                if (that.loadedData)
                    this.$(".has-no-effectors").show();
            } else {                
                this.$(".has-no-effectors").hide();
                this._effectors.each(function (effector) {
                    $tableBody.append(that.effectorRow({
                        name:effector.get("name"),
                        description:effector.get("description"),
                        // cid is mapped to id (here) which is mapped to name (in Effector.Summary), 
                        // so it is consistent across resets
                        cid:effector.id
                    }))
                })
            }
            return this
        },
        showEffectorModal:function (eventName) {
            // get the model that we need to show, create its view and show it
            var cid = $(eventName.currentTarget).attr("id")
            var effectorModel = this._effectors.get(cid);
            var modal = new EffectorInvokeView({
                el:"#effector-modal",
                model:effectorModel,
                entity:this.model,
                tabView:this.options.tabView,
                openTask:true
            })
            modal.render().$el.modal('show')
        }
    })
    return EntityEffectorsView
})