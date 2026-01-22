use crate::model::BookVo;
use regex::Regex;
use scraper::{Html, Selector};
use std::collections::HashMap;
use std::sync::OnceLock;
use tracing::{error, info};

pub struct BookHtmlParser;

impl BookHtmlParser {
    pub fn parse(url: &str, html_content: &str) -> Option<BookVo> {
        let document = Html::parse_document(html_content);
        let content_sel = Selector::parse("body").unwrap();
        let content = document.select(&content_sel).next();

        if content.is_none() {
            error!("获取书籍失败：{}", url);
            return None;
        }
        let content = content.unwrap();

        let mut book_vo = BookVo::default();

        // Title
        let title_sel = Selector::parse("[property='v:itemreviewed']").unwrap();
        if let Some(title_el) = document.select(&title_sel).next() {
            book_vo.title = Some(title_el.text().collect::<String>());
        }

        // URL & ID
        let share_sel = Selector::parse("a.bn-sharing").unwrap();
        let mut final_url = url.to_string();
        if let Some(share_el) = content.select(&share_sel).next() {
            if let Some(data_url) = share_el.value().attr("data-url") {
                final_url = data_url.to_string();
            }
        }
        book_vo.url = Some(final_url.clone());

        static ID_REGEX: OnceLock<Regex> = OnceLock::new();
        let id_regex = ID_REGEX.get_or_init(|| Regex::new(r".*/subject/(\d+)/?").unwrap());
        if let Some(caps) = id_regex.captures(&final_url) {
            book_vo.id = Some(caps.get(1).unwrap().as_str().to_string());
        }

        // Image
        let nbg_sel = Selector::parse("a.nbg").unwrap();
        if let Some(nbg) = content.select(&nbg_sel).next() {
            if let Some(href) = nbg.value().attr("href") {
                book_vo.image = Some(href.to_string());
            }
        }

        // Rating
        let rate_sel = Selector::parse("[property='v:average']").unwrap();
        if let Some(rate_el) = content.select(&rate_sel).next() {
            let mut rating_map = HashMap::new();
            rating_map.insert("average".to_string(), rate_el.text().collect::<String>().trim().to_string());
            book_vo.rating = Some(rating_map);
        }

        // Details (Author, Publisher, etc.)
        let pl_sel = Selector::parse("span.pl").unwrap();
        for element in content.select(&pl_sel) {
            let text = element.text().collect::<String>();
            let text = text.trim();
            let is_translator = text.starts_with("译者");

            if text.starts_with("作者") || is_translator {
                let mut authors = Vec::new();
                // Siblings are tricky in scraper. We iterate next_siblings.
                // In Jsoup: element.nextElementSiblings()
                // In scraper: we iterate the node's siblings.

                let mut next_sibling = element.next_sibling();
                while let Some(node) = next_sibling {
                     if let Some(el) = scraper::ElementRef::wrap(node) {
                        if el.value().name() == "a" {
                             let author_text = el.text().collect::<String>();
                             // Split by slash and trim
                             for part in author_text.split('/') {
                                 let p = part.trim();
                                 if !p.is_empty() {
                                     authors.push(p.to_string());
                                 }
                             }
                        } else if el.value().name() == "br" {
                            break;
                        } else {
                            // might be span or text, check logic
                        }
                     } else if node.value().is_text() {
                         // Some text like ": " or " " or " / "
                     }
                     next_sibling = node.next_sibling();
                }

                // Fallback or adjustment if structure is different?
                // The Java code: element.nextElementSiblings(), breaks on <br>.
                // `authors.addAll(Arrays.stream(authorElement.text().split("\\s*/\\s*"))...)`

                if is_translator {
                    book_vo.translator = Some(authors);
                } else {
                    book_vo.author = Some(authors);
                }

            } else if text.starts_with("原作名") {
                book_vo.origin_title = Some(get_info(element));
            } else if text.starts_with("副标题") {
                book_vo.sub_title = Some(get_info(element));
            } else if text.starts_with("出版社") {
                book_vo.publisher = Some(get_info_or_next(element));
            } else if text.starts_with("出版年") {
                book_vo.publish_date = get_info(element);
            } else if text.starts_with("ISBN") {
                book_vo.isbn13 = Some(get_info(element));
            } else if text.starts_with("页数") {
                book_vo.pages = Some(get_info(element));
            } else if text.starts_with("定价") {
                book_vo.price = Some(get_info(element));
            } else if text.starts_with("装帧") {
                book_vo.binding = Some(get_info(element));
            } else if text.starts_with("丛书") {
                // Next element sibling
                let mut next_sibling = element.next_sibling();
                while let Some(node) = next_sibling {
                    if let Some(el) = scraper::ElementRef::wrap(node) {
                        // Found next element
                        let series_title = el.text().collect::<String>();
                        let series_href = el.value().attr("href").unwrap_or("");

                        static SERIES_REGEX: OnceLock<Regex> = OnceLock::new();
                        let series_regex = SERIES_REGEX.get_or_init(|| Regex::new(r".*/series/(\d+)/?").unwrap());

                        let mut series_map = HashMap::new();
                        if let Some(caps) = series_regex.captures(series_href) {
                            if let Some(id) = caps.get(1) {
                                series_map.insert("id".to_string(), id.as_str().to_string());
                            }
                        }
                        series_map.insert("title".to_string(), series_title);
                        book_vo.series = Some(series_map);
                        break;
                    }
                    if node.value().is_text() && !node.value().as_text().unwrap().trim().is_empty() {
                         // If we hit non-whitespace text before an element, maybe structure is different?
                         // But for "丛书", usually it's `<span>丛书:</span> <a href="...">Name</a>`
                    }
                    next_sibling = node.next_sibling();
                }
            }
        }

        // Summary
        let summary_sel = Selector::parse("#link-report :not(.short) .intro").unwrap();
        if let Some(summary_el) = content.select(&summary_sel).next() {
            book_vo.summary = Some(summary_el.inner_html().trim().to_string());
        }

        // Tags
        static TAGS_REGEX: OnceLock<Regex> = OnceLock::new();
        let tags_regex = TAGS_REGEX.get_or_init(|| Regex::new(r"criteria = '(.+)'").unwrap());
        if let Some(caps) = tags_regex.captures(html_content) {
             let tags_str = caps.get(1).unwrap().as_str();
             let tags: Vec<HashMap<String, String>> = tags_str.split('|')
                .filter(|t| t.starts_with("7:"))
                .map(|t| t.replace("7:", ""))
                // Java uses distinct(), strict mapping.
                // We'll just map
                .map(|t| {
                    let mut m = HashMap::new();
                    m.insert("name".to_string(), t.clone());
                    m.insert("title".to_string(), t);
                    m
                })
                .collect();
             if !tags.is_empty() {
                 book_vo.tags = Some(tags);
             }
        }

        info!("解析书籍成功: {:?}", book_vo.title);
        Some(book_vo)
    }
}

// Helpers
fn get_info(element: scraper::ElementRef) -> String {
    // Java: element.nextSibling().toString().trim()
    // It assumes the info is a text node immediately following the span.
    if let Some(node) = element.next_sibling() {
        if let Some(text) = node.value().as_text() {
            return text.trim().to_string();
        }
    }
    "".to_string()
}

fn get_info_or_next(element: scraper::ElementRef) -> String {
    // Java: getInfo then check isBlank, if so check nextElementSibling
    let mut info = get_info(element);
    if info.is_empty() {
        // Find next element sibling
        let mut next = element.next_sibling();
        while let Some(node) = next {
            if let Some(el) = scraper::ElementRef::wrap(node) {
                info = el.text().collect::<String>().trim().to_string();
                break;
            }
            next = node.next_sibling();
        }
    }
    info
}
