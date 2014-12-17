# tag to write the correct URL depending whether we are running with dependencies local (for offline) or remote (eg using a CDN)

# specify a map of <basename>: <remote_url> in the key `dependency_urls` in your `_config.yml`,
# then, if `dependency_mode: local` is defined, the path `{{site.path.style}}/deps/<basename>` will be used,
# otherwise the <remote_url> will be used

module JekyllDependencyUrl
  class DependencyUrlTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
	jekyllSite = context.registers[:site]
        mode = context['site']['dependency_mode']
        if mode != 'local'
          result = context['site']['dependency_urls'][@text.strip]
          if result.to_s == ''
            raise 'No value in dependency_urls specified for ' + @text.strip
          end
        end
        if result.to_s == ''
          result = context['site']['path']['style'] + "/deps/" + @text.strip
        end
        return result
    end
  end
end

Liquid::Template.register_tag('dependency_url', JekyllDependencyUrl::DependencyUrlTag)

