import org.codehaus.groovy.grails.web.context.ServletContextHolder;

import brooklyn.web.console.SecurityRole
import brooklyn.web.console.SecurityUser
import brooklyn.web.console.SecurityUserRole

class BootStrap {
    def springSecurityService

    // TODO hard-coded admin/password account!  (password can be passed in, but that's still far from ideal!)
    def init = { servletContext ->
        String password = ServletContextHolder.servletContext?.getAttribute("brooklynbrooklynWeb") ?:
            'password'
        
        def adminRole = SecurityRole.findByAuthority('ROLE_ADMIN') ?: new SecurityRole(authority: 'ROLE_ADMIN').save(failOnError: true)
        def adminUser =  SecurityUser.findByUsername('admin') ?: new SecurityUser(
                username: 'admin',
                password: springSecurityService.encodePassword(password),
                enabled: true).save(failOnError: true)

        if (!adminUser.authorities.contains(adminRole)) {
            SecurityUserRole.create adminUser, adminRole
        }

        def userRole =  SecurityRole.findByAuthority('ROLE_USER') ?: new SecurityRole(authority: 'ROLE_USER').save(failOnError: true)
        def user = SecurityUser.findByUsername('user') ?: new SecurityUser(
                username: 'user',
                password: springSecurityService.encodePassword(password),
                enabled: true).save(failOnError: true)

        if (!user.authorities.contains(userRole)) {
            SecurityUserRole.create user, userRole
        }
    }

    def destroy = {
    }
}
