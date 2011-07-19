<?php

/**************************************************************************************
 **  DO NOT modify this script, as your changes will likely be overwritten.
 **  Auto-generated on DATESTAMP.
 **************************************************************************************/


/* Returns the approximate distance (in km) between 2 points on the Earth's surface,
 * specified as latitude and longitude in decimal degrees. Derived from the spherical
 * law of cosines.
 */
function distanceBetween($lat1_deg, $long1_deg, $lat2_deg, $long2_deg) {
    define("RADIUS_KM", 6372.8); // approx
    $lat1_rad = deg2rad($lat1_deg);
    $lat2_rad = deg2rad($lat2_deg);
    $long_delta_rad = deg2rad($long1_deg - $long2_deg);
    $distance_km = RADIUS_KM * acos( (sin($lat1_rad) * sin($lat2_rad)) +
                                     (cos($lat1_rad) * cos($lat2_rad) * cos($long_delta_rad)) );
    return $distance_km;
}

function findClosestServer($lat_deg, $long_deg, $available_servers) {
    $minimum_distance = PHP_INT_MAX;
    for ($i = 0 ; $i < sizeof($available_servers); $i++) {
        $server = $available_servers[$i];
        $distance_km = distanceBetween($lat_deg, $long_deg, $server['latitude'], $server['longitude']);
        if ($distance_km < $minimum_distance) {
            $minimum_distance = $distance_km;
            $closest_server = $server;
        }
    }
    return $closest_server;
}


/* SERVER DECLARATIONS TO BE SUBSTITUTED HERE */

$closest_server = findClosestServer($city_info['latitude'], $city_info['longitude'], $servers);

if (isset($closest_server)) {
    $output[] = array("A", $closest_server['ip']);
    $output[] = array("TXT", "Chosen closest server is ".$closest_server['name']);
    $output[] = array("TXT", "Request originated from [".$city_info['latitude'].",".$city_info['longitude']."]");
} else {
    $output[] = array("fail");
}

?>