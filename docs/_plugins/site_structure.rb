# Builds a hierarchical structure for the site, based on the YAML front matter of each page
# Starts from a page called "index.md", and follows "children" links in the YAML front matter
module SiteStructure
 
  BROOKLYN_WEBSITE_ROOT = "/website/index.md" unless defined? BROOKLYN_WEBSITE_ROOT
  
  class Generator < Jekyll::Generator
    def find_page_with_path_absolute_or_relative_to(site, path, referrent, structure_processed_pages)
      uncleaned_path = path
      
      # Pathname API ignores first arg below if second is absolute
      file = Pathname.new(File.dirname(referrent ? referrent.path : "")) + path
      file = file.cleanpath
      # is there a better way to trim a leading / ?
      file = file.relative_path_from(Pathname.new("/")) unless file.relative?
      path = "#{file}"
        
      # look in our cache        
      page = structure_processed_pages.detect { |item| item['path'] == path }
      return page if page != nil
      
      # look in site cache
      page = site.pages.detect { |page| page.path == path }
      if !page
        page = site.pages.detect { |page| '/'+page.path == uncleaned_path }
        puts "WARNING: link to #{path} in #{referrent ? referrent.path : "root"} uses legacy absolute syntax without leading slash" if page
      end

      unless page
        # could not load it from pages, look on disk
                 
        raise "No such file #{path} in site_structure call (from #{referrent ? referrent.path : ""})" unless file.exist?
#        puts "INFO: reading excluded file #{file} for site structure generation"
        page = Jekyll::Page.new(site, site.source, File.dirname(file), File.basename(file))
        
        throw "Could not find a page called: #{path} (referenced from #{referrent ? referrent.path : "root"})" unless page
      end

      # now apply standard clean-up
      if (page.url.start_with?("/website"))
        page.url.slice!("/website")
        page.url.prepend(site.config['path']['website'])
      end
      if (page.url.start_with?("/guide"))
        page.url.slice!("/guide")
        page.url.prepend(site.config['path']['guide'])
      end
      # and put in cache
      structure_processed_pages << page
 
      page     
    end

    def generate(site)
      structure_processed_pages = []
      root_page = find_page_with_path_absolute_or_relative_to(site, SiteStructure::BROOKLYN_WEBSITE_ROOT, nil, structure_processed_pages)
      navgroups = root_page.data['navgroups']
      navgroups.each do |ng|
        ng['page'] = find_page_with_path_absolute_or_relative_to(site, ng['page'], root_page, structure_processed_pages)
        if not ng['title_in_menu']
          ng['title_in_menu'] = ng['title'].capitalize
        end
      end
      site.data['navgroups'] = navgroups
      site.data['structure'] = gen_structure(site, SiteStructure::BROOKLYN_WEBSITE_ROOT, nil, navgroups, structure_processed_pages)
    end

    def render_liquid(site, page, content)
      info = { :filters => [Jekyll::Filters], :registers => { :site => site, :page => page } }
      page.render_liquid(content, site.site_payload, info)
    end
        
    def gen_structure(site, pagename, parent, navgroups, structure_processed_pages)
      page = find_page_with_path_absolute_or_relative_to(site, pagename, parent, structure_processed_pages)
      
      # My navgroup is (first rule matches):
      # 1. what I have explicitly declared
      # 2. if I find my path referred to in the global navgroup list
      # 3. my parent's navgroup
      unless page.data['navgroup']
        match = navgroups.detect { |ng| ng['page'] == page }
        if match
          page.data['navgroup'] = match['id']
        elsif parent
          page.data['navgroup'] = parent.data['navgroup']
        end
      end
            
      # Figure out second level menu
      # If there's no parent => I'm at the top level, so no action
      # If there's a parent, but parent has no parent => I'm at second level, so set second-level menu
      # Otherwise, use the parent's second level menu
      if parent && !parent.data['parent']
        page.data['menu2parent'] = page
        page.data['menu2'] = page.data['children']
      elsif parent && parent.data['parent']
        page.data['menu2parent'] = parent.data['menu2parent']
        page.data['menu2'] = parent.data['menu2']
      end
      
      page.data['parent'] = parent
      if page.data['children']
        page.data['children'].each do |c|
          if c['path']
            # links to another Jekyll site-structured page
            c['reference'] = gen_structure(site, render_liquid(site, page, c['path']), page, navgroups, structure_processed_pages)
          elsif c['link']
            # links to a non-site-structured page, on this site or elsewhere
            # allow title and link to use vars and tags (liquid processing)
            c['reference'] = { 'url' => render_liquid(site, page, c['link']), 'title' => render_liquid(site, page, c['title']) }
          end
        end
      end
      
      page
    end
  end
end
