package ru.funeralagency.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Component;
import ru.funeralagency.dto.CsvRowErrorDto;
import ru.funeralagency.model.FuneralRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class FuneralRequestCsv {
    private static final String[] COLUMNS = {"requestCode", "deceasedFullName", "client", "ceremonyDate",
            "ceremonyType", "status", "price", "urgent", "priority", "archived", "createdAt", "comment"};
    private static final Set<String> REQUIRED = Set.of("deceasedFullName", "client", "ceremonyDate", "ceremonyType", "status", "price");

    public record Row(long line, Map<String, String> fields, String error) {
    }

    public record Parsed(List<Row> rows, CsvRowErrorDto fileError) {
    }

    public byte[] write(List<FuneralRequest> requests) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (CSVPrinter printer = new CSVPrinter(new OutputStreamWriter(output, StandardCharsets.UTF_8),
                CSVFormat.RFC4180.builder().setHeader(COLUMNS).get())) {
            for (FuneralRequest request : requests) {
                printer.printRecord(request.getRequestCode(), request.getDeceasedFullName(), request.getClient().getId(),
                        request.getCeremonyDate(), request.getCeremonyType().getId(), request.getStatus(),
                        request.getPrice().toPlainString(), request.isUrgent(), request.getPriority(),
                        request.isArchived(), request.getCreatedAt(), request.getComment());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return output.toByteArray();
    }

    public Parsed read(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            return invalidFile(1, "CSV должен быть в кодировке UTF-8");
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        long nextLine = 1;
        var format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).setAllowMissingColumnNames(false).get();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            List<String> headers = parser.getHeaderNames();
            if (!headers.containsAll(REQUIRED)) {
                return invalidFile(1, "Обязательные CSV колонки: deceasedFullName, client, ceremonyDate, ceremonyType, status, price");
            }
            for (String header : headers) {
                if (!List.of(COLUMNS).contains(header)) {
                    return invalidFile(1, "Неизвестная CSV колонка: " + header);
                }
            }
            List<Row> rows = new ArrayList<>();
            var iterator = parser.iterator();
            nextLine = parser.getCurrentLineNumber() + 1;
            while (iterator.hasNext()) {
                var record = iterator.next();
                if (!record.isConsistent()) {
                    rows.add(new Row(nextLine, Map.of(), "Число полей не соответствует CSV заголовку"));
                } else {
                    Map<String, String> fields = new HashMap<>();
                    for (String header : headers) {
                        fields.put(header, record.get(header));
                    }
                    rows.add(new Row(nextLine, fields, null));
                }
                nextLine = parser.getCurrentLineNumber() + 1;
            }
            if (rows.isEmpty()) {
                return invalidFile(nextLine, "CSV не содержит строк заявок");
            }
            return new Parsed(rows, null);
        } catch (IOException | UncheckedIOException | IllegalArgumentException exception) {
            // При повреждённом синтаксисе весь файл отклоняется до любых записей в apply.
            return invalidFile(nextLine, "Некорректный CSV: проверьте заголовок, кавычки и разделители");
        }
    }

    private Parsed invalidFile(long line, String message) {
        return new Parsed(List.of(), new CsvRowErrorDto(line, message));
    }
}
