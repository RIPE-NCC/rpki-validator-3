package net.ripe.rpki.validator3.api.roas;

import lombok.Data;

@Data(staticConstructor = "of")
public class Sorting {
    final By by;
    final Direction direction;

    public enum Direction {
        ASC, DESC
    }

    public enum By {
        PREFIX, ASN, TA,
    }
}
