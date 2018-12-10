package net.ripe.rpki.validator3.api.rpkiobjects;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Certified {
    List<String> resources;
}
