# frozen_string_literal: true

require 'digest'
require 'fileutils'
require 'json'
require 'net/http'
require 'open3'
require 'uri'

# Downloads the source datasets into pipeline/data/. Each dataset is fetched
# only when missing; pass refresh: true to re-fetch the "latest release" ones.
# Versions are recorded in data/versions.json and copied into the database's
# meta table.
module Sources
  DATA_DIR = File.expand_path('../data', __dir__)
  VERSIONS_PATH = File.join(DATA_DIR, 'versions.json')
  USER_AGENT = 'mamejibiki-pipeline'

  JMDICT_RELEASES = 'https://api.github.com/repos/scriptin/jmdict-simplified/releases/latest'
  FURIGANA_RELEASES = 'https://api.github.com/repos/Doublevil/JmdictFurigana/releases/latest'

  # Pinned: JPDB v2 is static, and the Wadoku extract hasn't changed in years.
  JPDB_URL = 'https://github.com/Kuuuube/yomitan-dictionaries/raw/main/dictionaries/JPDB_v2.2_Frequency_Kana_2024-10-13.zip'
  WADOKU_URL = 'https://raw.githubusercontent.com/IllDepence/anki_add_pitch_plugin/master/src/wadoku_pitchdb.csv'
  WADOKU_SHA256 = '5517407a34a54247df7941bf7f6678006fa83ac114699090d825f7783f10116c'

  JMDICT_PATH = File.join(DATA_DIR, 'jmdict-eng.json')
  FURIGANA_PATH = File.join(DATA_DIR, 'JmdictFurigana.json')
  JPDB_DIR = File.join(DATA_DIR, 'jpdb')
  WADOKU_PATH = File.join(DATA_DIR, 'wadoku_pitchdb.csv')

  class << self
    def fetch_all!(refresh: false)
      FileUtils.mkdir_p(DATA_DIR)
      versions = load_versions
      fetch_jmdict!(versions) if refresh || !File.exist?(JMDICT_PATH)
      fetch_furigana!(versions) if refresh || !File.exist?(FURIGANA_PATH)
      fetch_jpdb!(versions) unless Dir.exist?(JPDB_DIR)
      fetch_wadoku!(versions) unless File.exist?(WADOKU_PATH)
      File.write(VERSIONS_PATH, JSON.pretty_generate(versions))
      versions
    end

    private

    def fetch_jmdict!(versions)
      release = latest_release(JMDICT_RELEASES)
      asset = release['assets'].find { |a| a['name'].match?(/\Ajmdict-eng-\d.*\.json\.tgz\z/) }
      tgz = File.join(DATA_DIR, asset['name'])
      download(asset['browser_download_url'], tgz)
      run('tar', '-xzf', tgz, '-C', DATA_DIR)
      File.delete(tgz)
      extracted = Dir.glob(File.join(DATA_DIR, 'jmdict-eng-*.json'))
      raise "Expected one extracted JMdict file, found #{extracted.inspect}" unless extracted.size == 1

      FileUtils.mv(extracted.first, JMDICT_PATH)
      versions['jmdict'] = release['tag_name']
    end

    def fetch_furigana!(versions)
      release = latest_release(FURIGANA_RELEASES)
      asset = release['assets'].find { |a| a['name'] == 'JmdictFurigana.json' }
      download(asset['browser_download_url'], FURIGANA_PATH)
      versions['jmdict_furigana'] = release['tag_name']
    end

    def fetch_jpdb!(versions)
      zip = File.join(DATA_DIR, File.basename(JPDB_URL))
      download(JPDB_URL, zip)
      run('unzip', '-o', '-q', zip, '-d', JPDB_DIR)
      File.delete(zip)
      versions['jpdb'] = File.basename(JPDB_URL, '.zip')
    end

    def fetch_wadoku!(versions)
      download(WADOKU_URL, WADOKU_PATH)
      actual = Digest::SHA256.file(WADOKU_PATH).hexdigest
      raise "Wadoku pitch DB checksum mismatch (got #{actual})" unless actual == WADOKU_SHA256

      versions['wadoku'] = "sha256:#{WADOKU_SHA256[0, 12]}"
    end

    def latest_release(api_url)
      JSON.parse(http_get(api_url))
    end

    def download(url, path)
      puts "  downloading #{url}"
      File.binwrite(path, http_get(url))
    end

    def http_get(url, limit = 5)
      raise "Too many redirects for #{url}" if limit.zero?

      uri = URI(url)
      response = Net::HTTP.start(uri.host, uri.port, use_ssl: uri.scheme == 'https',
                                                     open_timeout: 30, read_timeout: 120) do |http|
        request = Net::HTTP::Get.new(uri)
        request['User-Agent'] = USER_AGENT
        http.request(request)
      end

      case response
      when Net::HTTPSuccess then response.body
      when Net::HTTPRedirection then http_get(response['location'], limit - 1)
      else raise "HTTP #{response.code} for #{url}"
      end
    end

    def run(*cmd)
      _out, err, status = Open3.capture3(*cmd)
      raise "#{cmd.first} failed: #{err}" unless status.success?
    end

    def load_versions
      File.exist?(VERSIONS_PATH) ? JSON.parse(File.read(VERSIONS_PATH)) : {}
    end
  end
end
