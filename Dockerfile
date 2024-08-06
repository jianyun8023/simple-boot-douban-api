# Start with base image
FROM openjdk:8-jdk-alpine as build
WORKDIR /app
COPY ./ /app
RUN mvn install -DskipTests=true

# Start with base image
FROM openjdk:8-jdk-alpine

# Add Maintainer Info
LABEL maintainer="fugary"

# Add a temporary volume
VOLUME /tmp

# Expose Port 8085
EXPOSE 8085

ENV JAVA_OPTS="-Xmx512M"
ENV DOUBAN_CONCURRENCY_SIZE="5"
ENV DOUBAN_BOOK_CACHE_SIZE="1000"
ENV DOUBAN_BOOK_CACHE_EXPIRE="24h"
ENV DOUBAN_PROXY_IMAGE_URL="true"

# Application Jar File
ARG JAR_FILE=/app/target/simple-boot-douban-api-*.jar

# Add Application Jar File to the Container
COPY --from=build ${JAR_FILE} simple-boot-douban-api.jar

# Run the JAR file
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar /simple-boot-douban-api.jar"]
