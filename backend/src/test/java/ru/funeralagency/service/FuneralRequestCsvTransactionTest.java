package ru.funeralagency.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ru.funeralagency.dto.CsvImportMode;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.repository.CeremonyTypeRepository;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.repository.TagRepository;

import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FuneralRequestCsvTransactionTest {
    private AnnotationConfigApplicationContext context;
    private PlatformTransactionManager transactions;
    private FuneralRequestRepository repository;
    private FuneralRequestCsvService csv;
    private final Map<TransactionStatus, TransactionDefinition> definitions = new IdentityHashMap<>();
    private static final byte[] FILE = ("deceasedFullName,client,ceremonyDate,ceremonyType,status,price\n"
            + "Первая,1,2099-01-01,1,NEW,100\nВторая,1,2099-01-01,1,NEW,200\n").getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        transactions = mock(PlatformTransactionManager.class);
        repository = mock(FuneralRequestRepository.class);
        ClientRepository clients = mock(ClientRepository.class);
        CeremonyTypeRepository types = mock(CeremonyTypeRepository.class);
        when(clients.findById(1L)).thenReturn(Optional.of(new Client(1L, "Клиент", "+79991234567", null)));
        when(types.findById(1L)).thenReturn(Optional.of(new CeremonyType(1L, "BURIAL", "Захоронение", true)));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.getTransaction(any())).thenAnswer(invocation -> {
            var status = new SimpleTransactionStatus(true);
            definitions.put(status, invocation.getArgument(0));
            return status;
        });
        context = new AnnotationConfigApplicationContext();
        context.registerBean(PlatformTransactionManager.class, () -> transactions);
        context.registerBean(FuneralRequestRepository.class, () -> repository);
        context.registerBean(ClientRepository.class, () -> clients);
        context.registerBean(CeremonyTypeRepository.class, () -> types);
        context.registerBean(TagRepository.class, () -> mock(TagRepository.class));
        context.register(TransactionConfig.class, TagService.class, FuneralRequestMapper.class, FuneralRequestService.class,
                FuneralRequestCsv.class, FuneralRequestCsvRowService.class, FuneralRequestCsvService.class);
        context.refresh();
        csv = context.getBean(FuneralRequestCsvService.class);
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void previewUsesReadOnlyRowTransactionsAndDoesNotSave() {
        assertEquals(2, csv.preview(FILE, CsvImportMode.CREATE_ONLY).validCount());
        long rowTransactions = definitions.values().stream()
                .filter(definition -> definition.getName().endsWith("FuneralRequestCsvRowService.preview"))
                .peek(definition -> assertTrue(definition.isReadOnly())).count();
        assertEquals(2, rowTransactions);
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).flush();
    }

    @Test
    void rowCommitFailureDoesNotCountAsSuccessAndLaterRowContinues() {
        boolean[] failed = {false};
        doAnswer(invocation -> {
            TransactionDefinition definition = definitions.get(invocation.getArgument(0));
            if (definition.getName().endsWith("FuneralRequestCsvRowService.apply") && !failed[0]) {
                failed[0] = true;
                throw new TransactionSystemException("internal commit failure");
            }
            return null;
        }).when(transactions).commit(any());
        var result = csv.apply(FILE, CsvImportMode.CREATE_ONLY);
        assertEquals(1, result.successful().size());
        assertEquals(3, result.successful().get(0).line());
        assertEquals(1, result.errors().size());
        assertEquals(2, result.errors().get(0).line());
        assertEquals("Не удалось обработать строку CSV", result.errors().get(0).message());
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionConfig {
    }
}
