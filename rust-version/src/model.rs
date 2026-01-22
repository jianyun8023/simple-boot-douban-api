use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ResultVo {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub count: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub start: Option<i32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub total: Option<i32>,
    pub books: Vec<BookVo>,
    pub success: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct BookVo {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub title: Option<String>,
    #[serde(rename = "origin_title", skip_serializing_if = "Option::is_none")]
    pub origin_title: Option<String>,
    #[serde(rename = "sub_title", skip_serializing_if = "Option::is_none")]
    pub sub_title: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub author: Option<Vec<String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub translator: Option<Vec<String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub summary: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub publisher: Option<String>,
    #[serde(rename = "pubdate", default = "default_pubdate")]
    pub publish_date: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tags: Option<Vec<MapItem>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub rating: Option<HashMap<String, String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub series: Option<HashMap<String, String>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub image: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub isbn13: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub isbn10: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub pages: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub binding: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub price: Option<String>,
    #[serde(rename = "author_intro", skip_serializing_if = "Option::is_none")]
    pub author_intro: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub catalog: Option<String>,
    #[serde(rename = "ebook_url", skip_serializing_if = "Option::is_none")]
    pub ebook_url: Option<String>,
    #[serde(rename = "ebook_price", skip_serializing_if = "Option::is_none")]
    pub ebook_price: Option<String>,
}

// Java uses Map<String, String> for tags, but code shows `tagMap.put("name", tag); tagMap.put("title", tag);`
// So it is essentially a list of objects with name and title.
type MapItem = HashMap<String, String>;

fn default_pubdate() -> String {
    "1900-01".to_string()
}
