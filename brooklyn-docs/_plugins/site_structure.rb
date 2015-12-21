#
# Builds a hierarchical structure for the site, based on the YAML front matter of each page
#
# Starts from a page called "/index.md" (or the value of `root_menu_page` in _config.yml),
# and follows `children` links in the YAML front matter, building up a variable called `data` 
# on `site` and on each referred `page`.
#
# In Ruby data is in page.data['menu'], but in templates `page.data` is promoted, 
# so you should just refer to things in markdown as `{{ page.menu }}`.
#
# Each `page.menu` entry will contain:
# * `url` - URL (relative or absolute) to link to
# * `title_in_menu` - title to show
# * `menu_path` - the path of this page for the purposes of looking in breadcrumbs (usually page.path, unless overriden) 
# * `breadcrumbs_pages` - page items for ancestor items (and self)
# * `breadcrumbs_paths` - paths of breadcrumb pages (useful for `if .. contains` jekyll tests)
# * `menu_parent` - the parent menu which contains this page
# * `menu_customization` - a hash of customization set in front matter or in children (can be any data you like)
# * (in addition the entry may *be* the actual page object when the item is a page whose menu is not overridden)
#
# To build, set `children` as a list of either strings (the relative or absolute path to the child .md file),
# or as maps indicating the target, one of:
# * `path` to a markdownfile
# * `link` as an URL 
# * `section` anchored in this file (annotated with `<a name="#section"></a>`)
# And optionally:
# * a `title` (required for `link`), to override the title from the file
# * an optional `menu` block  (for `path` only) to override the menu inherited from the `children` record noted at `path`
# * `menu_customization` to set arbitrary data available (e.g. for templates to use when styling)
# * `href_path` (for `path` only) to specify that a click should send to a different page than the path used to produce the menu
#
# For instance:
#
#children:
#- child.md
#- { path: child.md }  
#  # identical to above
#- { path: subchild.md, title: "Sub Child" }   
#  # different child, with custom title
#- { path: subchild.md, href_path: subchild_alt.md }  
#  # takes title and menu from subchild page, but links to subchild_alt
#- { path: child.md, menu: [ { path: subchild.md, title: "Sub-Child with New Name" } ] } 
#  # child, but with custom sub-menu and custom title in there 
#- { path: child.md, menu: null }  # suppress sub-menu (note `null` not `nil` because this is yaml)
#  # child again, but suppressing sub-menu (note `null` not `nil` because this is yaml)
#- { section: Foo }
#- { section: Bar }
#  # various sections in *this* doc (to make highlighting work for sections requires
#  # extra JS responding to scrolls; otherwise the parent page remains highlighted)
#
# The menu is automatically generated for all files referenced from the root menu.
# You can also set `breadcrumbs` as a list of paths in a page to force breadcrumbs, and
# `menu_proxy_for` to have `menu_path` set differently to the usual `path` (highlight another page in a menu via breadcrumbs)
# or `menu_parent` to a path to the menu which should be the parent of the current node.
# 
# The hash `menu_customization` allows you to pass arbitrary data around, e.g. for use in styling.
# 
# Additionally URL rewriting is done if a path map is set in _config.yaml,
# with `path: { xxx: /new_xxx }` causing `/xxx/foo.html` to be rewritten as `/new_xxx/foo.html`.
#
module SiteStructure

  DEBUG = false

  require 'yaml'  
#  require 'pp'

  class RewritePaths < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
      page = context['page']
      site = context['site']
      RewritePaths.rewrite_paths(site, page)
    end
    
    def self.rewrite_paths(site, page)
      path = page['path']
      page_hash = (page.is_a? Hash) ? page : page.data
      # set url_basedir and apply path mapping
      page_hash['url_basedir'] = File.dirname(path)+"/"
      page_hash['url_basedir'].prepend("/") unless page_hash['url_basedir'].start_with? "/"
      
      config_hash = (site.is_a? Hash) ? site : site.config
      
      if ((config_hash['path']) && (config_hash['path'].is_a? Hash))
        config_hash['path'].each {|key, value| 
          if (path.start_with?(key))
            if ((!page.is_a? Hash) && page.url)
              page.url.slice!("/"+key)
              page.url.prepend(value)
            end
            
            page_hash['url_basedir'].slice!("/"+key)
            page_hash['url_basedir'].prepend(value)
          end
        }
      end
      
      nil
    end
  end
  
  Liquid::Template.register_tag('rewrite_paths', SiteStructure::RewritePaths)

  
  class Generator < Jekyll::Generator

    @@verbose = false;
    
    def self.find_page_with_path_absolute_or_relative_to(site, path, referrent, structure_processed_pages)
      uncleaned_path = path
      
      # Pathname API ignores first arg below if second is absolute
#      puts "converting #{path} wrt #{referrent ? referrent.path : ""}"
      file = Pathname.new(File.dirname(referrent ? referrent.path : "")) + path

      if file.to_s.end_with? "/"
        if File.exist? File.join(file, 'index.md')
          file += 'index.md'
        elsif File.exist? File.join(file, 'index.html')
          file += 'index.html'
        else
          file += 'index.md'
        end
      end

      file = file.cleanpath
      # is there a better way to trim a leading / ?
      file = file.relative_path_from(Pathname.new("/")) unless file.relative?
      path = "#{file}"
        
      # look in our cache        
      page = structure_processed_pages[path]
      return page if page != nil
      
      # look in site cache
      page = site.pages.detect { |page| page.path == path }
      if !page
        page = site.pages.detect { |page| '/'+page.path == uncleaned_path }
        puts "WARNING: link to #{path} in #{referrent ? referrent.path : "root"} uses legacy absolute syntax without leading slash" if page
      end

      unless page
        # could not load it from pages, look on disk

        if file.exist?                 
          puts "INFO: reading excluded file #{file} for site structure generation" if SiteStructure::DEBUG
          page = Jekyll::Page.new(site, site.source, File.dirname(file), File.basename(file))
          # make sure right url is set
          RewritePaths.rewrite_paths(site, page)
        end
 
        unless page
          raise "No such file #{path} in site_structure call (from #{referrent ? referrent.path : ""})" unless SiteStructure::DEBUG
          puts "Could not find a page called: #{path} (referenced from #{referrent ? referrent.path : "root"}); skipping"
          return nil
        end
      end
      
      # and put in cache
      structure_processed_pages[path] = page
 
      page     
    end

    def generate(site)
      # rewrite paths
      site.pages.each { |p| RewritePaths.rewrite_paths(site, p) }
      structure_processed_pages = {}
      # process root page
      root_menu_page = site.config['root_menu_page']
      puts "site_structure processing root menu page #{root_menu_page}" if @@verbose
      site.data.merge!( Generator.gen_structure(site, { 'path' => root_menu_page }, nil, [], [], structure_processed_pages).data ) if root_menu_page
      # process all pages
      puts "site_structure now processing all pages" if @@verbose
      site.pages.each { |p| 
        Generator.gen_structure(site, { 'path' => p.path }, nil, [], [], structure_processed_pages) if (p.path.end_with?(".md") || p.path.end_with?(".html")) && (!p['menu_processed'])
      }
      site.data['structure_processed_pages'] = structure_processed_pages
#      puts "ROOT menu is #{site.data['menu']}"
#      puts "PAGE menu is #{structure_processed_pages['website/documentation/index.'].data['menu']}"
# (but note, in the context hash map 'data' on pages is promoted, so you access it like {{ page.menu }})
    end

    # processes liquid tags, e.g. in a link or path object
    def self.render_liquid(site, page, content)
      return content unless page
      info = { :filters => [Jekyll::Filters], :registers => { :site => site, :page => page } }
      page.render_liquid(content, site.site_payload, info)
    end
    
    def self.gen_structure(site, item, parent, breadcrumb_pages_in, breadcrumb_paths_in, structure_processed_pages)
      puts "gen_structure #{item} from #{parent ? parent.path : 'root'} (#{breadcrumb_paths_in})" if @@verbose
      breadcrumb_pages = breadcrumb_pages_in.dup
      breadcrumb_paths = breadcrumb_paths_in.dup
      if (item.is_a? String)
        item = { 'path' => item }
      end
      if (item['path'])      
        page = find_page_with_path_absolute_or_relative_to(site, render_liquid(site, parent, item['path']), parent, structure_processed_pages)
        # if nil and find_page doesn't raise, we are in debug mode, silently ignore
        return nil unless page
        # build up the menu info
        if (item.length==1 && !page['menu_processed'])
          puts "setting up #{item} from #{page.path} as original" if @@verbose
          data = page.data
          result = page
        else
          puts "setting up #{item} from #{page.path} as copy" if @@verbose
          # if other fields are set on 'item' then we are overriding, so we have to take a duplicate
          unless page['menu_processed']
            # force processing if not yet processed, breadcrumbs etc set from that page
            puts "making copy of #{page.path}" if @@verbose
            page = gen_structure(site, "/"+page.path, parent, breadcrumb_pages_in, breadcrumb_paths_in, structure_processed_pages)
            puts "copy is #{page.path}" if @@verbose
          end
          data = page.data.dup
          data['data'] = data
          result = data
        end 
        data['path'] = page.path
        if item['href_path']
          href_page = find_page_with_path_absolute_or_relative_to(site, render_liquid(site, page, item['href_path']), parent, structure_processed_pages)
        else
          href_page = page
        end
        data['url'] = href_page.url
        puts "data is #{data}" if @@verbose
        data['page'] = page
        breadcrumb_pages << page
        breadcrumb_paths << page.path
        
      elsif (item['section'])
        puts "setting up #{item} as section" if @@verbose
        section = item['section']
        section_cleaned = section.gsub(%r{[^A-Za-z0-9]+}, "-").downcase;
        section_cleaned.slice!(1) if section_cleaned.start_with?("-")
        section_cleaned.chomp!("-") # 0..-1) if section_cleaned.end_with?("-")
        link = (parent ? parent.url : "") + '#' + section_cleaned
        data = { 'link' => link, 'url' => link, 'section' => section_cleaned, 'section_title' => section }
        data['title'] = item['title'] if item['title']
        data['title'] = section unless data['title']
        # nothing for breadcrumbs
        data['data'] = data
        result = data
        
      elsif (item['link'])
        puts "setting up #{item} as link" if @@verbose
        link = render_liquid(site, parent, item['link'])
        data = { 'link' => link, 'url' => link, 'external' => true }
        data['title'] = item['title'] if item['title']
        breadcrumb_pages << data
        breadcrumb_paths << data['link']
        data['data'] = data
        result = data
      else
        raise "Link to #{item} in #{parent ? parent.path : nil} must have path or section or link"
      end

      data['menu_customization'] = {}.merge(data['menu_customization'] || {}).merge(item['menu_customization'] || {})
      
      data['breadcrumb_pages'] ||= breadcrumb_pages
      data['breadcrumb_paths'] ||= breadcrumb_paths
      data['menu_parent'] ||= parent
      
      data['title_in_menu'] = render_liquid(site, parent, item['title_in_menu'] || item['title'] || data['title_in_menu'] || data['title'])
      data['title'] ||= data['title_in_menu']
#      puts "built #{data}, now looking at children"

      # if already processed then return now that we have set custom item overrides (don't recurse through children)
      return result if data['menu']
      
      data['menu_path'] = page.path if page
      
      if data['menu_proxy_for']
        menu_proxy_for = gen_structure(site, { 'path' => data['menu_proxy_for'], 'no_copy' => "because breadcrumbs won't be right" }, page, [], [], structure_processed_pages)
        raise "missing menu_proxy_for #{data['menu_proxy_for']} in #{page.path}" unless menu_proxy_for
        data['menu_path'] = menu_proxy_for['path']
        # copy other data across
        data.merge!(menu_proxy_for.select {|key, value| ['breadcrumb_paths', 'breadcrumb_pages', 'menu', 'title_in_menu', 'menu_parent', 'menu_customization'].include?(key) })
      end
      
      if data['breadcrumbs']
        # if custom breadcrumbs set on page, use them instead
        breadcrumb_pages = data['breadcrumb_pages'] = data['breadcrumbs'].collect { |path|
          result = find_page_with_path_absolute_or_relative_to(site, render_liquid(site, parent, path), page, structure_processed_pages)
          raise "missing breadcrumb #{path} in #{page.path}" unless result
          result
        }
        breadcrumb_paths = data['breadcrumb_paths'] = data['breadcrumb_pages'].collect { |p| p.path }
      end

      if data['menu_parent'] 
        if data['menu_parent'].is_a? String
          # if custom menu_parent was set as a string then load it
          parent_result = find_page_with_path_absolute_or_relative_to(site, render_liquid(site, parent, data['menu_parent']), page, structure_processed_pages)        
          raise "missing parent #{data['menu_parent']} in #{page['path']}" unless parent_result
          data['menu_parent'] = parent_result
          if !data['breadcrumbs']
            # TODO should we inherit actual menu parent breadcrumbs if not set on page?
          end
        end
      else
        # set menu_parent from breadcrumbs if not set (e.g. we are loading an isolated page)
        data['menu_parent'] = page['breadcrumb_pages'][-1]
      end

      if (data['children'])
        data['menu'] = []
        puts "children of #{data['path']} - #{data['children']}" if @@verbose
        data['children'].each do |child|
          sub = gen_structure(site, child, page, breadcrumb_pages, breadcrumb_paths, structure_processed_pages)
          if sub
            if (!(child.is_a? String) && child.has_key?('menu'))
              # process custom menu override
              sub['menu'] = child['menu']
              if (sub['menu'] != nil)
                if sub['menu'].is_a? String
                  sub['menu'] = YAML.load(render_liquid(site, page, sub['menu'])) if sub['menu'].is_a? String
                end
                sub['menu'] = sub['menu'].collect { |mi| 
                  gen_structure(site, mi, page, breadcrumb_pages, breadcrumb_paths, structure_processed_pages)
                }
                sub['menu'].compact!
              end
            end
            data['menu'] << sub
            puts "sub is #{sub['url']}" if @@verbose
          else
            raise "could not find #{child} in #{page.path}"
          end
        end
        puts "end children of #{data['path']}" if @@verbose
      end
      
      data['menu_processed']=true
      puts "done #{item}" if @@verbose
      result
    end
  end
end
