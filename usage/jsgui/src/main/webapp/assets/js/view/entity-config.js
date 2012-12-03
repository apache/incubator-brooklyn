/**
 * Render entity config tab.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils", "model/config-summary", "text!tpl/apps/config.html",
    "text!tpl/apps/config-row.html", "tablesorter"
], function (_, $, Backbone, ViewUtils, ConfigSummary, ConfigHtml, ConfigRowHtml) {

    var EntityConfigView = Backbone.View.extend({
        template:_.template(ConfigHtml),
        configTemplate:_.template(ConfigRowHtml),
        events:{
            'click .refresh':'refreshConfig',
            'click .filterEmpty':'toggleFilterEmpty'
        },
        initialize:function () {
            this.$el.html(this.template({}))
            var configCollection = new ConfigSummary.Collection,
                $table = this.$('#config-table'),
                $tableBody = this.$('tbody').empty(),
                that = this
            this.viewUtils = new ViewUtils({})
            configCollection.url = this.model.getLinkByName('config')
            var success = function () {
                configCollection.each(function (config) {
                    $tableBody.append(that.configTemplate({
                        name:config.get("name"),
                        description:config.get("description"),
                        value:'',
                        type:config.get("type")
                    }))
                })
                that.updateConfigPeriodically(that)
                that.viewUtils.myDataTable($table)
                // TODO tooltip doesn't work on 'i' elements in table (bottom left toolbar)
                $table.find('*[rel="tooltip"]').tooltip()
            }
            configCollection.fetch({async:false, success:success})
            this.toggleFilterEmpty()
        },
        render:function () {
            return this
        },
        toggleFilterEmpty: function() {
            this.viewUtils.toggleFilterEmpty(this.$('#config-table'), 1)
        },
        refreshConfig:function () {
            this.updateConfigNow(this);  
        },
        // register a callback to update the sensors
        updateConfigPeriodically:function (that) {
            var self = this;
            that.updateConfigNow(that)
            that.callPeriodically("entity-config", function() { self.updateConfigNow(that) }, 3000)
        },
        updateConfigNow:function (that) {
            // NB: this won't add new dynamic config
            var $table = this.$('#config-table');
            var url = that.model.getConfigUpdateUrl(),
            $rows = that.$("tr.config-row")
                $.get(url, function (data) {
                    // iterate over the config table and update each value
                    $rows.each(function (index,row) {
                        var key = $(this).find(".config-name").text()
                        var v = data[key]
                        if (v === undefined) v = ''
                        $table.dataTable().fnUpdate(_.escape(v), row, 1)
                    })
                })
            }
        })
    return EntityConfigView
})
