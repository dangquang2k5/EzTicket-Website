package huce.fit.ezticket_backend.config;

import huce.fit.ezticket_backend.entity.Category;
import huce.fit.ezticket_backend.entity.Event;
import huce.fit.ezticket_backend.entity.TicketType;
import huce.fit.ezticket_backend.entity.User;
import huce.fit.ezticket_backend.entity.enums.EventStatus;
import huce.fit.ezticket_backend.entity.enums.UserRole;
import huce.fit.ezticket_backend.entity.enums.UserStatus;
import huce.fit.ezticket_backend.repository.CategoryRepository;
import huce.fit.ezticket_backend.repository.EventRepository;
import huce.fit.ezticket_backend.repository.TicketTypeRepository;
import huce.fit.ezticket_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-20: test DataSeeder bang Mockito, khong can DB/Redis that.
 */
@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private DataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DataSeeder(userRepository, categoryRepository, eventRepository,
                ticketTypeRepository, passwordEncoder,
                true, "admin@ezticket.local", "Admin12345", "EzTicket Admin");
    }

    private void stubEmptyDbForFirstRun() {
        when(userRepository.findByEmail("admin@ezticket.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Admin12345")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });
        // seedEvent tim category qua findAll().
        when(categoryRepository.findAll()).thenReturn(List.of(
                Category.builder().id(1L).name("Am nhac").slug("am-nhac").build(),
                Category.builder().id(2L).name("The thao").slug("the-thao").build(),
                Category.builder().id(3L).name("Hoi thao & Workshop").slug("hoi-thao-workshop").build()));
        when(eventRepository.existsBySlug(anyString())).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });
        when(ticketTypeRepository.existsByEventIdAndName(any(), anyString())).thenReturn(false);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void run_seedsAdminCategoriesEvents_whenDbEmpty() {
        stubEmptyDbForFirstRun();

        seeder.run();

        // AC-2: admin role=ADMIN, status=ACTIVE, mat khau BCrypt.
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("HASHED");
        verify(passwordEncoder).encode("Admin12345");

        // AC-3: >= 3 category.
        verify(categoryRepository, atLeast(3)).save(any(Category.class));

        // AC-4: >= 5 event OPEN, endTime > startTime.
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository, atLeast(5)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues()).hasSize(5);
        for (Event e : eventCaptor.getAllValues()) {
            assertThat(e.getStatus()).isEqualTo(EventStatus.OPEN);
            assertThat(e.getDescription()).hasSizeGreaterThanOrEqualTo(20);
            assertThat(e.getEndTime()).isAfter(e.getStartTime());
            assertThat(e.getCreatedBy()).isNotNull();
        }

        // AC-5: moi event du 2 hang ve, VIP > Standard, soldQuantity=0.
        ArgumentCaptor<TicketType> typeCaptor = ArgumentCaptor.forClass(TicketType.class);
        verify(ticketTypeRepository, atLeast(10)).save(typeCaptor.capture());
        assertThat(typeCaptor.getAllValues()).hasSize(10);
        for (int i = 0; i < 10; i += 2) {
            TicketType vip = typeCaptor.getAllValues().get(i);
            TicketType standard = typeCaptor.getAllValues().get(i + 1);
            assertThat(vip.getName()).isEqualTo("VIP");
            assertThat(standard.getName()).isEqualTo("Standard");
            assertThat(vip.getPrice()).isGreaterThan(standard.getPrice());
            assertThat(vip.getSoldQuantity()).isZero();
            assertThat(standard.getSoldQuantity()).isZero();
        }
    }

    @Test
    void run_isIdempotent_secondRunInsertsNothing() {
        stubEmptyDbForFirstRun();
        seeder.run();

        // Lan 2: moi thu da ton tai -> khong save them gi.
        when(userRepository.findByEmail("admin@ezticket.local"))
                .thenReturn(Optional.of(User.builder().id(1L).email("admin@ezticket.local")
                        .role(UserRole.ADMIN).status(UserStatus.ACTIVE).build()));
        when(categoryRepository.existsBySlug(anyString())).thenReturn(true);
        when(eventRepository.existsBySlug(anyString())).thenReturn(true);

        long userSavesBefore = countSaves(userRepository);
        seeder.run();

        // AC-6: chay 2 lan, so ban ghi khong doi -> lan 2 khong save them gi.
        verify(userRepository, times((int) userSavesBefore)).save(any(User.class));
        verify(categoryRepository, times(3)).save(any(Category.class));
        verify(eventRepository, times(5)).save(any(Event.class));
        verify(ticketTypeRepository, times(10)).save(any(TicketType.class));
        verify(passwordEncoder, times(1)).encode(anyString());
    }

    @Test
    void run_reusesExistingAdmin_withoutReEncodingPassword() {
        User existing = User.builder().id(7L).email("admin@ezticket.local")
                .role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        when(userRepository.findByEmail("admin@ezticket.local")).thenReturn(Optional.of(existing));
        when(categoryRepository.existsBySlug(anyString())).thenReturn(true);
        when(eventRepository.existsBySlug(anyString())).thenReturn(true);

        seeder.run();

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void run_continuesOtherSeeds_whenOneCategoryFails() {
        when(userRepository.findByEmail("admin@ezticket.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            // AC-7: 1 category loi khong chan cac category khac.
            if ("the-thao".equals(c.getSlug())) {
                throw new RuntimeException("DB down gia lap");
            }
            return c;
        });
        when(categoryRepository.findAll()).thenReturn(List.of(
                Category.builder().id(1L).name("Am nhac").slug("am-nhac").build(),
                Category.builder().id(3L).name("Hoi thao & Workshop").slug("hoi-thao-workshop").build()));
        when(eventRepository.existsBySlug(anyString())).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            if (!List.of("am-nhac", "hoi-thao-workshop").contains(e.getCategory().getSlug())) {
                throw new IllegalStateException("Khong tim thay category");
            }
            e.setId(100L);
            return e;
        });
        when(ticketTypeRepository.existsByEventIdAndName(any(), anyString())).thenReturn(false);
        when(ticketTypeRepository.save(any(TicketType.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.run();

        // Van seed duoc category khac va event thuoc 2 category con lai (3 event).
        verify(categoryRepository, atLeast(3)).save(any(Category.class));
        verify(eventRepository, atLeast(3)).save(any(Event.class));
    }

    @Test
    void run_skipped_whenDisabled() {
        DataSeeder disabled = new DataSeeder(userRepository, categoryRepository, eventRepository,
                ticketTypeRepository, passwordEncoder,
                false, "admin@ezticket.local", "Admin12345", "EzTicket Admin");

        disabled.run();

        // AC-7: app.seed.enabled=false -> khong cham DB.
        verify(userRepository, never()).findByEmail(anyString());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void run_logsErrorAndSeedsNothing_whenAdminConfigMissing() {
        DataSeeder noConfig = new DataSeeder(userRepository, categoryRepository, eventRepository,
                ticketTypeRepository, passwordEncoder,
                true, "", "", "EzTicket Admin");

        noConfig.run();

        // AC-7: khong tao duoc Admin -> log ERROR, khong seed tiep.
        verify(userRepository, never()).save(any(User.class));
        verify(categoryRepository, never()).save(any(Category.class));
        verify(eventRepository, never()).save(any(Event.class));
    }

    private long countSaves(UserRepository repo) {
        // Dem so lan save da goi de doi chieu truoc/sau (minh hoa idempotent).
        try {
            var invocations = org.mockito.Mockito.mockingDetails(repo).getInvocations();
            return invocations.stream().filter(i -> i.getMethod().getName().equals("save")).count();
        } catch (Exception e) {
            return -1;
        }
    }
}
