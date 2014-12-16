# Builds a hierarchical structure for the site, based on the YAML front matter of each page
# Starts from a page called "index.md", and follows "children" links in the YAML front matter
module SiteStructure
 
  BROOKLYN_WEBSITE_ROOT = "/website/index.md" unless defined? BROOKLYN_WEBSITE_ROOT
  
  class Generator < Jekyll::Generator
    def find_page_with_path_absolute_or_relative_to(site, path, referrent)
      page = site.pages.detect { |page| "/"+page.path == path }
      if !page && referrent
        page = site.pages.detect { |page| "/"+page.path == "/"+File.dirname(referrent.path)+"/"+path }
      end
      if !page
        page = site.pages.detect { |page| page.path == path }
        puts "WARNING: link to #{path} in #{referrent ? referrent.path : "root"} uses legacy absolute syntax without leading slash" if page
      end

      throw "Could not find a page called: #{path} (referenced from #{referrent ? referrent.path : "root"})" unless page

      if (page.url.start_with?("/website"))
        page.url.slice!("/website")
        page.url.prepend(site.config['path']['website'])
      end
 
      page     
    end

    def generate(site)
      root_page = find_page_with_path_absolute_or_relative_to(site, SiteStructure::BROOKLYN_WEBSITE_ROOT, nil)
      navgroups = root_page.data['navgroups']
      navgroups.each do |ng|
        ng['page'] = find_page_with_path_absolute_or_relative_to(site, ng['page'], root_page)
        if not ng['title_in_menu']
          ng['title_in_menu'] = ng['title'].capitalize
        end
      end
      site.data['navgroups'] = navgroups
      site.data['structure'] = gen_structure(site, SiteStructure::BROOKLYN_WEBSITE_ROOT, nil, navgroups)
    end
    
    def gen_structure(site, pagename, parent, navgroups)
      page = find_page_with_path_absolute_or_relative_to(site, pagename, parent)
      
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
            c['reference'] = gen_structure(site, c['path'], page, navgroups)
          elsif c['link']
            # links to a non-site-structured page, on this site or elsewhere
            c['reference'] = { 'url' => c['link'], 'title' => c['title'] }
          end
        end
      end
      
      page
    end
  end
end
