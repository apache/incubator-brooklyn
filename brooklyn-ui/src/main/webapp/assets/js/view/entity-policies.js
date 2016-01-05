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
 * Render the policies tab. You must supply the model and optionally the element
 * on which the view binds itself.
 */
define([
    "underscore", "jquery", "backbone", "brooklyn",
    "model/policy-summary", "model/policy-config-summary",
    "view/viewutils", "view/policy-config-invoke", "view/policy-new",
    "text!tpl/apps/policy.html", "text!tpl/apps/policy-row.html", "text!tpl/apps/policy-config-row.html",
    "jquery-datatables", "datatables-extensions"
], function (_, $, Backbone, Brooklyn,
        PolicySummary, PolicyConfigSummary,
        ViewUtils, PolicyConfigInvokeView, NewPolicyView,
        PolicyHtml, PolicyRowHtml, PolicyConfigRowHtml) {

    var EntityPoliciesView = Backbone.View.extend({

        template: _.template(PolicyHtml),
        policyRow: _.template(PolicyRowHtml),

        events:{
            'click .refresh':'refreshPolicyConfigNow',
            'click .filterEmpty':'toggleFilterEmpty',
            "click #policies-table tr":"rowClick",
            "click .policy-start":"callStart",
            "click .policy-stop":"callStop",
            "click .policy-destroy":"callDestroy",
            "click .show-policy-config-modal":"showPolicyConfigModal",
            "click .add-new-policy": "showNewPolicyModal"
        },

        initialize:function () {
            _.bindAll(this)
            this.$el.html(this.template({ }));
            var that = this;
            // fetch the list of policies and create a row for each one
            that._policies = new PolicySummary.Collection();
            that._policies.url = that.model.getLinkByName("policies");
            
            this.loadedData = false;
            ViewUtils.fadeToIndicateInitialLoad(this.$('#policies-table'));
            that.render();
            this._policies.on("all", this.render, this)
            ViewUtils.fetchRepeatedlyWithDelay(this, this._policies, {
                doitnow: true,
                success: function () {
                    that.loadedData = true;
                    ViewUtils.cancelFadeOnceLoaded(that.$('#policies-table'));
                }});
        },

        render:function () {
            if (this.viewIsClosed)
                return;
            var that = this,
                $tbody = this.$('#policies-table tbody').empty();
            if (that._policies.length==0) {
                if (this.loadedData)
                    this.$(".has-no-policies").show();
                this.$("#policy-config").hide();
                this.$("#policy-config-none-selected").hide();
            } else {
                this.$(".has-no-policies").hide();
                that._policies.each(function (policy) {
                    // TODO better to use datatables, and a json array, as we do elsewhere
                    $tbody.append(that.policyRow({
                        cid:policy.get("id"),
                        name:policy.get("name"),
                        state:policy.get("state"),
                        summary:policy
                    }));
                    if (that.activePolicy) {
                        that.$("#policies-table tr[id='"+that.activePolicy+"']").addClass("selected");
                        that.showPolicyConfig(that.activePolicy);
                        that.refreshPolicyConfig();
                    } else {
                        that.$("#policy-config").hide();
                        that.$("#policy-config-none-selected").show();
                    }
                });
            }
            return that;
        },

        toggleFilterEmpty:function() {
            ViewUtils.toggleFilterEmpty($('#policy-config-table'), 2);
        },

        refreshPolicyConfigNow:function () {
            this.refreshPolicyConfig();  
        },

        rowClick:function(evt) {
            evt.stopPropagation();
            var row = $(evt.currentTarget).closest("tr"),
                id = row.attr("id"),
                policy = this._policies.get(id);
            $("#policies-table tr").removeClass("selected");
            if (this.activePolicy == id) {
                // deselected
                this.activePolicy = null;
                this._config = null;
                $("#policy-config-table").dataTable().fnDestroy();
                $("#policy-config").slideUp(100);
                $("#policy-config-none-selected").slideDown(100);
            } else {
                row.addClass("selected");
                // fetch the list of policy config entries
                this._config = new PolicyConfigSummary.Collection();
                this._config.url = policy.getLinkByName("config");
                ViewUtils.fadeToIndicateInitialLoad($('#policy-config-table'));
                this.showPolicyConfig(id);
                var that = this;
                this._config.fetch().done(function () {
                    that.showPolicyConfig(id);
                    ViewUtils.cancelFadeOnceLoaded($('#policy-config-table'))
                });
            }
        },

        showPolicyConfig:function (activePolicyId) {
            var that = this;
            if (activePolicyId != null && that.activePolicy != activePolicyId) {
                // TODO better to use a json array, as we do elsewhere
                var $table = $('#policy-config-table'),
                    $tbody = $table.find('tbody');
                $table.dataTable().fnClearTable();
                $("#policy-config-none-selected").slideUp(100);
                if (that._config.length==0) {
                    $(".has-no-policy-config").show();
                } else {
                    $(".has-no-policy-config").hide();
                    that.activePolicy = activePolicyId;
                    var policyConfigRow = _.template(PolicyConfigRowHtml);
                    that._config.each(function (config) {
                        $tbody.append(policyConfigRow({
                            cid:config.cid,
                            name:config.get("name"),
                            description:config.get("description"),
                            type:config.get("type"),
                            reconfigurable:config.get("reconfigurable"),
                            link:config.getLinkByName('self'),
                            value: config.get("defaultValue")
                        }));
                        $tbody.find('*[rel="tooltip"]').tooltip();
                    });
                    that.currentStateUrl = that._policies.get(that.activePolicy).getLinkByName("config") + "/current-state";
                    $("#policy-config").slideDown(100);
                    $table.slideDown(100);
                    ViewUtils.myDataTable($table, {
                        "bAutoWidth": false,
                        "aoColumns" : [
                            { sWidth: '220px' },
                            { sWidth: '240px' },
                            { sWidth: '25px' }
                        ]
                    });
                    $table.dataTable().fnAdjustColumnSizing();
                }
            }
            that.refreshPolicyConfig();
        },

        refreshPolicyConfig:function() {
            var that = this;
            if (that.viewIsClosed || !that.currentStateUrl) return;
            var $table = that.$('#policy-config-table').dataTable(),
                $rows = that.$("tr.policy-config-row");
            $.get(that.currentStateUrl, function (data) {
                if (that.viewIsClosed) return;
                // iterate over the sensors table and update each sensor
                $rows.each(function (index, row) {
                    var key = $(this).find(".policy-config-name").text();
                    var v = data[key];
                    if (v !== undefined) {
                        $table.fnUpdate(_.escape(v), row, 1, false);
                    }
                });
            });
            $table.dataTable().fnStandingRedraw();
        },

        showPolicyConfigModal: function (evt) {
            var cid = $(evt.currentTarget).attr("id");
            var currentValue = $(evt.currentTarget)
                .parent().parent()
                .find(".policy-config-value")
                .text();
            Brooklyn.view.showModalWith(new PolicyConfigInvokeView({
                model: this._config.get(cid),
                policy: this.model,
                currentValue: currentValue
            }));
        },

        showNewPolicyModal: function () {
            var self = this;
            Brooklyn.view.showModalWith(new NewPolicyView({
                entity: this.model,
                onSave: function (policy) {
                    console.log("New policy", policy);
                    self._policies.add(policy);
                }
            }));
        },

        callStart:function(event) { this.doPost(event, "start"); },
        callStop:function(event) { this.doPost(event, "stop"); },
        callDestroy:function(event) { this.doPost(event, "destroy"); },
        doPost:function(event, linkname) {
            event.stopPropagation();
            var that = this,
                url = $(event.currentTarget).attr("link");
            $.ajax({
                type:"POST",
                url:url,
                success:function() {
                    that._policies.fetch();
                }
            });
        }

    });

    return EntityPoliciesView;
});
