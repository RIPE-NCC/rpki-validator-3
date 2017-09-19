package net.ripe.rpki.validator3.api;

import lombok.Value;

@Value(staticConstructor = "of")
public class ApiCommand<T> {
    T data;
}
