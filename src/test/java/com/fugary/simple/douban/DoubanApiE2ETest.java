package com.fugary.simple.douban;

import com.fugary.simple.douban.loader.BookLoader;
import com.fugary.simple.douban.service.DoubanHtmlService;
import com.fugary.simple.douban.vo.BookVo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
public class DoubanApiE2ETest {

    @InjectMock
    BookLoader bookLoader;

    @InjectMock
    DoubanHtmlService doubanHtmlService;

    @BeforeEach
    public void setup() {
        BookVo bookVo = new BookVo();
        bookVo.setTitle("E2E Test Book");
        bookVo.setAuthor(Collections.singletonList("E2E Author"));
        bookVo.setImage("http://example.com/e2e-image.jpg");

        Mockito.when(bookLoader.loadBook(anyString())).thenReturn(bookVo);
        Mockito.when(bookLoader.loadImage(anyString())).thenReturn(new byte[]{10, 20, 30});
    }

    @Test
    public void testAvailabilityOfV2IdEndpoint() {
        // Ensures that http://host/v2/1896753 is accessible
        given()
                .when().get("/v2/1896753")
                .then()
                .statusCode(200)
                .body("books[0].title", is("E2E Test Book"));
    }

    @Test
    public void testAvailabilityOfViewCoverEndpoint() {
        // Ensures that http://host/view/cover?cover=... is accessible
        given()
                .queryParam("cover", "https://img3.doubanio.com/view/subject/l/public/s2008433.jpg")
                .when().get("/view/cover")
                .then()
                .statusCode(200);
    }
}
