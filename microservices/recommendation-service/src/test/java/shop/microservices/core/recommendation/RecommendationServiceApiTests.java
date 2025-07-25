package shop.microservices.core.recommendation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import shop.api.core.recommendation.Recommendation;
import shop.api.event.Event;
import shop.api.exceptions.InvalidInputException;
import shop.microservices.core.recommendation.persistence.RecommendationRepository;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static shop.api.event.Event.Type.CREATE;
import static shop.api.event.Event.Type.DELETE;

@SuppressWarnings("DataFlowIssue")
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"eureka.client.enabled=false"})
class RecommendationServiceApiTests extends MongoDbTestBase {

    private static final String RECOMMENDATION_CONTENT = "Lorem ipsum dolor sit amet, consetetur sadipscingw";

    @Autowired
    private WebTestClient client;

    @Autowired
    private RecommendationRepository repository;

    @Autowired
    @Qualifier("messageProcessor")
    private Consumer<Event<Integer, Recommendation>> messageProcessor;

    @BeforeEach
    void setupDb() {
        repository.deleteAll().block();
    }

    @Test
    void getRecommendationsByProductId() {
        int productId = 1;

        sendCreateRecommendationEvent(productId, 1);
        sendCreateRecommendationEvent(productId, 2);
        sendCreateRecommendationEvent(productId, 3);

        assertEquals(3, (long) repository.findByProductId(productId).count().block());

        getAndVerifyRecommendationsByProductId(productId, OK)
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[2].productId").isEqualTo(productId)
                .jsonPath("$[2].recommendationId").isEqualTo(3);
    }

    @Test
    void duplicateError() {
        int productId = 1;
        int recommendationId = 1;

        sendCreateRecommendationEvent(productId, recommendationId);

        assertEquals(1, (long) repository.count().block());

        InvalidInputException thrown = assertThrows(
                InvalidInputException.class,
                () -> sendCreateRecommendationEvent(productId, recommendationId),
                "Expected a InvalidInputException here!");
        assertEquals("Duplicate key, Product Id: 1, Recommendation Id:1", thrown.getMessage());

        assertEquals(1, (long) repository.count().block());
    }

    @Test
    void deleteRecommendations() {
        int productId = 1;
        int recommendationId = 1;

        sendCreateRecommendationEvent(productId, recommendationId);
        assertEquals(1, (long) repository.findByProductId(productId).count().block());

        sendDeleteRecommendationEvent(productId);
        assertEquals(0, (long) repository.findByProductId(productId).count().block());

        sendDeleteRecommendationEvent(productId);
    }

    @Test
    void getRecommendationsMissingParameter() {
        getAndVerifyRecommendationsByProductId("", BAD_REQUEST);
    }

    @Test
    void getRecommendationsInvalidParameter() {
        getAndVerifyRecommendationsByProductId("?productId=no-integer", BAD_REQUEST);
    }

    @Test
    void getRecommendationsInvalidParameterNegativeValue() {
        int productIdInvalid = -1;

        getAndVerifyRecommendationsByProductId("?productId=" + productIdInvalid, UNPROCESSABLE_ENTITY)
                .jsonPath("$.path").isEqualTo("/recommendation")
                .jsonPath("$.message").isEqualTo("Invalid productId: " + productIdInvalid);
    }

    @SuppressWarnings("SameParameterValue")
    private WebTestClient.BodyContentSpec getAndVerifyRecommendationsByProductId(int productId, HttpStatus expectedStatus) {
        return getAndVerifyRecommendationsByProductId("?productId=" + productId, expectedStatus);
    }

    private WebTestClient.BodyContentSpec getAndVerifyRecommendationsByProductId(String productIdQuery, HttpStatus expectedStatus) {
        return client.get()
                .uri("/recommendation" + productIdQuery)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectHeader().contentType(APPLICATION_JSON)
                .expectBody();
    }

    private void sendCreateRecommendationEvent(int productId, int recommendationId) {
        Recommendation recommendation = new Recommendation(productId, recommendationId, "Author " + recommendationId, recommendationId, RECOMMENDATION_CONTENT + recommendationId, "SA");
        Event<Integer, Recommendation> event = new Event<>(CREATE, productId, recommendation);
        messageProcessor.accept(event);
    }

    private void sendDeleteRecommendationEvent(int productId) {
        Event<Integer, Recommendation> event = new Event<>(DELETE, productId, null);
        messageProcessor.accept(event);
    }
}
