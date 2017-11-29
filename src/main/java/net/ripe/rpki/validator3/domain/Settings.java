package net.ripe.rpki.validator3.domain;

import java.util.Optional;

public interface Settings {
    Optional<String> get(String key);
    void put(String key, String value);
}
