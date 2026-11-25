package ru.funeralagency.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ru.funeralagency.dto.BulkOperation;
import ru.funeralagency.dto.FuneralRequestBulkDto;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.repository.CeremonyTypeRepository;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.repository.TagRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FuneralRequestBulkTransactionTest {
    private AnnotationConfigApplicationContext context;
    private PlatformTransactionManager transactions;
    private FuneralRequestRepository repository;
    private FuneralRequestBulkService bulk;
    private final List<TransactionStatus> statuses = new ArrayList<>();
    private final List<Integer> propagations = new ArrayList<>();
    private final List<UUID> uuids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        transactions = mock(PlatformTransactionManager.class);
        repository = mock(FuneralRequestRepository.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            propagations.add(definition.getPropagationBehavior());
            var status = new SimpleTransactionStatus(true);
            statuses.add(status);
            return status;
        });
        for (UUID uuid : uuids) {
            FuneralRequest request = new FuneralRequest();
            request.setUuid(uuid);
            when(repository.findByUuid(uuid)).thenReturn(Optional.of(request));
        }
        context = new AnnotationConfigApplicationContext();
        context.registerBean(PlatformTransactionManager.class, () -> transactions);
        context.registerBean(FuneralRequestRepository.class, () -> repository);
        context.registerBean(ClientRepository.class, () -> mock(ClientRepository.class));
        context.registerBean(CeremonyTypeRepository.class, () -> mock(CeremonyTypeRepository.class));
        context.registerBean(TagRepository.class, () -> mock(TagRepository.class));
        context.register(TransactionConfig.class, FuneralRequestMapper.class,
                TagService.class, FuneralRequestBulkService.class);
        context.registerBean(FuneralRequestService.class, () -> new FuneralRequestService(
                context.getBean(FuneralRequestRepository.class), context.getBean(ClientRepository.class),
                context.getBean(FuneralRequestMapper.class), context.getBean(CeremonyTypeRepository.class),
                context.getBean(TagService.class), mock(AuditService.class)));
        context.refresh();
        bulk = context.getBean(FuneralRequestBulkService.class);
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void springProxyRollsBackFailedItemAndCommitsOtherItemsSeparately() {
        doAnswer(invocation -> {
            FuneralRequest request = invocation.getArgument(0);
            if (request.getUuid().equals(uuids.get(1))) {
                throw new DataIntegrityViolationException("constraint");
            }
            return request;
        }).when(repository).saveAndFlush(any());
        var response = bulk.execute(new FuneralRequestBulkDto(BulkOperation.ARCHIVE, uuids, null, null));
        assertEquals(List.of(uuids.get(0), uuids.get(2)), response.successful());
        assertEquals(uuids.get(1), response.errors().get(0).uuid());
        assertEquals(List.of(TransactionDefinition.PROPAGATION_NOT_SUPPORTED,
                TransactionDefinition.PROPAGATION_REQUIRED, TransactionDefinition.PROPAGATION_REQUIRED,
                TransactionDefinition.PROPAGATION_REQUIRED), propagations);
        var order = inOrder(transactions);
        order.verify(transactions).commit(statuses.get(1));
        order.verify(transactions).rollback(statuses.get(2));
        order.verify(transactions).commit(statuses.get(3));
        order.verify(transactions).commit(statuses.get(0));
    }

    @Test
    void commitFailureIsReportedAsErrorAndDoesNotStopLaterTransactions() {
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == statuses.get(1)) {
                throw new TransactionSystemException("internal commit failure");
            }
            return null;
        }).when(transactions).commit(any());
        var response = bulk.execute(new FuneralRequestBulkDto(BulkOperation.ARCHIVE, uuids, null, null));
        assertEquals(List.of(uuids.get(1), uuids.get(2)), response.successful());
        assertEquals(uuids.get(0), response.errors().get(0).uuid());
        assertEquals("Не удалось выполнить операцию с заявкой", response.errors().get(0).message());
        verify(transactions).commit(statuses.get(2));
        verify(transactions).commit(statuses.get(3));
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionConfig {
    }
}
