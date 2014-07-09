define(["backbone", "brooklyn", "view/viewutils"], function (Backbone, Brooklyn, ViewUtils) {

    var HAStatus = Backbone.Model.extend({
        callbacks: [],
        loaded: false,
        url: "/v1/server/highAvailability",
        isMaster: function() {
            return this.get("masterId") == this.get("ownId");
        },
        getMasterUri: function() {
            // Might be undefined if first fetch hasn't completed
            var nodes = this.get("nodes") || {};
            var master = nodes[this.get("masterId")];
            // defensive, if this happens something more serious has gone wrong!
            if (!master) {
                return null;
            } else {
                return master.nodeUri;
            }
        },
        onLoad: function(f) {
            if (this.loaded) {
                f();
            } else {
                this.callbacks.push(f);
            }
        },
        autoUpdate: function() {
            ViewUtils.fetchModelRepeatedlyWithDelay(this, { doitnow: true });
        }
    });

    var haStatus = new HAStatus();
    haStatus.once("sync", function() {
        haStatus.loaded = true;
        _.invoke(haStatus.callbacks, "apply");
        haStatus.callbacks = undefined;
    });

    // Will returning the instance rather than the object be confusing?
    // It breaks the pattern used by all the other models.
    return haStatus;

});