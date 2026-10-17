package com.yulim.flowcommerce.domain.product;

import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.global.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductAuthorizationTest {
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties properties;

    private static final String BODY = """
            {"name":"상품", "originalPrice":1000}
            """;

    @Test
    void readsRequireSellerRole() throws Exception {
        Product product = product();
        for (String path : List.of("/api/products", "/api/products/" + product.getId())) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String role : List.of("USER", "ADMIN")) {
                mvc.perform(get(path).header("Authorization", token(101L, role)))
                        .andExpect(status().isForbidden());
            }
            mvc.perform(get(path).header("Authorization", token(101L, "SELLER")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @Transactional
    void listSupportsPagingAndSortingAndDetailIncludesAllDisplayFields() throws Exception {
        Product first = products.save(Product.builder().sellerId(101L).name("첫 상품")
                .description("상세 설명").thumbnailUrl("https://example.com/product.jpg")
                .originalPrice(BigDecimal.valueOf(1500)).build());
        Product second = products.save(Product.builder().sellerId(101L).name("최근 상품")
                .originalPrice(BigDecimal.valueOf(2000)).build());
        mvc.perform(get("/api/products").param("page", "0").param("size", "1").param("sort", "id,desc")
                        .header("Authorization", token(101L, "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(second.getId()))
                .andExpect(jsonPath("$.data.number").value(0))
                .andExpect(jsonPath("$.data.totalElements").isNumber())
                .andExpect(jsonPath("$.data.totalPages").isNumber());
        mvc.perform(get("/api/products").param("page", "1").param("size", "1").param("sort", "id,desc")
                        .header("Authorization", token(101L, "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(first.getId()));
        mvc.perform(get("/api/products/{id}", first.getId()).header("Authorization", token(101L, "SELLER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(first.getId()))
                .andExpect(jsonPath("$.data.sellerId").value(101))
                .andExpect(jsonPath("$.data.name").value("첫 상품"))
                .andExpect(jsonPath("$.data.description").value("상세 설명"))
                .andExpect(jsonPath("$.data.originalPrice").value(1500))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://example.com/product.jpg"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").isString());
        first.deactivate(java.time.LocalDateTime.of(2030, 1, 1, 9, 0));
        mvc.perform(get("/api/products/{id}", first.getId()).header("Authorization", token(101L, "SELLER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_001"));
    }

    @Test
    void writesRequireSellerRole() throws Exception {
        for (String method : List.of("POST", "PATCH", "DELETE")) {
            String path = method.equals("POST") ? "/api/products" : "/api/products/1";
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_001"));
            for (String role : List.of("USER", "ADMIN")) {
                mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path)
                                .header("Authorization", token(101L, role))
                                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("COMMON_003"));
            }
        }
    }

    @Test
    void creationUsesJwtSubjectEvenWhenSellerIdIsSupplied() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", token(101L, "SELLER"))
                        .param("sellerId", "999")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.sellerId").value(101));
    }

    @Test
    void onlyOwnerCanUpdateAndDelete() throws Exception {
        Product product = product();
        for (String method : List.of("PATCH", "DELETE")) {
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/api/products/" + product.getId())
                            .header("Authorization", token(202L, "SELLER"))
                            .param("sellerId", "101")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PRODUCT_002"));
        }
        assertThat(products.findById(product.getId()).orElseThrow().getName()).isEqualTo("원래 상품");
        mvc.perform(patch("/api/products/{id}", product.getId()).header("Authorization", token(101L, "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("상품"));
        mvc.perform(delete("/api/products/{id}", product.getId()).header("Authorization", token(101L, "SELLER")))
                .andExpect(status().isOk());
        assertThat(products.findById(product.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    private Product product() {
        return products.save(Product.builder().sellerId(101L).name("원래 상품")
                .originalPrice(BigDecimal.valueOf(1000)).build());
    }

    private String token(Long userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .audience(List.of(properties.audience())).subject(userId.toString())
                .claim("email", "seller@example.com").claim("role", role)
                .issuedAt(now).expiresAt(now.plusSeconds(900)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
