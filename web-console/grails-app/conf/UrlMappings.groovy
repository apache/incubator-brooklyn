class UrlMappings {

    static excludes = ["/images/**", "/css/**", "/js/**", "/static/**" ]

	static mappings = {
		"/$controller/$action?/$id?"{
			constraints {
				// apply constraints here
			}
		}

        "/login/$action?"(controller: "login")
        "/logout/$action?"(controller: "logout")

        "/dynjs/$action"(controller: "javascript")

		"/"(view:"/index")

        "500"(view:'/error')
	}
}
