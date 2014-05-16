define(["backbone", "brooklyn", "view/viewutils"], function (Backbone, Brooklyn, ViewUtils) {

    var HAStatus = Backbone.Model.extend({
        callbacks: [],
        loaded: false,
        url: "/v1/server/highAvailability",
        isMaster: function() {
            return this.get("masterId") == this.get("ownId");
        },
        onLoad: function(f) {
            if (this.loaded) {
                f();
            } else {
                this.callbacks.push(f);
            }
        }
    });

    var haStatus = new HAStatus();
    haStatus.once("sync", function() {
        haStatus.loaded = true;
        _.invoke(haStatus.callbacks, "apply");
        haStatus.callbacks = undefined;
    });

    ViewUtils.fetchModelRepeatedlyWithDelay(haStatus, {
        doitnow: true
    });

    // Will returning the instance rather than the object be confusing?
    // It breaks the pattern used by all the other models.
    return haStatus;

});