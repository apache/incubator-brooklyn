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
# * (in addition the entry may *be* the actual page object when the item is a page whose menu is not overridden)
#
# To build, set `children` as a list of either strings (the relative or absolute path to the child .md file),
# or as maps with a `path` or `link` (absolute URL) key, a `title` (optional for `path`, to override the title from the file),
# and (for `path` only) an optional `menu` block (to override the menu inherited from the `children` records in file).
#
# For instance:
#
#children:
#- child.md
#- { path: child.md }  # identical to above
#- { path: child.md, title: "Child with New Name" }  # overriding name
#- { path: child.md, menu: [ { path: subchild.md, title: "Sub-Child with New Name" } ] }  # custom sub-menu with custom title
#- { path: child.md, menu: null }  # suppress sub-menu (note `null` not `nil` because this is yaml)
#
# The menu is automatically generated for all files referenced from the root menu.
# You can also set `breadcrumbs` as a list of paths in a page to force breadcrumbs, and
# `menu_proxy_for` to have `menu_path` set differently to the usual `path` (to fake breadcrumbs).
# 
# Additionally URL rewriting is done if a path map is set in _config.yaml,
# with `path: { xxx: /new_xxx }` causing `/xxx/foo.html` to be rewritten as `/new_xxx/foo.html`.
#
module SiteStructure

  DEBUG = false
  
#  require 'pp'

  class Generator < Jekyll::Generator

    def self.find_page_with_path_absolute_or_relative_to(site, path, referrent, structure_processed_pages)
      uncleaned_path = path
      
      # Pathname API ignores first arg below if second is absolute
#      puts "converting #{path} wrt #{referrent ? referrent.path : ""}"
      file = Pathname.new(File.dirname(referrent ? referrent.path : "")) + path
      file += "index.md" if file.to_s.end_with? "/"
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
          puts "INFO: reading excluded file #{file} for site structure generation" unless SiteStructure::DEBUG
          page = Jekyll::Page.new(site, site.source, File.dirname(file), File.basename(file))
        end
 
        unless page
          raise "No such file #{path} in site_structure call (from #{referrent ? referrent.path : ""})" unless SiteStructure::DEBUG
          puts "Could not find a page called: #{path} (referenced from #{referrent ? referrent.path : "root"}); skipping"
          return nil
        end
      end

      # now apply standard clean-up
      if ((site.config['path']) && (site.config['path'].is_a? Hash))
        site.config['path'].each {|key, value| 
          if (page.url.start_with?("/"+key))
            page.url.slice!("/"+key)
            page.url.prepend(value)
          end
        }
      end 
      
      # and put in cache
      structure_processed_pages[path] = page
 
      page     
    end

    def generate(site)
      
      structure_processed_pages = {}
      # process root page
      root_menu_page = site.config['root_menu_page']
      site.data.merge!( Generator.gen_structure(site, { 'path' => root_menu_page }, nil, [], [], structure_processed_pages).data ) if root_menu_page
      # process all pages
      site.pages.each { |p| 
        Generator.gen_structure(site, { 'path' => p.path }, nil, [], [], structure_processed_pages) if p.path.end_with? ".md"
      }
      site.data['structure_processed_pages'] = structure_processed_pages
#      puts "ROOT MENU is #{site.data['menu']}"
#      puts "PAGE menu is #{structure_processed_pages['/website/index.md'].data['menu'}"
#but in the context hash map 'data' on pages is promoted, so you access it like {{ page.menu }}
    end

    # processes liquid tags, e.g. in a link or path object
    def self.render_liquid(site, page, content)
      return content unless page
      info = { :filters => [Jekyll::Filters], :registers => { :site => site, :page => page } }
      page.render_liquid(content, site.site_payload, info)
    end
        
    def self.gen_structure(site, item, parent, breadcrumb_pages_in, breadcrumb_paths_in, structure_processed_pages)
#      puts "gen_structure #{item}"
      breadcrumb_pages = breadcrumb_pages_in.dup
      breadcrumb_paths = breadcrumb_paths_in.dup
      if (item.is_a? String)
        item = { 'path' => item }
      end
      if (item['path'])      
        page = find_page_with_path_absolute_or_relative_to(site, render_liquid(site, parent, item['path']), parent, structure_processed_pages)
        # if find_page doesn't raise, we are in debug, silently ignore
        return nil unless page
        # build up the menu info
        if (item.length==1)
          data = page.data
          result = page
        else
          # if other fields set on 'item' then we are overriding, so we have to take a duplicate
          unless page['menu']
            # force processing if not yet processed, breadcrumbs etc set from that page
            page = gen_structure(site, "/"+page.path, parent, breadcrumb_pages_in, breadcrumb_paths_in, structure_processed_pages)
          end
          data = page.data.dup
          data['data'] = data
          result = data
        end 
        data['path'] = page.path
        data['url'] = page.url
        data['page'] = page
        breadcrumb_pages << page
        breadcrumb_paths << page.path
      elsif (item['link'])
        link = render_liquid(site, parent, item['link'])
        data = { 'link' => link, 'url' => link }
        breadcrumb_pages << data
        breadcrumb_paths << data['link']
        data['data'] = data
        result = data
      else
        raise "Link to #{item} in #{parent ? parent.path : nil} must have link or path"
      end

      data['breadcrumb_pages'] ||= breadcrumb_pages
      data['breadcrumb_paths'] ||= breadcrumb_paths
      data['menu_parent'] ||= parent
      
      data['title_in_menu'] = item['title_in_menu'] || item['title'] || data['title_in_menu'] || data['title']
#      puts "built #{data}, now looking at children"

      # if already processed then return now that we have set custom item overrides (don't recurse through children)
      return result if data['menu']
      
      data['menu_path'] = page.path if page
      
      if data['menu_proxy_for']
        menu_proxy_for = gen_structure(site, { 'path' => data['menu_proxy_for'], 'no_copy' => "because breadcrumbs won't be right" }, page, [], [], structure_processed_pages)
        raise "missing menu_proxy_for #{data['menu_proxy_for']} in #{page.path}" unless menu_proxy_for
        data['menu_path'] = menu_proxy_for['path']
        data.merge!(menu_proxy_for.select {|key, value| ['menu', 'breadcrumb_paths', 'breadcrumb_pages', 'menu_parent'].include?(key) })
      end
      
      if data['breadcrumbs']
        # if custom breadcrumbs set on page, use them instead
        data['breadcrumb_pages'] = data['breadcrumbs'].collect { |path|
          result = find_page_with_path_absolute_or_relative_to(site, render_liquid(site, parent, path), page, structure_processed_pages)
          raise "missing breadcrumb #{path} in #{page.path}" unless result
          result
        }
        data['breadcrumb_paths'] = data['breadcrumb_pages'].collect { |p| p.path }
      end

      if data['menu_parent'] 
        if data['menu_parent'].is_a? String
          # if custom menu_parent was set as a string then load it
          result = find_page_with_path_absolute_or_relative_to(site, render_liquid(site, parent, data['menu_parent']), page, structure_processed_pages)        
          raise "missing parent #{data['menu_parent']} in #{page['path']}" unless result
          data['menu_parent'] = result
        end
      else
        # set menu_parent from breadcrumbs if not set (e.g. we are loading an isolated page)
        data['menu_parent'] = page['breadcrumb_pages'][-1]
      end

      if (data['children'])
        data['menu'] = []
        data['children'].each do |child|
          sub = gen_structure(site, child, page, breadcrumb_pages, breadcrumb_paths, structure_processed_pages)
          if sub
            if (!(child.is_a? String) && child.has_key?('menu'))
              # process custom menu override
              sub['menu'] = child['menu']
              if (sub['menu'] != nil)
                sub['menu'] = sub['menu'].collect { |mi| 
                  gen_structure(site, mi, page, breadcrumb_pages, breadcrumb_paths, structure_processed_pages)
                }
                sub['menu'].compact!
              end
            end
            data['menu'] << sub
          end
        end
      end
      
      result
    end
  end
end
