package com.turkcell.billing_service.service;

import com.turkcell.billing_service.domain.entity.Invoice;
import com.turkcell.billing_service.domain.entity.InvoiceLine;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Slf4j
@Service
public class InvoicePdfService {

    public byte[] generate(Invoice invoice, List<InvoiceLine> lines) {
        String html = buildHtml(invoice, lines);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF for invoice {}", invoice.getId(), e);
            throw new RuntimeException("PDF generation failed for invoice: " + invoice.getId(), e);
        }
    }

    private String buildHtml(Invoice invoice, List<InvoiceLine> lines) {
        StringBuilder rows = new StringBuilder();
        for (InvoiceLine line : lines) {
            rows.append("<tr><td>").append(escape(line.getDescription())).append("</td>")
                .append("<td>").append(line.getQuantity()).append("</td>")
                .append("<td>").append(line.getUnitPrice()).append(" TRY</td>")
                .append("<td>").append(line.getLineTotal()).append(" TRY</td></tr>");
        }

        return """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8"/><style>
            body { font-family: Arial, sans-serif; margin: 40px; }
            h1 { color: #003366; }
            table { width: 100%%; border-collapse: collapse; margin-top: 20px; }
            th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
            th { background: #f0f0f0; }
            .totals { margin-top: 20px; text-align: right; }
            </style></head><body>
            <h1>TelcoX Fatura</h1>
            <p><strong>Fatura No:</strong> %s</p>
            <p><strong>Musteri:</strong> %s</p>
            <p><strong>Donem:</strong> %s - %s</p>
            <p><strong>Vade:</strong> %s</p>
            <table>
            <thead><tr><th>Aciklama</th><th>Miktar</th><th>Birim Fiyat</th><th>Tutar</th></tr></thead>
            <tbody>%s</tbody>
            </table>
            <div class="totals">
            <p>Ara Toplam: %s TRY</p>
            <p>KDV: %s TRY</p>
            <p><strong>Genel Toplam: %s TRY</strong></p>
            </div>
            </body></html>
            """.formatted(
                invoice.getId(),
                invoice.getCustomerId(),
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getDueDate(),
                rows,
                invoice.getSubTotal(),
                invoice.getTax(),
                invoice.getGrandTotal()
        );
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;");
    }
}
