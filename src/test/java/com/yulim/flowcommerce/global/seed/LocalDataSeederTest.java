package com.yulim.flowcommerce.global.seed;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LocalDataSeederTest {
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired DealRepository deals;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Clock clock;

    @Test
    void seedsThirtyProductsWithTwoNonOverlappingDealsEachOnlyOnce() {
        LocalDataSeeder seeder = new LocalDataSeeder(users, products, deals, passwordEncoder, clock);

        seeder.run(null);

        User seller = users.findByEmail(LocalDataSeeder.SEED_SELLER_EMAIL).orElseThrow();
        assertThat(seller.isSeller()).isTrue();
        List<Product> seededProducts = products.findAll().stream()
                .filter(product -> product.isOwnedBy(seller.getId()))
                .toList();
        Set<Long> productIds = seededProducts.stream().map(Product::getId).collect(Collectors.toSet());
        List<Deal> seededDeals = deals.findAll().stream()
                .filter(deal -> productIds.contains(deal.getProductId()))
                .toList();

        assertThat(seededProducts).hasSize(30).allMatch(Product::isActive);
        assertThat(seededDeals).hasSize(60);

        Map<Long, List<Deal>> dealsByProduct = seededDeals.stream()
                .collect(Collectors.groupingBy(Deal::getProductId));
        assertThat(dealsByProduct).hasSize(30);
        dealsByProduct.values().forEach(pair -> {
            assertThat(pair).hasSize(2);
            Deal a = pair.get(0);
            Deal b = pair.get(1);
            boolean overlaps = a.getOpenAt().isBefore(b.getCloseAt()) && a.getCloseAt().isAfter(b.getOpenAt());
            assertThat(overlaps).isFalse();
        });

        LocalDateTime now = LocalDateTime.now(clock);
        assertThat(seededDeals.stream().filter(deal -> deal.displayStatus(now) == Deal.DisplayStatus.ON_SALE)).hasSize(10);
        assertThat(seededDeals.stream().filter(deal -> deal.displayStatus(now) == Deal.DisplayStatus.UPCOMING)).hasSize(50);

        long productCount = products.count();
        long dealCount = deals.count();
        seeder.run(null);
        assertThat(products.count()).isEqualTo(productCount);
        assertThat(deals.count()).isEqualTo(dealCount);
    }
}
