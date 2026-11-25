package ru.funeralagency.repository;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.Tag;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AnalyticsQueryTest {
    @Test
    void hibernateCompilesAllAggregateQueriesWithoutDatabaseConnection() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();
        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(FuneralRequest.class)
                    .addAnnotatedClass(Client.class).addAnnotatedClass(CeremonyType.class).addAnnotatedClass(Tag.class);
            try (var factory = metadata.buildMetadata().buildSessionFactory(); var session = factory.openSession()) {
                for (String name : new String[]{"summarizeRequests", "countByCeremonyType", "countByStatus", "countByPriority"}) {
                    String jpql = FuneralRequestRepository.class.getMethod(name).getAnnotation(Query.class).value();
                    // Проверяется HQL/модель, а не выполнение SQL или результаты PostgreSQL.
                    assertNotNull(session.createQuery(jpql, Object[].class));
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
