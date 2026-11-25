package ru.funeralagency.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuneralRequestCsvTest {
    private final FuneralRequestCsv csv = new FuneralRequestCsv();
    private static final String HEADER = "deceasedFullName,client,ceremonyDate,ceremonyType,status,price\r\n";

    @Test
    void exportRoundTripsCommasQuotesNewlinesAndCyrillicWithPhysicalLineNumbers() {
        FuneralRequest first = new FuneralRequest(1L, new Client(1L, "Клиент", "+79991234567", null),
                "Иванов, \"Иван\"", LocalDate.of(2099, 1, 1), new CeremonyType(1L, "BURIAL", "Захоронение", true),
                RequestStatus.NEW, new BigDecimal("123.45"), LocalDateTime.of(2026, 1, 1, 12, 0), "Строка 1\nСтрока 2, \"да\"");
        first.setPriority(2);
        var parsed = csv.read(csv.write(List.of(first, first)));
        assertNull(parsed.fileError());
        assertEquals(2, parsed.rows().size());
        assertEquals(first.getDeceasedFullName(), parsed.rows().get(0).fields().get("deceasedFullName"));
        assertEquals(first.getComment(), parsed.rows().get(0).fields().get("comment"));
        assertEquals("123.45", parsed.rows().get(0).fields().get("price"));
        assertEquals("1", parsed.rows().get(0).fields().get("client"));
        assertEquals("1", parsed.rows().get(0).fields().get("ceremonyType"));
        assertEquals(2, parsed.rows().get(0).line());
        assertEquals(4, parsed.rows().get(1).line());
    }

    @Test
    void acceptsUtf8BomAndReportsInconsistentRowWithoutLosingLaterRow() {
        var parsed = read("\uFEFF" + HEADER + "Лишние,поля\r\nИмя,1,2099-01-01,1,NEW,10\r\n");
        assertNull(parsed.fileError());
        assertNotNull(parsed.rows().get(0).error());
        assertEquals(2, parsed.rows().get(0).line());
        assertNull(parsed.rows().get(1).error());
        assertEquals(3, parsed.rows().get(1).line());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "name,price\nИмя,10", "deceasedFullName,client,ceremonyDate,ceremonyType,status,price,client\n",
            "deceasedFullName,client,ceremonyDate,ceremonyType,status,price,typo\n", HEADER,
            HEADER + "Имя,1,2099-01-01,1,NEW,10\n\"незакрытая кавычка"})
    void malformedFileNeverReturnsPartiallyParsedRows(String text) {
        var parsed = read(text);
        assertNotNull(parsed.fileError());
        assertTrue(parsed.fileError().line() > 0);
        assertTrue(parsed.rows().isEmpty());
    }

    @Test
    void invalidUtf8IsAFileError() {
        assertNotNull(csv.read(new byte[]{(byte) 0xC3, (byte) 0x28}).fileError());
    }

    private FuneralRequestCsv.Parsed read(String text) {
        return csv.read(text.getBytes(StandardCharsets.UTF_8));
    }
}
