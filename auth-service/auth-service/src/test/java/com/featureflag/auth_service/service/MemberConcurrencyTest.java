package com.featureflag.auth_service.service;

import com.featureflag.auth_service.entity.Role;
import com.featureflag.auth_service.entity.User;
import com.featureflag.auth_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@Import(MemberService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MemberConcurrencyTest {
    @Autowired private UserRepository users;
    @Autowired private MemberService members;

    @Test
    void staleRoleSaveCannotRestoreDisabledAccount() {
        User member = users.saveAndFlush(User.builder().email("race@example.test")
                .password("test-hash").role(Role.DEVELOPER).build());
        User stale = users.findById(member.getId()).orElseThrow();
        User owner = User.builder().id(-1L).role(Role.OWNER).build();

        members.updateEnabled(member.getId(), false, owner);
        stale.setRole(Role.VIEWER);

        assertThatThrownBy(() -> users.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        User retained = users.findById(member.getId()).orElseThrow();
        assertThat(retained.isEnabled()).isFalse();
        assertThat(retained.getRole()).isEqualTo(Role.DEVELOPER);
    }
}
