package ru.funeralagency.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import ru.funeralagency.model.FuneralRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Формирует XLSX-файл со списком заявок. */
@Component
public class ExcelExporter {

    private static final String[] HEADERS = {
            "ID",
            "ID клиента",
            "ФИО умершего",
            "Дата церемонии",
            "Тип церемонии",
            "Статус",
            "Стоимость",
            "Создана",
            "Комментарий"
    };

    public byte[] export(List<FuneralRequest> requests) {
        if (requests == null) {
            throw new IllegalArgumentException("Список заявок не может быть null");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Заявки");
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook, "dd.mm.yyyy");
            CellStyle dateTimeStyle = createDateStyle(workbook, "dd.mm.yyyy hh:mm");

            createHeader(sheet, headerStyle);
            for (int index = 0; index < requests.size(); index++) {
                createRequestRow(
                        sheet.createRow(index + 1),
                        requests.get(index),
                        dateStyle,
                        dateTimeStyle
                );
            }
            for (int column = 0; column < HEADERS.length; column++) {
                sheet.autoSizeColumn(column);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Не удалось сформировать XLSX-файл", exception);
        }
    }

    private void createHeader(Sheet sheet, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            Cell cell = row.createCell(column);
            cell.setCellValue(HEADERS[column]);
            cell.setCellStyle(style);
        }
    }

    private void createRequestRow(
            Row row,
            FuneralRequest request,
            CellStyle dateStyle,
            CellStyle dateTimeStyle
    ) {
        setNumericCell(row, 0, request.getId());
        setNumericCell(row, 1, request.getClientId());
        setTextCell(row, 2, request.getDeceasedFullName());

        if (request.getCeremonyDate() != null) {
            Cell ceremonyDate = row.createCell(3);
            ceremonyDate.setCellValue(request.getCeremonyDate());
            ceremonyDate.setCellStyle(dateStyle);
        }
        setTextCell(row, 4, enumName(request.getCeremonyType()));
        setTextCell(row, 5, enumName(request.getStatus()));
        if (request.getPrice() != null) {
            row.createCell(6).setCellValue(request.getPrice().doubleValue());
        }
        if (request.getCreatedAt() != null) {
            Cell createdAt = row.createCell(7);
            createdAt.setCellValue(request.getCreatedAt());
            createdAt.setCellStyle(dateTimeStyle);
        }
        setTextCell(row, 8, request.getComment());
    }

    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    private CellStyle createDateStyle(XSSFWorkbook workbook, String pattern) {
        CreationHelper creationHelper = workbook.getCreationHelper();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(creationHelper.createDataFormat().getFormat(pattern));
        return style;
    }

    private void setNumericCell(Row row, int column, Long value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    private void setTextCell(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private String enumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }
}
