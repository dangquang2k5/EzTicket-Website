package huce.fit.ezticket_backend.config;

import huce.fit.ezticket_backend.entity.Category;
import huce.fit.ezticket_backend.entity.Event;
import huce.fit.ezticket_backend.entity.TicketType;
import huce.fit.ezticket_backend.entity.User;
import huce.fit.ezticket_backend.entity.enums.EventStatus;
import huce.fit.ezticket_backend.entity.enums.TicketTypeStatus;
import huce.fit.ezticket_backend.entity.enums.UserRole;
import huce.fit.ezticket_backend.entity.enums.UserStatus;
import huce.fit.ezticket_backend.repository.CategoryRepository;
import huce.fit.ezticket_backend.repository.EventRepository;
import huce.fit.ezticket_backend.repository.TicketTypeRepository;
import huce.fit.ezticket_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SCRUM-20: Nap du lieu mau khi ung dung khoi dong.
 *
 * <p>Pham vi: tai khoan Admin, danh muc, su kien + hang ve.
 * Idempotent: chay lai khong chen trung (kiem tra existsBy... truoc khi save).
 * Loi seed khong lam sap app (log roi tiep tuc), tru Admin thi log ERROR.
 *
 * <p>BR lien quan: BR-19 (su kien OPEN co hang ve, endTime &gt; startTime),
 * BR-20 (ten hang ve duy nhat trong event, totalQuantity &gt; 0),
 * BR-21 (ten danh muc duy nhat).
 * Phuong an C-01: status su kien dung bo DD (OPEN...), khong dung ON_SALE.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final PasswordEncoder passwordEncoder;

    private final boolean enabled;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public DataSeeder(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            EventRepository eventRepository,
            TicketTypeRepository ticketTypeRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.enabled:true}") boolean enabled,
            @Value("${app.seed.admin-email:}") String adminEmail,
            @Value("${app.seed.admin-password:}") String adminPassword,
            @Value("${app.seed.admin-name:EzTicket Admin}") String adminName) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        this.adminName = adminName == null || adminName.isBlank() ? "EzTicket Admin" : adminName.trim();
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("DataSeeder: bo qua nap du lieu mau (app.seed.enabled=false)");
            return;
        }
        try {
            User admin = seedAdmin();
            if (admin == null) {
                log.error("DataSeeder: KHONG tao duoc Admin seed (email={}), bo qua seed category/event",
                        adminEmail);
                return;
            }
            seedCategories();
            seedEvents(admin);
            log.info("DataSeeder: nap du lieu mau hoan tat");
        } catch (Exception e) {
            // Khong de seed lam sap app (AC-7).
            log.error("DataSeeder: loi khong mong doi khi nap du lieu mau: {}", e.getMessage(), e);
        }
    }

    User seedAdmin() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.error("DataSeeder: thieu cau hinh app.seed.admin-email hoac app.seed.admin-password, "
                    + "khong the tao Admin seed");
            return null;
        }
        try {
            return userRepository.findByEmail(adminEmail)
                    .orElseGet(() -> userRepository.save(User.builder()
                            .fullName(adminName)
                            .email(adminEmail)
                            .passwordHash(passwordEncoder.encode(adminPassword))
                            .role(UserRole.ADMIN)
                            .status(UserStatus.ACTIVE)
                            .build()));
        } catch (Exception e) {
            log.error("DataSeeder: loi khi tao Admin seed: {}", e.getMessage(), e);
            return null;
        }
    }

    void seedCategories() {
        List<Category> defaults = List.of(
                Category.builder().name("Am nhac").slug("am-nhac")
                        .description("Cac su kien am nhac, liveshow, hoa nhac").build(),
                Category.builder().name("The thao").slug("the-thao")
                        .description("Giai dau, tran bong da, su kien the thao").build(),
                Category.builder().name("Hoi thao & Workshop").slug("hoi-thao-workshop")
                        .description("Hoi thao, workshop, khoa hoc ky nang").build());
        for (Category category : defaults) {
            try {
                if (categoryRepository.existsBySlug(category.getSlug())) {
                    log.info("DataSeeder: category '{}' da ton tai, bo qua", category.getSlug());
                    continue;
                }
                categoryRepository.save(category);
                log.info("DataSeeder: da tao category '{}'", category.getSlug());
            } catch (Exception e) {
                // Loi 1 category khong chan cac category khac (AC-7).
                log.error("DataSeeder: loi khi tao category '{}': {}",
                        category.getSlug(), e.getMessage(), e);
            }
        }
    }

    void seedEvents(User admin) {
        LocalDateTime now = LocalDateTime.now();
        List<EventSeed> defaults = List.of(
                new EventSeed("am-nhac", "Le hoi Am nhac Mua He",
                        "le-hoi-am-nhac-mua-he",
                        "Le hoi am nhac mua he voi su tham gia cua nhieu nghe si noi tieng trong va ngoai nuoc.",
                        "https://cdn.ezticket.vn/banners/le-hoi-mua-he.jpg",
                        "San van dong My Dinh", "Ha Noi", now.plusDays(30), 8,
                        new BigDecimal("1500000"), new BigDecimal("500000"), 500, 2000),
                new EventSeed("the-thao", "Giai Bong da Sinh vien Toan quoc",
                        "giai-bong-da-sinh-vien-toan-quoc",
                        "Giai bong da sinh vien toan quoc: vong chung ket giua 8 doi manh nhat ca nuoc.",
                        "https://cdn.ezticket.vn/banners/bong-da-sinh-vien.jpg",
                        "San Thong Nhat", "TP. Ho Chi Minh", now.plusDays(45), 4,
                        new BigDecimal("300000"), new BigDecimal("100000"), 1000, 5000),
                new EventSeed("hoi-thao-workshop", "Hoi thao Cong nghe & Doi moi Sang tao",
                        "hoi-thao-cong-nghe-doi-moi-sang-tao",
                        "Hoi thao cong nghe quy tu cac dien gia dau nganh ve AI, chuyen doi so va khoi nghiep.",
                        "https://cdn.ezticket.vn/banners/cong-nghe-sang-tao.jpg",
                        "Trung tam Hoi nghi Quoc gia", "Ha Noi", now.plusDays(60), 9,
                        new BigDecimal("800000"), new BigDecimal("200000"), 300, 1000),
                new EventSeed("am-nhac", "Dem nhac Acoustic Ben Ho",
                        "dem-nhac-acoustic-ben-ho",
                        "Dem nhac acoustic lang man ben ho voi guitar, piano va giong ca tre day cam xuc.",
                        "https://cdn.ezticket.vn/banners/acoustic-ben-ho.jpg",
                        "Nha hat Lon Ha Noi", "Ha Noi", now.plusDays(35), 3,
                        new BigDecimal("600000"), new BigDecimal("250000"), 200, 800),
                new EventSeed("hoi-thao-workshop", "Workshop Khoi nghiep & Marketing So",
                        "workshop-khoi-nghiep-marketing-so",
                        "Workshop thuc chien ve khoi nghiep, marketing so va ban hang da kenh cho nguoi moi.",
                        "https://cdn.ezticket.vn/banners/khoi-nghiep-marketing.jpg",
                        "SECC Saigon Exhibition", "TP. Ho Chi Minh", now.plusDays(50), 6,
                        new BigDecimal("500000"), new BigDecimal("150000"), 250, 700));
        for (EventSeed seed : defaults) {
            try {
                seedEvent(admin, seed);
            } catch (Exception e) {
                // Loi 1 event khong chan cac event khac (AC-7).
                log.error("DataSeeder: loi khi tao event '{}': {}", seed.slug, e.getMessage(), e);
            }
        }
    }

    private void seedEvent(User admin, EventSeed seed) {
        if (eventRepository.existsBySlug(seed.slug)) {
            log.info("DataSeeder: event '{}' da ton tai, bo qua", seed.slug);
            return;
        }
        Category category = categoryRepository.findAll().stream()
                .filter(c -> seed.categorySlug.equals(c.getSlug()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Khong tim thay category '" + seed.categorySlug + "' de tao event '" + seed.slug + "'"));
        LocalDateTime endTime = seed.startTime.plusHours(seed.durationHours);
        Event event = eventRepository.save(Event.builder()
                .category(category)
                .title(seed.title)
                .slug(seed.slug)
                .description(seed.description)
                .bannerUrl(seed.bannerUrl)
                .venue(seed.venue)
                .city(seed.city)
                .startTime(seed.startTime)
                .endTime(endTime)
                .status(EventStatus.OPEN)
                .createdBy(admin)
                .build());
        // Moi event du 2 hang ve VIP va Standard (AC-5). VIP > Standard (BR-20: price >= 0).
        seedTicketType(event, "VIP", seed.vipPrice, seed.vipQuantity, 2);
        seedTicketType(event, "Standard", seed.standardPrice, seed.standardQuantity, 4);
        log.info("DataSeeder: da tao event '{}' kem 2 hang ve", seed.slug);
    }

    private void seedTicketType(Event event, String name, BigDecimal price, int totalQuantity, int maxPerOrder) {
        if (ticketTypeRepository.existsByEventIdAndName(event.getId(), name)) {
            log.info("DataSeeder: ticket-type '{}' cua event '{}' da ton tai, bo qua", name, event.getSlug());
            return;
        }
        ticketTypeRepository.save(TicketType.builder()
                .event(event)
                .name(name)
                .price(price)
                .totalQuantity(totalQuantity)
                .soldQuantity(0)
                .maxPerOrder(maxPerOrder)
                .status(TicketTypeStatus.OPEN)
                .build());
    }

    private record EventSeed(
            String categorySlug, String title, String slug, String description,
            String bannerUrl, String venue, String city, LocalDateTime startTime, int durationHours,
            BigDecimal vipPrice, BigDecimal standardPrice, int vipQuantity, int standardQuantity) {
    }
}
