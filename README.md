# 豆瓣图书API

这是一个基于Quarkus构建的豆瓣图书API，用于从豆瓣网站抓取图书信息。

## API 接口

### 1. 搜索图书

- **URL:** `/v2/book/search`
- **Method:** `GET`
- **Query Parameter:** `q` (搜索关键词)
- **Example:** `http://localhost:8085/v2/book/search?q=深入理解计算机系统`

### 2. 根据ISBN获取图书信息

- **URL:** `/v2/book/isbn/{isbn}`
- **Method:** `GET`
- **Path Parameter:** `isbn` (图书的ISBN)
- **Example:** `http://localhost:8085/v2/book/isbn/9787111559573`

### 3. 根据ID获取图书信息

- **URL:** `/v2/book/{id}`
- **Method:** `GET`
- **Path Parameter:** `id` (豆瓣图书ID)
- **Example:** `http://localhost:8085/v2/book/27079527`

## 使用Docker启动

### 拉取镜像

```shell
docker pull fugary/simple-boot-douban-api
```

### 启动容器

```shell
docker run -it -p 8085:8085 fugary/simple-boot-douban-api
```

### 自定义配置

您可以通过环境变量来自定义应用的配置：

- `DOUBAN_CONCURRENCY_SIZE`: 并发查询线程数 (默认: `5`)
- `DOUBAN_BOOK_CACHE_SIZE`: 图书缓存数量 (默认: `1000`)
- `DOUBAN_BOOK_CACHE_EXPIRE`: 图书缓存过期时间 (默认: `24h`)
- `DOUBAN_PROXY_IMAGE_URL`: 是否代理图片地址 (默认: `true`)

例如，要修改并发线程数和缓存大小，可以使用以下命令：

```shell
docker run -it -p 8085:8085 \
  -e DOUBAN_CONCURRENCY_SIZE=10 \
  -e DOUBAN_BOOK_CACHE_SIZE=2000 \
  fugary/simple-boot-douban-api
```
