mod client;
mod config;
mod handler;
mod model;
mod parser;

use crate::client::BookLoader;
use crate::config::Config;
use crate::handler::{AppState, detail, search_book, search_isbn, view_image};
use axum::{
    routing::get,
    Router,
};
use std::net::SocketAddr;
use std::sync::Arc;
use tracing::info;

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();

    let config = Config::global();
    let state = Arc::new(AppState {
        loader: BookLoader::new(),
    });

    let app = Router::new()
        .route("/v2/book/search", get(search_book))
        .route("/v2/book/isbn/:isbn", get(search_isbn))
        .route("/v2/book/:id", get(detail))
        .route("/view/cover", get(view_image))
        .with_state(state);

    let addr = SocketAddr::from(([0, 0, 0, 0], config.port));
    info!("listening on {}", addr);
    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
