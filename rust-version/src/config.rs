use std::env;
use std::sync::OnceLock;

#[derive(Debug)]
pub struct Config {
    pub port: u16,
    pub base_url: String,
    pub search_url: String,
    pub detail_url: String,
    pub isbn_url: String,
    pub proxy_image_url: bool,
    pub concurrency_size: usize,
    pub book_cache_size: u64,
    pub book_cache_expire_hours: u64,
    pub image_cache_size: u64,
    pub image_cache_expire_hours: u64,
}

impl Config {
    pub fn global() -> &'static Config {
        static CONFIG: OnceLock<Config> = OnceLock::new();
        CONFIG.get_or_init(|| {
            dotenvy::dotenv().ok();
            Config {
                port: env::var("QUARKUS_HTTP_PORT")
                    .or_else(|_| env::var("PORT"))
                    .unwrap_or_else(|_| "8085".to_string())
                    .parse()
                    .unwrap_or(8085),
                base_url: env::var("DOUBAN_API_BASE_URL")
                    .unwrap_or_else(|_| "https://book.douban.com/".to_string()),
                search_url: env::var("DOUBAN_API_SEARCH_URL")
                    .unwrap_or_else(|_| "https://www.douban.com/search?cat={searchType}&q={searchText}".to_string()),
                detail_url: env::var("DOUBAN_API_DETAIL_URL")
                    .unwrap_or_else(|_| "https://book.douban.com/subject/{id}/".to_string()),
                isbn_url: env::var("DOUBAN_API_ISBN_URL")
                    .unwrap_or_else(|_| "https://book.douban.com/isbn/{isbn}/".to_string()),
                proxy_image_url: env::var("DOUBAN_PROXY_IMAGE_URL")
                    .unwrap_or_else(|_| "true".to_string())
                    .parse()
                    .unwrap_or(true),
                concurrency_size: env::var("DOUBAN_CONCURRENCY_SIZE")
                    .unwrap_or_else(|_| "3".to_string())
                    .parse()
                    .unwrap_or(3),
                book_cache_size: env::var("DOUBAN_BOOK_CACHE_SIZE")
                    .unwrap_or_else(|_| "1000".to_string())
                    .parse()
                    .unwrap_or(1000),
                book_cache_expire_hours: env::var("DOUBAN_BOOK_CACHE_EXPIRE")
                    .unwrap_or_else(|_| "24".to_string()) // Simplified, Java used "24h" format, here assume hours or parse int
                    .replace("h", "")
                    .parse()
                    .unwrap_or(24),
                image_cache_size: env::var("DOUBAN_IMAGE_CACHE_SIZE") // Assuming name, original reused BOOK_CACHE_SIZE config for both? Java file says "quarkus.cache.caffeine.doubanImage.maximum-size=${DOUBAN_BOOK_CACHE_SIZE:1000}"
                    .unwrap_or_else(|_| "1000".to_string())
                    .parse()
                    .unwrap_or(1000),
                image_cache_expire_hours: 24,
            }
        })
    }
}
