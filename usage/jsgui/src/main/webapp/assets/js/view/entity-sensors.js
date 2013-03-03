/**
 * Render entity sensors tab.
 *
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone",
    "view/viewutils", "model/sensor-summary", "text!tpl/apps/sensors.html", "text!tpl/apps/sensor-row.html",
    "jquery-datatables", "datatables-fnstandingredraw"
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
            $.ajaxSetup({ async:false });
            var that = this,
                sensorsCollection = new SensorSummary.Collection,
                $table = this.$('#sensors-table'),
                $tbody = this.$('tbody').empty();
            sensorsCollection.url = that.model.getLinkByName('sensors');
            sensorsCollection.fetch({ success:function () {
                sensorsCollection.each(function (sensor) {
                    var actions = {};
                    _.each(sensor.get("links"), function(v,k) {
                        if (k.slice(0, 7) == "action:") {
                            actions[k.slice(7)] = v;
                        }
                    });
                    $tbody.append(that.sensorTemplate({
                        name:sensor.get("name"),
                        description:sensor.get("description"),
                        actions:actions,
                        type:sensor.get("type"),
                        value:'' // will be set later
                    }));
                });
                $tbody.find('*[rel="tooltip"]').tooltip();
                that.updateSensorsPeriodically(that);
                ViewUtils.myDataTable($table);
                $table.dataTable().fnAdjustColumnSizing();
            }});
            that.toggleFilterEmpty();
        },
        render:function () {
            this.updateSensorsNow(this);
            return this;
        },
        toggleFilterEmpty:function () {
            ViewUtils.toggleFilterEmpty(this.$('#sensors-table'), 2);
        },
        refreshSensors:function () {
            this.updateSensorsNow(this);  
        },
        // register a callback to update the sensors
        updateSensorsPeriodically:function (that) {
            var self = this;
            that.updateSensorsNow(that);
            that.callPeriodically("entity-sensors", function() {
                self.updateSensorsNow(that);
            }, 3000);
        },
        updateSensorsNow:function (that) {
            // NB: this won't add new dynamic sensors
            var url = that.model.getSensorUpdateUrl(),
                $table = that.$('#sensors-table'),
                $rows = that.$("tr.sensor-row");
            $.get(url, function (data) {
                // iterate over the sensors table and update each sensor
                $rows.each(function (index, row) {
                    var key = $(this).find(".sensor-name").text();
                    var v = data[key];
                    if (v === undefined) v = '';
                    $table.dataTable().fnUpdate(_.escape(v), row, 2, false);
                });
            });
            $table.dataTable().fnStandingRedraw();
        }
    });
    return EntitySensorsView;
});
