package com.yulim.flowcommerce.domain.deal;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.entity.UserRole;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import com.yulim.flowcommerce.support.FixedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FixedClockConfig.class)
@Transactional
class DealApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired DealRepository deals;
    User seller;
    Product product;

    @BeforeEach
    void setup() {
        seller = user(UserRole.SELLER);
        product = products.save(Product.builder().sellerId(seller.getId()).name("상품")
                .originalPrice(BigDecimal.valueOf(1000)).build());
    }

    @Test
    void sellerCanCreatePatchAndCancelAndProductIdCannotChange() throws Exception {
        mvc.perform(post("/api/deals").with(as(seller)).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.displayStatus").value("UPCOMING"))
                .andExpect(jsonPath("$.data.remainingStock").value(10));
        Deal deal = deals.findAll().stream().filter(d -> d.getProductId().equals(product.getId())).findFirst().orElseThrow();
        mvc.perform(patch("/api/deals/{id}", deal.getId()).with(as(seller)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalStock\":20,\"productId\":999999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalStock").value(20))
                .andExpect(jsonPath("$.data.productId").value(product.getId()));
        mvc.perform(delete("/api/deals/{id}", deal.getId()).with(as(seller)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));
        assertThat(deals.findById(deal.getId()).orElseThrow().getStatus()).isEqualTo(Deal.DealStatus.CLOSED);
        mvc.perform(delete("/api/deals/{id}", deal.getId()).with(as(seller)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEAL_007"));
    }

    @Test
    void anonymousAndUserCannotCallAnyWriteEndpoint() throws Exception {
        User user = user(UserRole.USER);
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                post("/api/deals"), patch("/api/deals/1"), delete("/api/deals/1")}) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isUnauthorized());
            mvc.perform(request.with(as(user)).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanManageAnotherSellersDealButAnotherSellerCannot() throws Exception {
        User other = user(UserRole.SELLER);
        User admin = user(UserRole.ADMIN);
        mvc.perform(post("/api/deals").with(as(other)).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEAL_002"));
        mvc.perform(post("/api/deals").with(as(admin)).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated());
        Deal deal = deals.findAll().stream().filter(d -> d.getProductId().equals(product.getId())).findFirst().orElseThrow();
        mvc.perform(patch("/api/deals/{id}", deal.getId()).with(as(other)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEAL_002"));
        mvc.perform(delete("/api/deals/{id}", deal.getId()).with(as(other)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEAL_002"));
        mvc.perform(patch("/api/deals/{id}", deal.getId()).with(as(admin)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/deals/{id}", deal.getId()).with(as(admin))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"totalStock\":0", "\"maxPurchasePerUser\":0", "\"holdDurationMinutes\":0", "\"holdDurationMinutes\":31"})
    void invalidBoundsReturnCommonInputErrorForPostAndPatch(String invalidField) throws Exception {
        String name = invalidField.split(":")[0];
        String invalidBody = body().replaceAll(name + ":\\d+", invalidField);
        mvc.perform(post("/api/deals").with(as(seller)).contentType(MediaType.APPLICATION_JSON).content(invalidBody))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_001"));
        mvc.perform(patch("/api/deals/1").with(as(seller)).contentType(MediaType.APPLICATION_JSON).content("{" + invalidField + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void soldOutDealLocksProductPriceAndDeletion() throws Exception {
        Deal deal = Deal.builder().productId(product.getId()).dealPrice(BigDecimal.valueOf(700))
                .totalStock(10).maxPurchasePerUser(1).holdDurationMinutes(5)
                .openAt(LocalDateTime.of(2030, 1, 1, 8, 0)).closeAt(LocalDateTime.of(2030, 1, 1, 10, 0)).build();
        ReflectionTestUtils.setField(deal, "status", Deal.DealStatus.SOLD_OUT);
        deals.saveAndFlush(deal);
        mvc.perform(patch("/api/products/{id}", product.getId()).with(as(seller)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"상품\",\"originalPrice\":1200}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_004"));
        mvc.perform(delete("/api/products/{id}", product.getId()).with(as(seller)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_003"));
    }

    private String body() {
        return """
                {"productId":%d,"dealPrice":700,"totalStock":10,"maxPurchasePerUser":2,
                 "openAt":"2030-01-01T10:00:00","closeAt":"2030-01-01T11:00:00","holdDurationMinutes":10}
                """.formatted(product.getId());
    }

    private User user(UserRole role) {
        User user = User.register(UUID.randomUUID() + "@example.com", "unused");
        ReflectionTestUtils.setField(user, "role", role);
        return users.save(user);
    }

    private RequestPostProcessor as(User user) {
        return jwt().jwt(token -> token.subject(user.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }
}
