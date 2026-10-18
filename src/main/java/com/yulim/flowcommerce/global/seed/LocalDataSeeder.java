package com.yulim.flowcommerce.global.seed;

import com.yulim.flowcommerce.domain.deal.dto.DealTerms;
import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** 로컬 개발용 시드 데이터(판매자 1명, 상품 30개, 상품별 딜 2개로 총 60개)를 앱 시작 시 한 번 넣는다. */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataSeeder implements ApplicationRunner {

    static final String SEED_SELLER_EMAIL = "seed-seller@flowcommerce.local";
    private static final String SEED_SELLER_PASSWORD = "seed-password-1234";

    private static final int HOLD_DURATION_MINUTES = 10;

    private record ProductSeed(String name, long price) {
    }

    private static final List<ProductSeed> PRODUCT_SEEDS = List.of(
            new ProductSeed("무선 블루투스 이어폰", 89_000),
            new ProductSeed("노이즈캔슬링 헤드폰", 259_000),
            new ProductSeed("27인치 QHD 모니터", 329_000),
            new ProductSeed("기계식 키보드", 119_000),
            new ProductSeed("무선 게이밍 마우스", 79_000),
            new ProductSeed("휴대용 SSD 1TB", 139_000),
            new ProductSeed("스마트워치 밴드형", 199_000),
            new ProductSeed("보조배터리 20000mAh", 45_000),
            new ProductSeed("USB-C 멀티허브", 59_000),
            new ProductSeed("4K 웹캠", 129_000),
            new ProductSeed("에어프라이어 5.5L", 99_000),
            new ProductSeed("전기포트 1.7L", 39_000),
            new ProductSeed("로봇청소기", 449_000),
            new ProductSeed("핸드블렌더 세트", 69_000),
            new ProductSeed("드립 커피머신", 159_000),
            new ProductSeed("전기 그릴", 89_000),
            new ProductSeed("공기청정기 소형", 189_000),
            new ProductSeed("무선 청소기", 379_000),
            new ProductSeed("러닝화 경량형", 109_000),
            new ProductSeed("기능성 후드집업", 79_000),
            new ProductSeed("방수 등산 재킷", 189_000),
            new ProductSeed("캠핑 의자 폴딩형", 49_000),
            new ProductSeed("요가매트 10mm", 29_000),
            new ProductSeed("스테인리스 텀블러 500ml", 25_000),
            new ProductSeed("노트북 수납 백팩", 69_000),
            new ProductSeed("선크림 SPF50+ 대용량", 19_000),
            new ProductSeed("비타민C 90정", 29_000),
            new ProductSeed("단백질 쉐이크 2kg", 59_000),
            new ProductSeed("원두 블렌드 1kg", 32_000),
            new ProductSeed("프리미엄 견과 선물세트", 45_000)
    );

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final DealRepository dealRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(SEED_SELLER_EMAIL)) {
            log.info("시드 데이터가 이미 있어 건너뜁니다. (seller={})", SEED_SELLER_EMAIL);
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        User seller = createSeller();
        List<Product> products = productRepository.saveAll(createProducts(seller.getId()));
        List<Deal> deals = dealRepository.saveAll(createDeals(products, now));
        log.info("시드 데이터를 넣었습니다. seller={}, products={}, deals={}",
                SEED_SELLER_EMAIL, products.size(), deals.size());
    }

    private User createSeller() {
        User seller = User.register(SEED_SELLER_EMAIL, passwordEncoder.encode(SEED_SELLER_PASSWORD));
        seller.promoteToSeller();
        return userRepository.save(seller);
    }

    private List<Product> createProducts(Long sellerId) {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < PRODUCT_SEEDS.size(); i++) {
            ProductSeed seed = PRODUCT_SEEDS.get(i);
            products.add(Product.builder()
                    .sellerId(sellerId)
                    .name(seed.name())
                    .description(seed.name() + " 플래시 딜 테스트용 시드 상품입니다.")
                    .originalPrice(BigDecimal.valueOf(seed.price()))
                    .thumbnailUrl("https://picsum.photos/seed/flow-" + (i + 1) + "/400/300")
                    .build());
        }
        return products;
    }

    // 상품마다 딜 2개를 만든다. 3개 중 1개 상품은 첫 딜이 이미 판매 중이고, 나머지는 30분 간격으로 순차 오픈한다.
    // 두 번째 딜은 첫 딜이 끝나고 1시간 뒤에 열려 같은 상품의 기간이 겹치지 않는다.
    private List<Deal> createDeals(List<Product> products, LocalDateTime now) {
        List<Deal> deals = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            boolean onSale = i % 3 == 0;
            LocalDateTime firstOpenAt = onSale ? now.minusMinutes(30) : now.plusMinutes(30L * (i + 1));
            LocalDateTime firstCloseAt = onSale ? now.plusHours(2 + i % 4) : firstOpenAt.plusHours(3);
            LocalDateTime secondOpenAt = firstCloseAt.plusHours(1);

            deals.add(newDeal(product, i, firstOpenAt, firstCloseAt));
            deals.add(newDeal(product, i + 1, secondOpenAt, secondOpenAt.plusHours(3)));
        }
        return deals;
    }

    private Deal newDeal(Product product, int variant, LocalDateTime openAt, LocalDateTime closeAt) {
        int discountPercent = 10 + (variant % 5) * 10;
        BigDecimal dealPrice = product.getOriginalPrice()
                .multiply(BigDecimal.valueOf(100 - discountPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);

        DealTerms terms = new DealTerms(dealPrice, 20 + (variant % 5) * 10, 1 + variant % 3,
                openAt, closeAt, HOLD_DURATION_MINUTES);
        return Deal.create(product.getId(), terms);
    }
}
