Brooklyn.gmaps = (function(){

    /* Convenience function for creating Google Maps InfoWindows which
     * contain a list of key value pairs.
     *
     * Pairs is an object of key value pairs to be displayed as a definition list.
     * Map and Marker are optional. They specify a marker to attach the info window to.
     */
    function ListInfoWindow(pairs, map, marker) {
        var contentString = '<div id="content" class="mapbox"><dl>';

        for (var key in pairs) {
            contentString += '<dt>' + key + '</dt>' + '<dd>' + pairs[key] + '</dd>';
        }

        contentString += '</dl> </div>';

        this.infowindow = new google.maps.InfoWindow({
            content: contentString
        });

        if ((typeof marker) !== 'undefined') {
            this.attachTo(map, marker);
        }
    }

    /* Open the info window when the specified marker is clicked on. */
    ListInfoWindow.prototype.attachTo = function(map, marker) {
        if (!map) { alert ("ListInfoWindow.attachTo requires map."); }
        if (!marker) { alert ("ListInfoWindow.attachTo requires marker."); }

        var iw = this.getInfoWindow();
        google.maps.event.addListener(marker, 'click' , function(){
            iw.open(map, marker);
        });
    }

    ListInfoWindow.prototype.getInfoWindow = function() {
        return this.infowindow;
    }

    return {
        ListInfoWindow: ListInfoWindow,
    };

}());
