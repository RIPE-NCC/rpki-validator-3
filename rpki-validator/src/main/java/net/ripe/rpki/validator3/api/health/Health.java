package net.ripe.rpki.validator3.api.health;

import java.util.Map;

@lombok.Value(staticConstructor = "of")
public class Health {
    Map<String, Boolean> trustAnchorReady;
    Map<String, Boolean> bgpDumpReady;
    String databaseStatus;
}
