# Start with base image
FROM vegardit/graalvm-maven:latest-java17 AS build
WORKDIR /app
COPY ./ /app
RUN --mount=type=cache,id=maven,target=/mvn/store mvn -Dmaven.repo.local=/mvn/store -Pnative -DskipTests=true clean  native:compile
RUN upx /app/target/simple-boot-douban-api
# Start with base image
FROM debian:bookworm-slim

WORKDIR /app
# Add Maintainer Info
LABEL maintainer="jianyun8023"

# Add a temporary volume
VOLUME /tmp

# Expose Port 8085
EXPOSE 8085

ENV JAVA_OPTS="-Xmx512M"
ENV DOUBAN_CONCURRENCY_SIZE="5"
ENV DOUBAN_BOOK_CACHE_SIZE="1000"
ENV DOUBAN_BOOK_CACHE_EXPIRE="24h"
ENV DOUBAN_PROXY_IMAGE_URL="true"

# Add Application Jar File to the Container
COPY --from=build /app/target/simple-boot-douban-api simple-boot-douban-api

# Run the JAR file
ENTRYPOINT ["bash","-c","/app/simple-boot-douban-api"]