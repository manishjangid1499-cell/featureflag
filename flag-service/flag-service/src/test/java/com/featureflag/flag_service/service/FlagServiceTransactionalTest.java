package com.featureflag.flag_service.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class FlagServiceTransactionalTest {

    @Test
    void mutatingOperationsAreTransactional()
            throws Exception {

        assertTransactional(
                "createFlag",
                com.featureflag.flag_service.dto
                        .FlagRequest.class
        );
        assertTransactional(
                "updateFlag",
                Long.class,
                com.featureflag.flag_service.dto
                        .FlagRequest.class
        );
        assertTransactional(
                "deleteFlag",
                Long.class
        );
        assertTransactional(
                "toggleFlag",
                Long.class
        );
    }

    private void assertTransactional(
            String methodName,
            Class<?>... parameterTypes
    ) throws Exception {

        Method method =
                FlagService.class.getMethod(
                        methodName,
                        parameterTypes
                );

        assertThat(
                method.getAnnotation(
                        Transactional.class
                )
        ).isNotNull();
    }
}
