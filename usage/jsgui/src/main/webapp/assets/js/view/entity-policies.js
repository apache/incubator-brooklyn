/**
 * Render the policies tab.You must supply the model and optionally the element
 * on which the view binds itself.
 *
 * @type {*}
 */
define([
            "underscore", "jquery", "backbone",
            "model/policy-summary",
            "model/policy-config-summary",
            "view/policy-config-invoke",
            "text!tpl/apps/policy.html",
            "text!tpl/apps/policy-row.html",
            "text!tpl/apps/policy-config-row.html",
            "bootstrap"
], function (
                _, $, Backbone,
                PolicySummary,
                PolicyConfigSummary,
                PolicyConfigInvokeView,
                PolicyHtml,
                PolicyRowHtml,
                PolicyConfigRowHtml
        ) {

    var EntityPoliciesView = Backbone.View.extend({
        template:_.template(PolicyHtml),
        policyRow:_.template(PolicyRowHtml),
        events:{
            "click #policies-table tr":"rowClick",
            "click .policy-start":"callStart",
            "click .policy-stop":"callStop",
            "click .policy-destroy":"callDestroy",
            "click .show-policy-config-modal":"showPolicyConfigModal",
        },
        initialize:function () {
            this.$el.html(this.template({}))
            var that = this
            this._policies = new PolicySummary.Collection()
            // fetch the list of policies and create a view for each one
            this._policies.url = this.model.getLinkByName("policies")
            this.refresh()
        },
        refresh: function() {
            var that = this
            this._policies.fetch({success:function () {
                that.render()
            }})
        },
        render:function () {
            var that = this,
                $tbody = this.$('#policies-table tbody').empty()
            if (this._policies.length==0) {
                this.$(".has-no-policies").show();
                this.$("#policy-config").hide()
                this.$("#policy-config-none-selected").hide()
            } else {
                this.$(".has-no-policies").hide();
                this._policies.each(function (policy) {
                    $tbody.append(that.policyRow({
                        cid:policy.get("id"),
                        name:policy.get("name"),
                        state:policy.get("state"),
                        summary:policy
                    }))
                if (that.activePolicy) {
                    $("#policies-table tr[id='"+that.activePolicy+"']").addClass("selected")
                    that.showPolicyConfig(that.activePolicy)
                } else {
                    this.$("#policy-config").hide()
                    this.$("#policy-config-none-selected").show()
                }
            })
            }
            return this
        },
        rowClick: function(evt) {
            var row = $(evt.currentTarget).closest("tr")
            var id = row.attr("id")
            $("#policies-table tr").removeClass("selected")
            if (this.activePolicy == id) {
                // deselected
                this.activePolicy = null
                this.$("#policy-config").hide(100)
                this.$("#policy-config-none-selected").show(100)
            } else {
                row.addClass("selected")
                var that = this
                this.activePolicy = id
                // fetch the list of policy config entries
                var policy = this._policies.get(id)
                this._config = new PolicyConfigSummary.Collection()
                this._config.url = policy.getLinkByName("config")
                this._config.fetch({success:function () {
                    that.showPolicyConfig()
                }})
            }
        },
        showPolicyConfig:function () {
            var that = this
            this.$("#policy-config-none-selected").hide(100)
            var $tc = this.$('#policy-config-table tbody').empty()
            if (this._config.length==0) {
                this.$(".has-no-policy-config").show();
            } else {
                this.$(".has-no-policy-config").hide();
                var policyConfigRow = _.template(PolicyConfigRowHtml)
                this._config.each(function (config) {
                    $tc.append(policyConfigRow({
                        cid:config.cid,
                        name:config.get("name"),
                        description:config.get("description"),
                        type:config.get("type"),
                        reconfigurable:config.get("reconfigurable"),
                        link:config.getLinkByName('self'),
                        value:"" // config.get("defaultValue"), /* will be re-set later */
                    }))
                    // TODO tooltip doesn't work on 'i' elements in table (bottom left toolbar)
                    $tc.find('*[rel="tooltip"]').tooltip()
                })
            }
            this.$("#policy-config").show(100)
            this.$("#policy-config-table").dataTable().show(100)
            var currentStateUrl = this._policies.get(that.activePolicy).getLinkByName("config") + "/current-state"
            that.refreshPolicyConfig(currentStateUrl)
            this.callPeriodically("entity-policy-config", function() {
                that.refreshPolicyConfig(currentStateUrl)
            }, 3000)
        },
        refreshPolicyConfig:function (currentStateUrl) {
            var that = this,
                $table = that.$('#policy-config-table'),
                $rows = that.$("tr.policy-config-row")
            $.get(currentStateUrl, function (data) {
                    // iterate over the sensors table and update each sensor
                    $rows.each(function (index, row) {
                        var key = $(this).find(".policy-config-name").text()
                        var v = data[key]
                        if (v === undefined) v = ""
                        $table.dataTable().fnUpdate(_.escape(v), row, 1)
                        that._config.at(index).set("value", v)
                    })
                })
        },
        showPolicyConfigModal:function (evt) {
            // get the model that we need to show, create its view and show it
            var cid = $(evt.currentTarget).attr("id")
            this._modal = new PolicyConfigInvokeView({
                el:"#policy-config-modal",
                model:this._config.getByCid(cid),
                policy:this.model,
            })
            var a = this._modal.render()
            a.$el.show()
            a.$el.modal('show')
        },
        callStart:function(event) { this.doPost(event, "start") },
        callStop:function(event) { this.doPost(event, "stop") },
        callDestroy:function(event) { this.doPost(event, "destroy") },
        doPost:function(event, linkname) {
            var that = this
            var url = $(event.currentTarget).attr("link");
            // trigger the event by ajax with attached parameters
            $.ajax({
                type:"POST",
                url:url,
                success: function() {
                    that.refresh()
                }})
        }
    })

    return EntityPoliciesView
})