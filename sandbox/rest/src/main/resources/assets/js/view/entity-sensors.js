/**
 * Render entity sensors tab.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils", "model/sensor-summary", "text!tpl/apps/sensors.html",
    "text!tpl/apps/sensor-row.html", "tablesorter"
], function (_, $, Backbone, ViewUtils, SensorSummary, SensorsHtml, SensorRowHtml) {

    var EntitySensorsView = Backbone.View.extend({
        template:_.template(SensorsHtml),
        sensorTemplate:_.template(SensorRowHtml),
        events:{
            'click .refresh':'refreshSensors',
            'click .filterEmpty':'toggleFilterEmpty'
        },
        initialize:function () {
            this.$el.html(this.template({}))
            var sensorsCollection = new SensorSummary.Collection,
                $table = this.$('#sensors-table'),
                $tableBody = this.$('tbody').empty(),
                that = this
            this.viewUtils = new ViewUtils({})
            sensorsCollection.url = this.model.getLinkByName('sensors')
            var success = function () {
                sensorsCollection.each(function (sensor) {
                    $tableBody.append(that.sensorTemplate({
                        name:sensor.get("name"),
                        description:sensor.get("description"),
                        value:'',
                        type:sensor.get("type")
                    }))
                })
                that.updateSensorsPeriodically(that)
                that.viewUtils.myDataTable($table)
                // TODO tooltip doesn't work on 'i' elements in table (bottom left toolbar)
                $table.find('*[rel="tooltip"]').tooltip()
            }
            sensorsCollection.fetch({async:false, success:success})
            this.toggleFilterEmpty()
        },
        render:function () {
            return this
        },
        toggleFilterEmpty: function() {
            this.viewUtils.toggleFilterEmpty(this.$('#sensors-table'), 1)
        },
        refreshSensors:function () {
            this.updateSensorsNow(this);  
        },
        // register a callback to update the sensors
        updateSensorsPeriodically:function (that) {
            var self = this;
            that.updateSensorsNow(that)
            that.callPeriodically(function() { self.updateSensorsNow(that) }, 3000)
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
                        $table.dataTable().fnUpdate(_.escape(v), row, 1)
                    })
                })
            }
        })
    return EntitySensorsView
})