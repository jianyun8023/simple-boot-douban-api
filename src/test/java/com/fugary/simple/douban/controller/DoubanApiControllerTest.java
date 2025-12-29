package com.fugary.simple.douban.controller;

import com.fugary.simple.douban.loader.BookLoader;
import com.fugary.simple.douban.service.DoubanHtmlService;
import com.fugary.simple.douban.vo.BookVo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
public class DoubanApiControllerTest {

    @InjectMock
    BookLoader bookLoader;

    @InjectMock
    DoubanHtmlService doubanHtmlService;

    @BeforeEach
    public void setup() {
        BookVo bookVo = new BookVo();
        bookVo.setTitle("Test Book");
        bookVo.setAuthor(Collections.singletonList("Test Author"));
        bookVo.setImage("http://example.com/image.jpg");

        Mockito.when(bookLoader.loadBook(anyString())).thenReturn(bookVo);
        Mockito.when(bookLoader.loadImage(anyString())).thenReturn(new byte[]{1, 2, 3});

        // Mock Html Service
        List<Element> elements = new ArrayList<>();
        Element element = new Element("a");
        element.attr("href", "https://book.douban.com/subject/123/?url=https://book.douban.com/subject/123456/");
        elements.add(element);
        Mockito.when(doubanHtmlService.searchBookElements(anyString(), anyString())).thenReturn(elements);
    }

    @Test
    public void testSearchBook() {
        given()
                .when().get("/v2/book/search?q=test")
                .then()
                .statusCode(200)
                .body("books[0].title", is("Test Book"));
    }

    @Test
    public void testSearchIsbn() {
        given()
                .when().get("/v2/book/isbn/1234567890")
                .then()
                .statusCode(200)
                .body("books[0].title", is("Test Book"));
    }

    @Test
    public void testDetail() {
        given()
                .when().get("/v2/book/123456")
                .then()
                .statusCode(200)
                .body("books[0].title", is("Test Book"));
    }

    @Test
    public void testDetailV2() {
        // This is the new path we want to support
        given()
                .when().get("/v2/1896753")
                .then()
                .statusCode(200)
                .body("books[0].title", is("Test Book"));
    }

    @Test
    public void testViewCover() {
        given()
                .queryParam("cover", "http://example.com/image.jpg")
                .when().get("/view/cover")
                .then()
                .statusCode(200);
    }
}
