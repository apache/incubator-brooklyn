/**
 * Render entity sensors tab.
 *
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils", "model/sensor-summary", "text!tpl/apps/sensors.html",
    "text!tpl/apps/sensor-row.html", "tablesorter", "brooklyn-utils"
], function (_, $, Backbone, ViewUtils, SensorSummary, SensorsHtml, SensorRowHtml) {

    var EntitySensorsView = Backbone.View.extend({
        template:_.template(SensorsHtml),
        sensorTemplate:_.template(SensorRowHtml),
        events:{
            'click .refresh':'refreshSensors',
            'click .filterEmpty':'toggleFilterEmpty'
        },
        initialize:function () {
            this.$el.html(this.template({ }));
            var that = this,
                sensorsCollection = new SensorSummary.Collection,
                $table = $('#sensors-table'),
                $tableBody = $('tbody').empty();
            ViewUtils.myDataTable($table);
            sensorsCollection.url = that.model.getLinkByName('sensors');
            var success = function () {
                sensorsCollection.each(function (sensor) {
                    var actions = {};
                    _.each(sensor.get("links"), function(v,k) {
                        if (k.slice(0, 7) == "action:") {
                            actions[k.slice(7)] = v;
                        }
                    });
                    $tableBody.append(that.sensorTemplate({
                        name:sensor.get("name"),
                        description:sensor.get("description"),
                        actions:actions,
                        type:sensor.get("type"),
                        value:'' /* will be set later */
                    }));
                });
                that.updateSensorsPeriodically(that);
                // TODO tooltip doesn't work on 'i' elements in table (bottom left toolbar)
                $table.find('*[rel="tooltip"]').tooltip();
            };
            sensorsCollection.fetch({ async:false, success:success });
            this.toggleFilterEmpty();
        },
        render:function () {
            return this;
        },
        toggleFilterEmpty:function() {
            ViewUtils.toggleFilterEmpty($('#sensors-table'), 2);
        },
        refreshSensors:function () {
            this.updateSensorsNow(this);  
        },
        // register a callback to update the sensors
        updateSensorsPeriodically:function (that) {
            var that = this;
            that.updateSensorsNow(that);
            that.callPeriodically("entity-sensors", function() {
                that.updateSensorsNow(that);
            }, 3000);
        },
        updateSensorsNow:function (that) {
            // NB: this won't add new dynamic sensors
            var $table = this.$('#sensors-table');
            var url = that.model.getSensorUpdateUrl(),
                $rows = that.$("tr.sensor-row")
            $.get(url, function (data) {
                    // iterate over the sensors table and update each sensor
                    $rows.each(function (index,row) {
                        var key = $(this).find(".sensor-name").text()
                        var v = data[key]
                        if (v === undefined) v = ''
                        $table.dataTable().fnUpdate(_.escape(v), row, 2)
                    })
                })
        }
    });
    return EntitySensorsView;
});
