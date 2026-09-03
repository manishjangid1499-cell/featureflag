package com.featureflag.flag_service.service;

import com.featureflag.flag_service.entity.FeatureFlag;
import com.featureflag.flag_service.repository.FeatureFlagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlagQueryServiceTest {

    @Test
    void delegatesPaginationToRepository() {
        FeatureFlagRepository repository =
                mock(FeatureFlagRepository.class);
        FlagQueryService service = new FlagQueryService(repository);
        PageRequest pageable = PageRequest.of(2, 20);
        when(repository.findAll(pageable)).thenReturn(Page.empty(pageable));

        assertThat(service.findAll(pageable).getNumber()).isEqualTo(2);
        verify(repository).findAll(pageable);
    }
}
