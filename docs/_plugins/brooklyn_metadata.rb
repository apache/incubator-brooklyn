# Inserts several useful fields that can be referenced using {{ name }} syntax
#
# TODO: move things from fields.md to here
#
# site.brooklyn.version: brooklyn version, such as 0.7.0-M1 (taken from brooklyn-version in _config.yml)
# site.brooklyn.is_snapshot: true if this is a snapshot version, otherwise false
#
module BrooklynMetadata

  BROOKLYN_VERSION = "0.9.0-SNAPSHOT" unless defined? BROOKLYN_VERSION

  class Generator < Jekyll::Generator
    def generate(site)
      raise "Brooklyn version mismatch" if BrooklynMetadata::BROOKLYN_VERSION != site.config['brooklyn-version']

      is_snapshot = BrooklynMetadata::BROOKLYN_VERSION.end_with?('-SNAPSHOT')
      
      if is_snapshot
        git_branch = 'master' unless site.data['git_branch']
        url_set = {
            "search" => {
                "all" => "https://oss.sonatype.org/index.html#nexus-search;gav~io.brooklyn~~#{ BrooklynMetadata::BROOKLYN_VERSION }~~",
                "dist" => "https://oss.sonatype.org/index.html#nexus-search;gav~io.brooklyn~brooklyn-dist~#{ BrooklynMetadata::BROOKLYN_VERSION }~~",
            },
            "dist" => {
                "base" => "https://oss.sonatype.org/content/groups/public/io/brooklyn/brooklyn-dist/#{ BrooklynMetadata::BROOKLYN_VERSION }/",
                "zip" => "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn&v=#{ BrooklynMetadata::BROOKLYN_VERSION }&a=brooklyn-dist&c=dist&e=zip",
                "tgz" => "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn&v=#{ BrooklynMetadata::BROOKLYN_VERSION }&a=brooklyn-dist&c=dist&e=tar.gz"
            }
        }
        
      else
        git_branch = BrooklynMetadata::BROOKLYN_VERSION unless site.data['git_branch']
        url_set = {
            "search" => {
                "all" => "http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22io.brooklyn%22%20AND%20v%3A%22#{ BrooklynMetadata::BROOKLYN_VERSION }%22",
                "dist" => "http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22io.brooklyn%22%20AND%20a%3A%22brooklyn-dist%22%20AND%20v%3A%22#{ BrooklynMetadata::BROOKLYN_VERSION }%22",
            },
            "dist" => {
                "base" => "http://repo1.maven.org/maven2/io/brooklyn/brooklyn-dist/#{ BrooklynMetadata::BROOKLYN_VERSION }/",
                "zip" => "http://repo1.maven.org/maven2/io/brooklyn/brooklyn-dist/#{ BrooklynMetadata::BROOKLYN_VERSION }/brooklyn-dist-#{ BrooklynMetadata::BROOKLYN_VERSION }-dist.zip",
                "tgz" => "http://repo1.maven.org/maven2/io/brooklyn/brooklyn-dist/#{ BrooklynMetadata::BROOKLYN_VERSION }/brooklyn-dist-#{ BrooklynMetadata::BROOKLYN_VERSION }-dist.tar.gz"
            }
        }
      end
      
      url_set["git"] = "https://github.com/apache/incubator-brooklyn/tree/#{ git_branch }"
      
      site.config['brooklyn'] = {
          "version" => BrooklynMetadata::BROOKLYN_VERSION,
          "is_snapshot" => is_snapshot,
          "is_release" => !is_snapshot,
          "url" => url_set,
          "git_branch" => git_branch
      }

      # config is preferred of data, because you can write just {{ site.brooklyn.xxx }},
      # also note {{ site.brooklyn-version }} v {{ site.brooklyn-stable-version }} from _config.yml
      # but some places referenced site.data.brooklyn (i think these have been remove)
      site.data['brooklyn'] = site.config['brooklyn']
  
    end
  end
end
