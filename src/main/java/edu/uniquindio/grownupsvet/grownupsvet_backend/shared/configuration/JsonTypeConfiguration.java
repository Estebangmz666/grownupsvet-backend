package edu.uniquindio.grownupsvet.grownupsvet_backend.shared.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;

import java.time.LocalDate;

/** Keeps JSON values consistent with the types declared in the API contract. */
@Configuration(proxyBeanMethods = false)
public class JsonTypeConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer strictStringInputCustomizer() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .withCoercionConfig(LogicalType.Enum, coercion -> {
                    coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                    coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                    coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                    coercion.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
                })
                .withCoercionConfig(LogicalType.Boolean, coercion -> {
                    coercion.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                    coercion.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
                    coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                    coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                })
                .withCoercionConfig(LogicalType.Textual, coercion -> {
            coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }

    @Bean
    public JsonMapperBuilderCustomizer strictLocalDateInputCustomizer() {
        return builder -> builder.addModule(new SimpleModule("strict-local-date-input")
                .addDeserializer(LocalDate.class, new StrictLocalDateDeserializer()));
    }
}
