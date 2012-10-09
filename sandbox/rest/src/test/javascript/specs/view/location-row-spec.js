define([
    "model/location", "view/location-row"
], function (Location, LocationRowView) {

    describe('OneLocationView rendering', function () {
        var locationSample = new Location.Model({
            provider:'amazon',
            config:{
                sample:'sample property'
            },
            links:{
                self:'/v1/locations/101'
            }
        })
        var locationListItemView = new LocationRowView({model:locationSample})
        var $renderedView = locationListItemView.render().$el
        it('contains all the properties defined in our sample', function () {
            expect($renderedView.html()).toContain('amazon')
            expect($renderedView.html()).toContain('sample property')
            expect($renderedView.find('button#101').length).toEqual(1)
        })
    })
})