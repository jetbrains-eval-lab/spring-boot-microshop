package shop.api.composite.product;

public record RecommendationSummary(
        int recommendationId,
        String author,
        int rate,
        String content
) {
}
