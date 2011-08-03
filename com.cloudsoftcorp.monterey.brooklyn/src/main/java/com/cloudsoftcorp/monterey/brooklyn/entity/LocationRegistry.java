/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import brooklyn.location.Location;

import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation;
import com.cloudsoftcorp.util.Loggers;

/**
 * Registry for tracking all the locations that can be accessed.
 *  
 * @author aled
 */
public class LocationRegistry {
	private static final Logger LOG = Loggers.getLogger(LocationRegistry.class);

    private final ConcurrentMap<MontereyActiveLocation, Location> montereyLocationMapping = new ConcurrentHashMap<MontereyActiveLocation, Location>();
    
    public Location getConvertedLocation(MontereyActiveLocation montereyLoc) {
        montereyLocationMapping.putIfAbsent(montereyLoc, new FooLocation(montereyLoc));
        Location result = montereyLocationMapping.get(montereyLoc);
        return result;
    }
}
