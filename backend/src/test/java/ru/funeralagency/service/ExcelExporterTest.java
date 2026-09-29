package ru.funeralagency.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelExporterTest {

    @Test
    void exportsHeadersAndRequestData() throws Exception {
        FuneralRequest request = new FuneralRequest(
                7L,
                3L,
                "Иванов Иван Иванович",
                LocalDate.of(2027, 1, 15),
                CeremonyType.BURIAL,
                RequestStatus.NEW,
                new BigDecimal("25000.50"),
                LocalDateTime.of(2026, 9, 22, 12, 0),
                "Без дополнительных пожеланий"
        );

        byte[] content = new ExcelExporter().export(List.of(request));

        assertTrue(content.length > 0);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertEquals("ID", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals(7, workbook.getSheetAt(0).getRow(1).getCell(0).getNumericCellValue());
            assertEquals(
                    "Иванов Иван Иванович",
                    workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue()
            );
            assertEquals(25000.50, workbook.getSheetAt(0).getRow(1).getCell(6).getNumericCellValue());
        }
    }
}
