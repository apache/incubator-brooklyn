/**
 * Render entity config tab.
 *
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone",
    "view/viewutils", "model/config-summary", "text!tpl/apps/config.html", "text!tpl/apps/config-row.html",
    "jquery-datatables", "datatables-fnstandingredraw"
], function (_, $, Backbone, ViewUtils, ConfigSummary, ConfigHtml, ConfigRowHtml) {

    var EntityConfigView = Backbone.View.extend({
        template:_.template(ConfigHtml),
        configTemplate:_.template(ConfigRowHtml),
        events:{
            'click .refresh':'refreshConfig',
            'click .filterEmpty':'toggleFilterEmpty'
        },
        initialize:function () {
        	this.$el.html(this.template({ }));
            $.ajaxSetup({ async:false });
            var that = this,
                configCollection = new ConfigSummary.Collection,
                $table = this.$('#config-table'),
                $tbody = this.$('tbody').empty();
            configCollection.url = that.model.getLinkByName('config');
            configCollection.fetch({ success:function () {
                configCollection.each(function (config) {
                    $tbody.append(that.configTemplate({
                        name:config.get("name"),
                        description:config.get("description"),
                        value:'', // will be set later
                        type:config.get("type")
                    }));
                });
                $tbody.find('*[rel="tooltip"]').tooltip();
                that.updateConfigPeriodically(that);
                ViewUtils.myDataTable($table);
                $table.dataTable().fnAdjustColumnSizing();
            }});
            that.toggleFilterEmpty();
        },
        render:function () {
            this.updateConfigNow(this);
            return this;
        },
        toggleFilterEmpty:function () {
            ViewUtils.toggleFilterEmpty(this.$('#config-table'), 1);
        },
        refreshConfig:function () {
            this.updateConfigNow(this);  
        },
        // register a callback to update the sensors
        updateConfigPeriodically:function (that) {
            var self = this;
            that.updateConfigNow(that);
            that.callPeriodically("entity-config", function() {
                self.updateConfigNow(that);
            }, 3000);
        },
        updateConfigNow:function (that) {
            // NB: this won't add new dynamic config
            var url = that.model.getConfigUpdateUrl(),
                $table = that.$('#config-table'),
                $rows = that.$("tr.config-row");
            $.get(url, function (data) {
                // iterate over the config table and update each value
                $rows.each(function (index, row) {
                    var key = $(this).find(".config-name").text();
                    var v = data[key];
                    if (v === undefined) v = '';
                    $table.dataTable().fnUpdate(_.escape(v), row, 1, false);
                });
            });
            $table.dataTable().fnStandingRedraw();
        }
    });
    return EntityConfigView;
});
