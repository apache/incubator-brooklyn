# Inserts several useful fields that can be referenced using {{ name }} syntax
#
# site.data.brooklyn.version: brooklyn version, such as 0.7.0-M1
# site.data.brooklyn.is_snapshot: true if this is a snapshot version, otherwise false
# site.data.brooklyn.url.userguide: URL of the user guide for this version
#
module BrooklynMetadata

  BROOKLYN_VERSION = "0.7.0-M1" unless defined? BROOKLYN_VERSION

  class Generator < Jekyll::Generator
    def generate(site)
      is_snapshot = BrooklynMetadata::BROOKLYN_VERSION.end_with?('-SNAPSHOT')
      
      if is_snapshot
        url_set = {
            "search" => {
                "all" => "https://oss.sonatype.org/index.html#nexus-search;gav~io.brooklyn~~#{ BrooklynMetadata::BROOKLYN_VERSION }~~",
                "dist" => "https://oss.sonatype.org/index.html#nexus-search;gav~io.brooklyn~brooklyn-dist~#{ BrooklynMetadata::BROOKLYN_VERSION }~~",
                "alljar" => "https://oss.sonatype.org/index.html#nexus-search;gav~io.brooklyn~brooklyn-dist~#{ BrooklynMetadata::BROOKLYN_VERSION }~~"
            },
            "dist" => {
                "base" => "https://oss.sonatype.org/content/groups/public/io/brooklyn/brooklyn-dist/#{ BrooklynMetadata::BROOKLYN_VERSION }/",
                "zip" => "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn&v=#{ BrooklynMetadata::BROOKLYN_VERSION }&a=brooklyn-dist&c=dist&e=zip",
                "tgz" => "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn&v=#{ BrooklynMetadata::BROOKLYN_VERSION }&a=brooklyn-dist&c=dist&e=tar.gz"
            },
            "alljar" => {
                "base" => "https://oss.sonatype.org/content/groups/public/io/brooklyn/brooklyn-all/#{ BrooklynMetadata::BROOKLYN_VERSION }/",
                "jar" => "https://oss.sonatype.org/service/local/artifact/maven/redirect?r=snapshots&g=io.brooklyn&v=#{ BrooklynMetadata::BROOKLYN_VERSION }}&a=brooklyn-all&c=with-dependencies&e=jar"
            }
        }
      else
        url_set = {
            "search" => {
                "all" => "http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22io.brooklyn%22%20AND%20v%3A%22#{ BrooklynMetadata::BROOKLYN_VERSION }%22",
                "dist" => "http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22io.brooklyn%22%20AND%20a%3A%22brooklyn-dist%22%20AND%20v%3A%22#{ BrooklynMetadata::BROOKLYN_VERSION }%22",
                "alljar" => "http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22io.brooklyn%22%20AND%20a%3A%22brooklyn-all%22%20AND%20v%3A%22#{ BrooklynMetadata::BROOKLYN_VERSION }%22"
            },
            "dist" => {
                "base" => "http://repo1.maven.org/maven2/io/brooklyn/brooklyn-dist/#{ BrooklynMetadata::BROOKLYN_VERSION }/",
                "zip" => "http://repo1.maven.org/maven2/io/brooklyn/brooklyn-dist/#{ BrooklynMetadata::BROOKLYN_VERSION }/brooklyn-dist-#{ BrooklynMetadata::BROOKLYN_VERSION }-dist.zip",
                "tgz" => "http://repo1.maven.org/maven2/io/brooklyn/brooklyn-dist/#{ BrooklynMetadata::BROOKLYN_VERSION }/brooklyn-dist-#{ BrooklynMetadata::BROOKLYN_VERSION }-dist.tar.gz"
            },
            "alljar" => {
                "base" => "http://repo1.maven.org/maven2/io/brooklyn/brooklyn-all/#{ BrooklynMetadata::BROOKLYN_VERSION }/",
                "jar" => "http://repo1.maven.org/maven2/io/brooklyn/brooklyn-all/#{ BrooklynMetadata::BROOKLYN_VERSION }/brooklyn-all-#{ BrooklynMetadata::BROOKLYN_VERSION }-with-dependencies.jar"
            }
        }
      end
      
      url_set['userguide'] = "#{site.config['url']}/v/#{BrooklynMetadata::BROOKLYN_VERSION}"
      
      site.data['brooklyn'] = {
          "version" => BrooklynMetadata::BROOKLYN_VERSION,
          "is_snapshot" => is_snapshot,
          "url" => url_set
      }
    end
  end
end
