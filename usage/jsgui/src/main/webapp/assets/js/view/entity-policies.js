/**
 * Render the policies tab.You must supply the model and optionally the element
 * on which the view binds itself.
 *
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone",
    "model/policy-summary", "model/policy-config-summary", "view/viewutils", "view/policy-config-invoke", "text!tpl/apps/policy.html", "text!tpl/apps/policy-row.html", "text!tpl/apps/policy-config-row.html",
    "jquery-datatables", "datatables-extensions"
], function (_, $, Backbone, PolicySummary, PolicyConfigSummary, ViewUtils, PolicyConfigInvokeView, PolicyHtml, PolicyRowHtml, PolicyConfigRowHtml) {

    var EntityPoliciesView = Backbone.View.extend({
        template:_.template(PolicyHtml),
        policyRow:_.template(PolicyRowHtml),
        events:{
            'click .refresh':'refreshPolicyConfigNow',
            'click .filterEmpty':'toggleFilterEmpty',
            "click #policies-table tr":"rowClick",
            "click .policy-start":"callStart",
            "click .policy-stop":"callStop",
            "click .policy-destroy":"callDestroy",
            "click .show-policy-config-modal":"showPolicyConfigModal",
        },
        initialize:function () {
            this.$el.html(this.template({ }));
            $.ajaxSetup({ async:false });
            var that = this;
            // fetch the list of policies and create a view for each one
            that._policies = new PolicySummary.Collection();
            that._policies.url = that.model.getLinkByName("policies");
            that.render();
            that.callPeriodically("entity-policies", function() {
                that.refresh();
            }, 3000);
        },
        refresh:function() {
            var that = this;
            that._policies.fetch({ success:function () {
                that.render();
            }});
        },
        render:function () {
            var that = this,
                $tbody = $('#policies-table tbody').empty();
            if (that._policies.length==0) {
                $(".has-no-policies").show();
                $("#policy-config").hide();
                $("#policy-config-none-selected").hide();
            } else {
                $(".has-no-policies").hide();
                that._policies.each(function (policy) {
                    $tbody.append(that.policyRow({
                        cid:policy.get("id"),
                        name:policy.get("name"),
                        state:policy.get("state"),
                        summary:policy
                    }));
                    if (that.activePolicy) {
                        $("#policies-table tr[id='"+that.activePolicy+"']").addClass("selected");
                        that.showPolicyConfig(that.activePolicy);
                        that.refreshPolicyConfig(that);
                    } else {
                        $("#policy-config").hide();
                        $("#policy-config-none-selected").show();
                    }
                });
            }
            return that;
        },
        toggleFilterEmpty:function() {
            ViewUtils.toggleFilterEmpty($('#policy-config-table'), 2);
        },
        refreshPolicyConfigNow:function () {
            this.refreshPolicyConfig(this);  
        },
        rowClick:function(evt) {
            var row = $(evt.currentTarget).closest("tr"),
                id = row.attr("id"),
                policy = this._policies.get(id);
            $("#policies-table tr").removeClass("selected");
            if (this.activePolicy == id) {
                // deselected
            	this.activePolicy = null;
                this._config = null;
                $("#policy-config-table").dataTable().fnDestroy();
                $("#policy-config").hide(100);
                $("#policy-config-none-selected").show(100);
            } else {
                row.addClass("selected");
                var that = this;
                // fetch the list of policy config entries
                that._config = new PolicyConfigSummary.Collection();
                that._config.url = policy.getLinkByName("config");
                that._config.fetch({ success:function () {
                    that.showPolicyConfig(id);
                }});
            }
        },
        showPolicyConfig:function (activePolicyId) {
            var that = this;
            if (activePolicyId != null && that.activePolicy != activePolicyId) {
                var $table = $('#policy-config-table'),
                    $tbody = $('#policy-config-table tbody').empty();
                $("#policy-config-none-selected").hide(100);
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
                            value:'' // will be set later
                        }));
                        $tbody.find('*[rel="tooltip"]').tooltip();
                    });
                    that.currentStateUrl = that._policies.get(that.activePolicy).getLinkByName("config") + "/current-state";
                    $("#policy-config").show(100);
                    $table.show(100);
                    ViewUtils.myDataTable($table);
                    $table.dataTable().fnAdjustColumnSizing();
                }
            }
            that.refreshPolicyConfig(that);
            that.callPeriodically("entity-policy-config", function() {
                that.refreshPolicyConfig(that);
            }, 3000);
        },
        refreshPolicyConfig:function (that) {
            var $table = that.$('#policy-config-table').dataTable(),
                $rows = that.$("tr.policy-config-row");
            $.get(that.currentStateUrl, function (data) {
                // iterate over the sensors table and update each sensor
                $rows.each(function (index, row) {
                    var key = $(this).find(".policy-config-name").text();
                    var v = data[key];
                    if (v === undefined) v = "";
                    $table.fnUpdate(_.escape(v), row, 1, false);
                });
            });
            $table.dataTable().fnStandingRedraw();
        },
        showPolicyConfigModal:function (evt) {
            // get the model that we need to show, create its view and show it
            var cid = $(evt.currentTarget).attr("id");
            this._modal = new PolicyConfigInvokeView({
                el:"#policy-config-modal",
                model:this._config.getByCid(cid),
                policy:this.model,
            });
            this._modal.render().$el.modal('show');
        },
        callStart:function(event) { this.doPost(event, "start"); },
        callStop:function(event) { this.doPost(event, "stop"); },
        callDestroy:function(event) { this.doPost(event, "destroy"); },
        doPost:function(event, linkname) {
            var that = this,
                url = $(event.currentTarget).attr("link");
            $.ajax({
                type:"POST",
                url:url,
                success:function() {
                    that.refresh();
                }
            });
        }
    });
    return EntityPoliciesView;
});