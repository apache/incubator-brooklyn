package org.overpaas.grails

import grails.converters.JSON

import org.overpaas.locations.SshMachineLocation

class EntityController {
    
    def index = {
        SshMachineLocation loc = new SshMachineLocation(name:'london', host:'localhost', user:'zebedee');
        render loc as JSON
    }

}
