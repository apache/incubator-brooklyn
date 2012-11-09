/**
 * Render entity config tab.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "model/config-summary", "text!tpl/apps/config.html",
    "text!tpl/apps/config-row.html", "tablesorter"
], function (_, $, Backbone, ConfigSummary, ConfigHtml, ConfigRowHtml) {

    var EntityConfigView = Backbone.View.extend({
        template:_.template(ConfigHtml),
        configTemplate:_.template(ConfigRowHtml),
        initialize:function () {
            this.$el.html(this.template({}))
            var configCollection = new ConfigSummary.Collection,
                $table = this.$('#config-table'),
                $tableBody = this.$('tbody').empty(),
                that = this
            configCollection.url = this.model.getLinkByName('config')
            var success = function () {
                configCollection.each(function (config) {
                    console.log(config)
                    console.log(config.get("description"))
                    $tableBody.append(that.configTemplate({
                        name:config.get("name"),
                        description:config.get("description"),
                        value:'n/a',
                        type:config.get("type")
                    }))
                })
                that.updateConfigKeys(that)
                // call the table paging and sorting
                $table.dataTable({
                	"iDisplayLength": 25,
                    "oLanguage":{
                        "sLengthMenu":'Display <select>' +
                            '<option value="25">25</option>' +
                            '<option value="50">50</option>' +
                            '<option value="-1">All</option>' +
                            '</select> records'
                    }
                })
                $table.find('*[rel="tooltip"]').tooltip()
            }
            configCollection.fetch({async:false, success:success})
        },
        render:function () {
            // nothing to do
            return this
        },
        // register a callback to update the configs
        updateConfigKeys:function (that) {
            var func = function () {
                var url = that.model.getConfigUpdateUrl(),
                    $rows = that.$("tr.config-row")
                $.get(url, function (data) {
                    // iterate over the configs table and update each config
                    $rows.each(function (index) {
                        var key = $(this).find(".config-name").text()
                        var v = data[key]
                        if (v === undefined) v = ''
                        $(this).find(".config-value").html(_.escape(v))
                    })
                })
            }
            func() // update for initial values
            that.callPeriodically(func, 3000)
        }
    })
    return EntityConfigView
})