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
            "click .show-policy-config-modal":"showPolicyConfigModal"
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
            ViewUtils.fetchRepeatedlyWithDelay(this, this._policies,
                    { doitnow: true, success: function() {
                        that.loadedData = true;
                        ViewUtils.cancelFadeOnceLoaded(that.$('#policies-table'));
                    }})
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
                var that = this;
                // fetch the list of policy config entries
                that._config = new PolicyConfigSummary.Collection();
                that._config.url = policy.getLinkByName("config");
                ViewUtils.fadeToIndicateInitialLoad($('#policy-config-table'))
                that.showPolicyConfig(id);
                that._config.fetch({ success:function () {
                    that.showPolicyConfig(id);
                    ViewUtils.cancelFadeOnceLoaded($('#policy-config-table'))
                }});
            }
        },

        showPolicyConfig:function (activePolicyId) {
            var that = this;
            if (activePolicyId != null && that.activePolicy != activePolicyId) {
                // TODO better to use a json array, as we do elsewhere
                var $table = $('#policy-config-table'),
                    $tbody = $('#policy-config-table tbody').empty();
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
                            value:'' // will be set later
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
            if (that.viewIsClosed) return;
            var $table = that.$('#policy-config-table').dataTable(),
                $rows = that.$("tr.policy-config-row");
            $.get(that.currentStateUrl, function (data) {
                if (that.viewIsClosed) return;
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
            evt.stopPropagation();
            // get the model that we need to show, create its view and show it
            var cid = $(evt.currentTarget).attr("id");
            this._modal = new PolicyConfigInvokeView({
                el:"#policy-config-modal",
                model:this._config.get(cid),
                policy:this.model
            });
            this._modal.render().$el.modal('show');
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
