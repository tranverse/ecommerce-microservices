package com.example.ecommerce.auth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthAccountTest {

    @Test
    void normalizesEmailAndRegistersOnlyTheCustomerRole() {
        AuthAccount account = AuthAccount.registerCustomer("  Alice@Example.COM ", "bcrypt-hash");

        assertThat(account.getEmail()).isEqualTo("alice@example.com");
        assertThat(account.getRoles()).containsExactly(AccountRole.CUSTOMER);
        assertThat(account.isActive()).isTrue();
    }

    @Test
    void disabledAccountIsNotActive() {
        AuthAccount account = AuthAccount.registerCustomer("alice@example.com", "bcrypt-hash");

        account.disable();

        assertThat(account.isActive()).isFalse();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.DISABLED);
    }

    @Test
    void rejectsAnEmptyRoleSetThroughThePublicFactoryContract() {
        assertThatThrownBy(() -> AuthAccount.registerCustomer(" ", "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
