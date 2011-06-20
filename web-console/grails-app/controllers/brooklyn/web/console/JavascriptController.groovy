package brooklyn.web.console

class JavascriptController {

    def jsTreeConfig = {
		render(contentType:"text/javascript", template:"jsTreeConfig")
	}
}
