use crate::client::BookLoader;
use crate::config::Config;
use crate::model::{BookVo, ResultVo};
use axum::{
    extract::{Path, Query, State},
    http::{HeaderMap, StatusCode},
    response::IntoResponse,
    Json,
};
use std::sync::Arc;
use tokio::task::JoinSet;
use tracing::info;

pub struct AppState {
    pub loader: BookLoader,
}

#[derive(serde::Deserialize)]
pub struct SearchParams {
    q: Option<String>,
}

#[derive(serde::Deserialize)]
pub struct ImageParams {
    cover: String,
}

pub async fn search_book(
    State(state): State<Arc<AppState>>,
    Query(params): Query<SearchParams>,
) -> Result<Json<ResultVo>, (StatusCode, String)> {
    let q = params.q.ok_or((StatusCode::BAD_REQUEST, "Query parameter 'q' is required".to_string()))?;

    if q.chars().all(char::is_numeric) && q.len() >= 10 {
         return search_isbn(State(state), Path(q)).await;
    }

    let start = std::time::Instant::now();
    let config = Config::global();
    let cat_type = "1001"; // book mapping

    let urls = state.loader.search_book_ids(&q, cat_type).await;
    info!("查询列表{}条耗时{:?}", urls.len(), start.elapsed());

    let mut result_vo = ResultVo::default();
    result_vo.books = Vec::new();

    let max_count = config.concurrency_size;
    let limit = std::cmp::min(urls.len(), max_count);

    // We only take 'limit' items as per Java code: list.size() < doubanApiConfigProperties.count()
    let target_urls = &urls[0..limit];

    let mut set = JoinSet::new();
    for url in target_urls {
        let loader = state.clone(); // Arc clone
        let url = url.clone();
        set.spawn(async move {
            loader.loader.load_book(&url).await
        });
    }

    while let Some(res) = set.join_next().await {
        if let Ok(Some(mut book)) = res {
            process_book_image(&mut book, &config);
            result_vo.books.push(book);
            result_vo.success = true;
        }
    }

    info!("查询书籍{}条完成耗时{:?}", result_vo.books.len(), start.elapsed());
    Ok(Json(result_vo))
}

pub async fn search_isbn(
    State(state): State<Arc<AppState>>,
    Path(isbn): Path<String>,
) -> Result<Json<ResultVo>, (StatusCode, String)> {
    let config = Config::global();
    let url = config.isbn_url.replace("{isbn}", &isbn);
    detail_result(&state, url).await
}

pub async fn detail(
    State(state): State<Arc<AppState>>,
    Path(id): Path<String>,
) -> Result<Json<ResultVo>, (StatusCode, String)> {
    let config = Config::global();
    let url = config.detail_url.replace("{id}", &id);
    detail_result(&state, url).await
}

async fn detail_result(state: &Arc<AppState>, url: String) -> Result<Json<ResultVo>, (StatusCode, String)> {
    let start = std::time::Instant::now();
    let book = state.loader.load_book(&url).await;

    let mut result_vo = ResultVo::default();
    if let Some(mut b) = book {
        process_book_image(&mut b, Config::global());
        result_vo.books = vec![b];
        result_vo.success = true;
    }

    info!("精确查询耗时{:?}", start.elapsed());
    Ok(Json(result_vo))
}

pub async fn view_image(
    State(state): State<Arc<AppState>>,
    Query(params): Query<ImageParams>,
) -> impl IntoResponse {
    let bytes = state.loader.load_image(&params.cover).await;
    if let Some(data) = bytes {
        let mut headers = HeaderMap::new();
        headers.insert("Content-Type", "image/jpeg".parse().unwrap());
        (StatusCode::OK, headers, data)
    } else {
        (StatusCode::OK, HeaderMap::new(), vec![])
    }
}

fn process_book_image(book: &mut BookVo, config: &Config) {
    if !config.proxy_image_url {
        return;
    }
    if let Some(img) = &book.image {
        // Construct proxy URL.
        // In Java: uses UriBuilder with baseUri from request.
        // Here, we don't easily have the absolute request URL unless we extract it.
        // However, usually proxies are relative or configured.
        // The Java code builds it dynamically: /view/cover?cover=...
        // We can just set it to a relative path or absolute if we knew the host.
        // For now, let's assume relative path /view/cover is enough if the client handles it,
        // or we can try to guess or use a config for "Self Base URL".
        // Java code:
        // UriBuilder.fromUri(baseUri).path("/view/cover")...

        // We will just replace it with the relative path which acts as the proxy.
        // Or better, let's look at `Config`. Java didn't have a config for "SELF_URL", it used request info.
        // We'll use a relative path since that works for same-origin.

        let new_url = format!("/view/cover?cover={}", img);
        book.image = Some(new_url);
    }
}
