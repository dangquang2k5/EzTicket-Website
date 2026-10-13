package huce.fit.ezticket_backend.service.pdf;

public interface TicketPdfService {

    byte[] generatePdf(Long ticketId);

    byte[] getPdfBytes(Long ticketId);
}
