import brooklyn.web.console.*

class BootStrap {
    def springSecurityService

    // TODO hard-coded admin/password account!
    def init = { servletContext ->
        def adminRole = new SecurityRole(authority: 'ROLE_ADMIN').save(failOnError: true)
        def adminUser = new SecurityUser(
                username: 'admin',
                password: springSecurityService.encodePassword('password'),
                enabled: true).save(failOnError: true)

        if (!adminUser.authorities.contains(adminRole)) {
            SecurityUserRole.create adminUser, adminRole
        }

        def userRole = new SecurityRole(authority: 'ROLE_USER').save(failOnError: true)
        def user = new SecurityUser(
                username: 'user',
                password: springSecurityService.encodePassword('password'),
                enabled: true).save(failOnError: true)

        if (!user.authorities.contains(userRole)) {
            SecurityUserRole.create user, userRole
        }
    }
    def destroy = {
    }
}
