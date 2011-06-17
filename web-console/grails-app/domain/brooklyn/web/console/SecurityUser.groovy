package brooklyn.web.console

class SecurityUser {

	String username
	String password
	boolean enabled
	boolean accountExpired
	boolean accountLocked
	boolean passwordExpired

	static constraints = {
		username blank: false, unique: true
		password blank: false
	}

	static mapping = {
		password column: '`password`'
	}

	Set<SecurityRole> getAuthorities() {
		SecurityUserRole.findAllBySecurityUser(this).collect { it.securityRole } as Set
	}
}
