class UrlMappings {

    static excludes = ["/images/**", "/css/**", "/js/**", "/static/**" ]

	static mappings = {
		"/$controller/$action?/$id?"{
			constraints {
				// apply constraints here
			}
		}

        "/dynjs/$action"(controller: "javascript")

		"/"(view:"jheytest/index")
		"500"(view:'/error')
	}
}
