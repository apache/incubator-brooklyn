# Builds a hierarchical structure for the site, based on the YAML front matter of each page
# Starts from a page called "index.md", and follows "children" links in the YAML front matter
module SiteStructure
 
  BROOKLYN_WEBSITE_ROOT = "/website/index.md" unless defined? BROOKLYN_WEBSITE_ROOT
  
  class Generator < Jekyll::Generator

    def find_page_with_path_absolute_or_relative_to(site, path, referrent, structure_processed_pages)
      uncleaned_path = path
      
      # Pathname API ignores first arg below if second is absolute
#      puts "converting #{path} wrt #{referrent ? referrent.path : ""}"
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
      site.data.merge!( gen_structure(site, { 'path' => SiteStructure::BROOKLYN_WEBSITE_ROOT }, nil, [], [], []).data )
#      puts "ROOT MENU is #{site.data['menu']}"
#      puts "C1 sub-menu is #{site.data['menu'][0].data['menu'}"
#but note after processing 'data' on pages is promoted, so you access it like:
#      puts "C1 sub-menu will be #{site.data['menu'][0]['menu'}"
    end

    # processes liquid tags, e.g. in a link or path object
    def render_liquid(site, page, content)
      return content unless page
      info = { :filters => [Jekyll::Filters], :registers => { :site => site, :page => page } }
      page.render_liquid(content, site.site_payload, info)
    end
        
    def gen_structure(site, item, parent, breadcrumb_pages, breadcrumb_paths, structure_processed_pages)
#      puts "gen_structure #{item}"
      breadcrumb_pages = breadcrumb_pages.dup
      breadcrumb_paths = breadcrumb_paths.dup
      if (item['path'])      
        page = find_page_with_path_absolute_or_relative_to(site, render_liquid(site, parent, item['path']), parent, structure_processed_pages)
        data = page.data
        data['path'] = page.path
        breadcrumb_pages << page
        breadcrumb_paths << page.path
      elsif (item['link'])
        data = { 'link' => render_liquid(site, parent, item['link']) }
        page = { 'data' => data }
        breadcrumb_pages << data
        breadcrumb_paths << item['link']
      else
        raise "Link to #{item} in #{parent ? parent.path : nil} must have link or path"
      end
      
      data['breadcrumb_pages'] = breadcrumb_pages
      data['breadcrumb_paths'] = breadcrumb_paths
      data['menu_parent'] = parent
      
      data['title_in_menu'] = item['title_in_menu'] || item['title'] || data['title_in_menu'] || data['title']
#      puts "built #{data}, now looking at children"
      
      if (data['children'])
        data['menu'] = []
        data['children'].each do |child|
          data['menu'] << gen_structure(site, child, page, breadcrumb_pages, breadcrumb_paths, structure_processed_pages)
        end
      end
      
      page
    end
    
  end

end
