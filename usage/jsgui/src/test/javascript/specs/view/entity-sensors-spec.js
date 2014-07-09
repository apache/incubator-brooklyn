define([
    "underscore", "view/entity-sensors",
    "text!tpl/apps/sensor-name.html"
], function (_, EntitySensorsView, SensorNameHtml) {

    function contains(string, value) {
        return string.indexOf(value) != -1;
    }

    describe("template/sensor-name", function () {
        var sensorNameHtml = _.template(SensorNameHtml);
        var context = {name: "name", description: "description", type: "type"};

        it("should not create an anchor tag in name", function() {
            var templated = sensorNameHtml(_.extend(context, {href: "href"}));
            expect(contains(templated, "<a href=\"href\"")).toBe(false);
        });
        
        it("should not fail if context.href is undefined", function() {
            var templated = sensorNameHtml(_.extend(context, {href: undefined}));
            expect(contains(templated, "<a href=")).toBe(false);
        });
    })

})