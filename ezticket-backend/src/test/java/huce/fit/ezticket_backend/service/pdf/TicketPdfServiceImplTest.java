package huce.fit.ezticket_backend.service.pdf;

import huce.fit.ezticket_backend.entity.Event;
import huce.fit.ezticket_backend.entity.Ticket;
import huce.fit.ezticket_backend.entity.TicketType;
import huce.fit.ezticket_backend.entity.User;
import huce.fit.ezticket_backend.entity.enums.TicketStatus;
import huce.fit.ezticket_backend.repository.TicketRepository;
import huce.fit.ezticket_backend.service.qr.QrImageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketPdfServiceImplTest {

    @Mock
    private TicketRepository ticketRepository;

    @TempDir
    private Path tempDir;

    private Ticket ticket(Long id, String bannerUrl) {
        Event event = Event.builder().id(101L).title("Dem nhac Hoi An").venue("Nha hat Lon")
                .city("Ha Noi").bannerUrl(bannerUrl)
                .startTime(LocalDateTime.of(2026, 11, 20, 19, 30))
                .endTime(LocalDateTime.of(2026, 11, 20, 22, 0)).build();
        TicketType type = TicketType.builder().id(11L).event(event).name("VIP").build();
        User user = User.builder().id(7L).fullName("Nguyen Van A").build();
        return Ticket.builder().id(id).ticketCode(UUID.randomUUID().toString())
                .event(event).ticketType(type).user(user).holderName("Nguyen Van A")
                .qrPayload("code.101.1729000000").qrSignature("sig-hop-le")
                .status(TicketStatus.VALID).build();
    }

    @Test
    void pdfSinhRaHopLeBatDauBangMagicBytes() {
        Ticket ticket = ticket(null, null);
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        TicketPdfServiceImpl pdfService =
                new TicketPdfServiceImpl(ticketRepository, new QrImageService(), tempDir.toString());

        byte[] pdf = pdfService.generatePdf(1L);

        String header = new String(pdf, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header);
        assertNotNull(ticket.getPdfUrl());
        assertTrue(tempDir.resolve(ticket.getTicketCode() + ".pdf").toFile().isFile());
    }

    @Test
    void bannerLoiKhongLamHongPdf() {
        Ticket ticket = ticket(null, "file:///khong-ton-tai/banner.png");
        when(ticketRepository.findById(2L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        TicketPdfServiceImpl pdfService =
                new TicketPdfServiceImpl(ticketRepository, new QrImageService(), tempDir.toString());

        byte[] pdf = pdfService.generatePdf(2L);

        String header = new String(pdf, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header);
    }
}
