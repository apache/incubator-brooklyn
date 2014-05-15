define(["backbone", "brooklyn", "view/viewutils"], function (Backbone, Brooklyn, ViewUtils) {

    var HAStatus = Backbone.Model.extend({
        url: "/v1/server/highAvailability"
    });
    var haStatus = new HAStatus();

    ViewUtils.fetchModelRepeatedlyWithDelay(haStatus);

    // Will returning the instance rather than the object be confusing?
    // It breaks the pattern used by all the other models.
    return haStatus;

});