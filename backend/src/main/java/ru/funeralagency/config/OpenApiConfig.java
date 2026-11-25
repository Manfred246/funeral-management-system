package ru.funeralagency.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OperationCustomizer;
import ru.funeralagency.dto.ApiErrorResponse;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI funeralAgencyApi() {
        Components components = new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
        ModelConverters.getInstance().read(ApiErrorResponse.class).forEach(components::addSchemas);
        return new OpenAPI().info(new Info().title("Funeral Management System API").version("1.0")
                        .description("Swagger/OpenAPI доступны только ADMIN с Bearer JWT. "
                                + "Login/refresh/logout не требуют access token. Ошибки возвращают ApiErrorResponse с correlationId."))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OperationCustomizer apiErrorDocumentation() {
        return (operation, method) -> {
            operation.getResponses().addApiResponse("default", new ApiResponse()
                    .description("REST error: 400/401/403/404/409/500; status и correlationId находятся в JSON")
                    .content(new Content().addMediaType("application/json", new MediaType()
                            .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse")))));
            return operation;
        };
    }
}
