use crate::config::Config;
use crate::model::BookVo;
use crate::parser::BookHtmlParser;
use moka::future::Cache;
use reqwest::Client;
use std::time::Duration;
use tracing::{error, info, warn};

pub struct BookLoader {
    client: Client,
    book_cache: Cache<String, BookVo>,
    image_cache: Cache<String, Vec<u8>>,
    base_url: String,
}

impl BookLoader {
    pub fn new() -> Self {
        let config = Config::global();
        // Redirect policy: Java used manual recursion up to 5.
        // Reqwest default is 10. We can stick to default or custom.
        // Since we want to mimic the manual handling which might handle specific 302 location logic?
        // Actually, 302/301 are standard. The manual logic in Java might just be for control or logging.
        // Let's rely on reqwest first, but set redirect policy to restricted if we want to mimic exact count.
        let client = Client::builder()
            .redirect(reqwest::redirect::Policy::limited(5))
            .build()
            .unwrap();

        let book_cache = Cache::builder()
            .max_capacity(config.book_cache_size)
            .time_to_live(Duration::from_secs(config.book_cache_expire_hours * 3600))
            .build();

        let image_cache = Cache::builder()
            .max_capacity(config.image_cache_size)
            .time_to_live(Duration::from_secs(config.image_cache_expire_hours * 3600))
            .build();

        BookLoader {
            client,
            book_cache,
            image_cache,
            base_url: config.base_url.clone(),
        }
    }

    pub async fn load_book(&self, url: &str) -> Option<BookVo> {
        if let Some(book) = self.book_cache.get(url).await {
            return Some(book);
        }

        let book_opt = self.load_book_internal(url).await;
        if let Some(ref book) = book_opt {
            self.book_cache.insert(url.to_string(), book.clone()).await;
        }
        book_opt
    }

    async fn load_book_internal(&self, url: &str) -> Option<BookVo> {
        // We'll trust Reqwest to handle redirects.
        // But we need to set headers.
        match self.client.get(url)
            .header("User-Agent", get_user_agent())
            .header("Referer", &self.base_url)
            .send()
            .await
        {
            Ok(resp) => {
                if resp.status().is_success() {
                    let html = resp.text().await.unwrap_or_default();
                    BookHtmlParser::parse(url, &html)
                } else {
                    warn!("Request failed for {}: {}", url, resp.status());
                    None
                }
            }
            Err(e) => {
                error!("Error fetching book {}: {}", url, e);
                None
            }
        }
    }

    pub async fn load_image(&self, url: &str) -> Option<Vec<u8>> {
        if let Some(img) = self.image_cache.get(url).await {
            return Some(img);
        }

        match self.client.get(url)
            .header("User-Agent", get_user_agent())
            .header("Referer", &self.base_url)
            .send()
            .await
        {
            Ok(resp) => {
                 if resp.status().is_success() {
                     let bytes = resp.bytes().await.unwrap_or_default().to_vec();
                     if !bytes.is_empty() {
                         info!("获取{}图片成功", url);
                         self.image_cache.insert(url.to_string(), bytes.clone()).await;
                         Some(bytes)
                     } else {
                         None
                     }
                 } else {
                     warn!("Image fetch failed {}: {}", url, resp.status());
                     None
                 }
            }
            Err(e) => {
                error!("获取{}图片异常: {}", url, e);
                None
            }
        }
    }

    pub async fn search_book_ids(&self, search_text: &str, cat_type: &str) -> Vec<String> {
        let config = Config::global();
        let search_url = config.search_url
            .replace("{searchType}", cat_type)
            .replace("{searchText}", search_text);

        // This part mimics DoubanHtmlService.searchBookElements
        // It returns a list of URLs to fetch details for.
        match self.client.get(&search_url)
            .header("User-Agent", get_user_agent())
            .send()
            .await
        {
            Ok(resp) => {
                let html = resp.text().await.unwrap_or_default();
                let document = scraper::Html::parse_document(&html);
                let selector = scraper::Selector::parse("a.nbg").unwrap();

                let mut urls = Vec::new();
                for element in document.select(&selector) {
                    if let Some(href) = element.value().attr("href") {
                         // Parse query to get 'url' param if it exists, or use href direct?
                         // Java: Map map = DoubanUrlUtils.parseQuery(URI.create(href).getQuery()); String url = map.get("url");
                         // Douban search results often redirect via https://www.douban.com/link2/?url=...
                         // Let's parse it.
                         if let Ok(u) = url::Url::parse(href) {
                             let pairs: std::collections::HashMap<_, _> = u.query_pairs().into_owned().collect();
                             if let Some(target_url) = pairs.get("url") {
                                  // isBookUrl check
                                  if is_book_url(target_url) {
                                      urls.push(target_url.clone());
                                  }
                             }
                         }
                    }
                }
                urls
            }
            Err(e) => {
                error!("Search failed: {}", e);
                Vec::new()
            }
        }
    }
}

fn get_user_agent() -> &'static str {
    "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3573.0 Safari/537.36"
}

fn is_book_url(url: &str) -> bool {
    // Basic regex check from Java code
    // ID_PATTERN = Pattern.compile(".*/subject/(\\d+)/?");
    let re = regex::Regex::new(r".*/subject/(\d+)/?").unwrap();
    re.is_match(url)
}
