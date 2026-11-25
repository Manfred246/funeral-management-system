package ru.funeralagency.repository;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import ru.funeralagency.model.AuditEvent;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RefreshSession;
import ru.funeralagency.model.RequestNote;
import ru.funeralagency.model.Tag;
import ru.funeralagency.model.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PersistenceMetadataTest {
    @Test
    void allEntitiesAndSpringDataQueryMethodsInitializeWithoutDatabase() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false).build();
        try {
            MetadataSources metadata = new MetadataSources(registry);
            for (Class<?> entity : List.of(Client.class, FuneralRequest.class, CeremonyType.class, Tag.class,
                    User.class, RefreshSession.class, RequestNote.class, AuditEvent.class)) {
                metadata.addAnnotatedClass(entity);
            }
            try (var factory = metadata.buildMetadata().buildSessionFactory(); var session = factory.openSession()) {
                assertEquals(8, factory.getMetamodel().getEntities().size());
                JpaRepositoryFactory repositories = new JpaRepositoryFactory(session);
                for (Class<?> repository : List.of(ClientRepository.class, FuneralRequestRepository.class,
                        CeremonyTypeRepository.class, TagRepository.class, UserRepository.class,
                        RefreshSessionRepository.class, RequestNoteRepository.class, AuditEventRepository.class)) {
                    assertNotNull(repositories.getRepository(repository));
                }
                // SQL и соответствие реальной схеме проверяются отдельно PostgreSQL IT.
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
