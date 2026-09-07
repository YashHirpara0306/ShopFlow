package com.shopflow.order.domain;

import com.shopflow.common.error.ConflictException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    @Test
    void happyPathTransitionsAreAllowed() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.INVENTORY_PENDING)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.INVENTORY_PENDING, OrderStatus.AWAITING_PAYMENT)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.AWAITING_PAYMENT, OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.REFUNDED)).isTrue();
    }

    @Test
    void cancellationIsAllowedFromEveryPreConfirmState() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.INVENTORY_PENDING, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStateMachine.canTransition(OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void illegalJumpsAreRejected() {
        assertThat(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.PENDING)).isFalse();
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (OrderStatus terminal : new OrderStatus[]{OrderStatus.CANCELLED, OrderStatus.REFUNDED, OrderStatus.FAILED}) {
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(OrderStateMachine.canTransition(terminal, target)).isFalse();
            }
        }
    }

    @Test
    void validateThrowsConflictOnIllegalTransition() {
        assertThatThrownBy(() -> OrderStateMachine.validate(OrderStatus.PENDING, OrderStatus.CONFIRMED))
                .isInstanceOf(ConflictException.class);
        assertThatCode(() -> OrderStateMachine.validate(OrderStatus.PENDING, OrderStatus.INVENTORY_PENDING))
                .doesNotThrowAnyException();
    }
}
