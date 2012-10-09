/**
 * Render entity sensors tab.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "model/sensor-summary", "text!tpl/apps/sensors.html",
    "text!tpl/apps/sensor-row.html", "tablesorter"
], function (_, $, Backbone, SensorSummary, SensorsHtml, SensorRowHtml) {

    var EntitySensorsView = Backbone.View.extend({
        template:_.template(SensorsHtml),
        sensorTemplate:_.template(SensorRowHtml),
        initialize:function () {
            this.$el.html(this.template({}))
            var sensorsCollection = new SensorSummary.Collection,
                $table = this.$('#sensors-table'),
                $tableBody = this.$('tbody').empty(),
                that = this
            sensorsCollection.url = this.model.getLinkByName('sensors')
            var success = function () {
                sensorsCollection.each(function (sensor) {
                    $tableBody.append(that.sensorTemplate({
                        name:sensor.get("name"),
                        description:sensor.get("description"),
                        value:'default',
                        type:sensor.get("type")
                    }))
                })
                that.updateSensors(that)
                // call the table paging and sorting
                $table.dataTable({
                    "oLanguage":{
                        "sLengthMenu":'Display <select>' +
                            '<option value="25">25</option>' +
                            '<option value="50">50</option>' +
                            '<option value="-1">All</option>' +
                            '</select> records'
                    }
                })
            }
            sensorsCollection.fetch({async:false, success:success})
        },
        render:function () {
            // nothing to do
            return this
        },
        // register a callback to update the sensors
        updateSensors:function (that) {
            var func = function () {
                var url = that.model.getSensorUpdateUrl(),
                    $rows = that.$("tr.sensor-row")
                $.get(url, function (data) {
                    // iterate over the sensors table and update each sensor
                    $rows.each(function (index) {
                        var key = $(this).find(".sensor-name").text()
                        $(this).find(".sensor-value").html(_.escape(data[key]))
                    })
                })
            }
            func() // update for initial values
            that.callPeriodically(func, 3000)
        }
    })
    return EntitySensorsView
})