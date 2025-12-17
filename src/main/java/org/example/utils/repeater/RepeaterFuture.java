package org.example.utils.repeater;

public interface RepeaterFuture {
    RepeaterFuture EMPTY_TASK = new RepeaterFuture() {
    };

    default void await() throws InterruptedException {}
}
