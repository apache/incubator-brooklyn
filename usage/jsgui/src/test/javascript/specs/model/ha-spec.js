define([
    "model/ha"
], function (ha) {

    describe("model/ha", function() {

        beforeEach(function () {
            ha.url = "fixtures/ha-summary.json";
            ha.fetch();
        });

        it("should determine the master's URI", function() {
            expect(ha.getMasterUri()).toBe("http://10.30.40.50:8081/");
        });

        it("should determine isMaster", function() {
            expect(ha.isMaster()).toBe(true);
            ha.set("masterId", "something else");
            expect(ha.isMaster()).toBe(false);
        });

    });
});