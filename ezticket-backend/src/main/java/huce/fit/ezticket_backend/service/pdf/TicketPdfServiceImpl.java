package huce.fit.ezticket_backend.service.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import huce.fit.ezticket_backend.entity.Ticket;
import huce.fit.ezticket_backend.repository.TicketRepository;
import huce.fit.ezticket_backend.service.qr.QrImageService;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;

/**
 * Kết xuất PDF vé khổ A4 bằng OpenPDF (BR-09, BR-10, C-22d).
 * Bố cục: banner, tên sự kiện, thời gian, địa điểm, holderName, hạng vé, mã vé, mã QR, điều khoản.
 * Banner lỗi hoặc không có thì bỏ qua, không làm hỏng PDF.
 */
@Slf4j
@Service
public class TicketPdfServiceImpl implements TicketPdfService {

    private static final DateTimeFormatter VN_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final TicketRepository ticketRepository;
    private final QrImageService qrImageService;
    private final String storageDir;

    public TicketPdfServiceImpl(TicketRepository ticketRepository,
                                QrImageService qrImageService,
                                @Value("${ticket.pdf.storage-dir:./storage/tickets}") String storageDir) {
        this.ticketRepository = ticketRepository;
        this.qrImageService = qrImageService;
        this.storageDir = storageDir;
    }

    @Override
    @Transactional
    public byte[] generatePdf(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Khong tim thay ve " + ticketId));
        byte[] pdf = renderPdf(ticket);
        try {
            Path dir = Paths.get(storageDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(ticket.getTicketCode() + ".pdf");
            Files.write(file, pdf);
            ticket.setPdfUrl(file.toAbsolutePath().toString());
            ticketRepository.save(ticket);
        } catch (Exception e) {
            log.error("Khong ghi duoc file PDF cho ve {}", ticketId, e);
            throw new IllegalStateException("Khong luu duoc file PDF ve", e);
        }
        return pdf;
    }

    @Override
    @Transactional
    public byte[] getPdfBytes(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException("Khong tim thay ve " + ticketId));
        if (ticket.getPdfUrl() != null) {
            try {
                Path file = Paths.get(ticket.getPdfUrl());
                if (Files.isRegularFile(file)) {
                    return Files.readAllBytes(file);
                }
            } catch (Exception e) {
                log.warn("Khong doc duoc PDF da luu cua ve {}, sinh lai", ticketId);
            }
        }
        return generatePdf(ticketId);
    }

    private byte[] renderPdf(Ticket ticket) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter.getInstance(doc, out);
            doc.open();
            try {
                Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
                Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
                Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
                Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

                addBanner(doc, ticket);

                Paragraph brand = new Paragraph("EzTicket - Ve dien tu", titleFont);
                brand.setAlignment(Element.ALIGN_CENTER);
                doc.add(brand);
                doc.add(new Paragraph(" "));

                doc.add(new Paragraph("Su kien: " + nz(ticket.getEvent().getTitle()), hFont));
                doc.add(new Paragraph("Thoi gian: " + formatTime(ticket), bodyFont));
                doc.add(new Paragraph("Dia diem: " + nz(ticket.getEvent().getVenue())
                        + (ticket.getEvent().getCity() != null ? ", " + ticket.getEvent().getCity() : ""), bodyFont));
                doc.add(new Paragraph("Nguoi giu ve: " + nz(ticket.getHolderName()), bodyFont));
                doc.add(new Paragraph("Hang ve: " + nz(ticket.getTicketType().getName()), bodyFont));
                doc.add(new Paragraph("Ma ve: " + nz(ticket.getTicketCode()), bodyFont));
                doc.add(new Paragraph(" "));

                byte[] qrPng = qrImageService.generateQrPng(
                        ticket.getQrPayload() + "." + ticket.getQrSignature());
                Image qr = Image.getInstance(qrPng);
                qr.scaleToFit(220, 220);
                qr.setAlignment(Element.ALIGN_CENTER);
                doc.add(qr);
                doc.add(new Paragraph(" "));

                Paragraph termsTitle = new Paragraph("Dieu khoan su dung:", hFont);
                doc.add(termsTitle);
                doc.add(new Paragraph(
                        "1. Moi ve chi su dung mot lan cho mot nguoi. "
                                + "2. Vui long den truoc gio bat dau 30 phut va xuat trinh ma QR tai cong. "
                                + "3. Ve khong co gia tri quy doi thanh tien mat. "
                                + "4. Ban to chuc co quyen tu choi ve co dau hieu lam gia.",
                        smallFont));
            } finally {
                if (doc.isOpen()) {
                    doc.close();
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Khong ket xuat duoc PDF ve", e);
        }
    }

    private void addBanner(Document doc, Ticket ticket) {
        String bannerUrl = ticket.getEvent().getBannerUrl();
        if (bannerUrl == null || bannerUrl.isBlank()) {
            return;
        }
        try {
            Image banner;
            if (bannerUrl.startsWith("http://") || bannerUrl.startsWith("https://")) {
                banner = Image.getInstance(new URL(bannerUrl));
            } else {
                banner = Image.getInstance(bannerUrl);
            }
            banner.scaleToFit(PageSize.A4.getWidth() - 96, 180);
            banner.setAlignment(Element.ALIGN_CENTER);
            doc.add(banner);
            doc.add(new Paragraph(" "));
        } catch (Exception e) {
            // Banner loi thi bo qua, khong lam hong PDF.
            log.warn("Bo qua banner loi cua su kien {}", ticket.getEvent().getId());
        }
    }

    private String formatTime(Ticket ticket) {
        String start = ticket.getEvent().getStartTime() != null
                ? ticket.getEvent().getStartTime().format(VN_TIME) : "?";
        String end = ticket.getEvent().getEndTime() != null
                ? " - " + ticket.getEvent().getEndTime().format(VN_TIME) : "";
        return start + end;
    }

    private static String nz(String value) {
        return value != null ? value : "";
    }
}
