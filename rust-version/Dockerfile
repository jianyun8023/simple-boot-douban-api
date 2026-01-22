# Stage 1: Build
FROM rust:1.76-slim-bookworm as builder

WORKDIR /usr/src/app
COPY . .

RUN cargo install --path .

# Stage 2: Runtime
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*

COPY --from=builder /usr/local/cargo/bin/simple-boot-douban-api-rust /usr/local/bin/simple-boot-douban-api-rust

ENV DOUBAN_API_BASE_URL="https://book.douban.com/"
ENV QUARKUS_HTTP_PORT=8085

EXPOSE 8085

CMD ["simple-boot-douban-api-rust"]
