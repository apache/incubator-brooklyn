// shim to access google maps with require.js -- courtesy https://github.com/p15martin/google-maps-hello-world/
define(
	[ "async!https://maps.googleapis.com/maps/api/js?sensor=false" ],
	function() {
		return {
			addMapToCanvas: function( mapCanvas, lat, long, zoom ) {
				var myOptions = {
					center: new google.maps.LatLng( lat, long ),
					zoom: zoom,
					mapTypeId: google.maps.MapTypeId.SATELLITE
				};

				var map = new google.maps.Map( mapCanvas, myOptions );
			}		
		}
	}
);