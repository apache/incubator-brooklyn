<?php

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

function findClosestHost($lat_deg, $long_deg, $available_hosts) {
    $minimum_distance = PHP_INT_MAX;
    for ($i = 0 ; $i < sizeof($available_hosts); $i++) {
        $host = $available_hosts[$i];
        $distance_km = distanceBetween($lat_deg, $long_deg, $host['latitude'], $host['longitude']);
        if ($distance_km < $minimum_distance) {
            $minimum_distance = $distance_km;
            $closest_host = $host;
        }
    }
    return $closest_host;
}


$hosts = array(
    array('name'      => 'Amazon US-West', // Northern California (approx)
          'latitude'  => 40.0,
          'longitude' => -120.0,
          'ip'        => '1.2.3.100'),

    array('name'      => 'Amazon US-East', // Northern Virginia (approx)
          'latitude'  => 38.0,
          'longitude' => 76.0,
          'ip'        => '1.2.3.200'),

    array('name'      => 'Amazon EU-West', // Dublin, Ireland
          'latitude'  => 53.34778,
          'longitude' => -6.25972,
          'ip'        => '1.2.3.300'),

    array('name'      => 'Monterey West', // Milpitas, CA
          'latitude'  => 37.43472,
          'longitude' => -121.89500,
          'ip'        => '208.95.232.123'),

    array('name'      => 'Monterey East', // Hawthorne, NY
          'latitude'  => 41.10361,
          'longitude' => -73.79583,
          'ip'        => '216.48.127.224'),
);

$closest_host = findClosestHost($city_info['latitude'], $city_info['longitude'], $hosts);

if (isset($closest_host)) {
    $output[] = array("A", $closest_host['ip']);
    $output[] = array("TXT", "GeoScaling config last auto-updated: Wed, 27 Jul 2011 at 11:39:53 UTC");
    $output[] = array("TXT", "Chosen closest server is ".$closest_host['name']);
    $output[] = array("TXT", "Request originated from [".$city_info['latitude'].",".$city_info['longitude']."]");
} else {
    $output[] = array("fail");
}

?>