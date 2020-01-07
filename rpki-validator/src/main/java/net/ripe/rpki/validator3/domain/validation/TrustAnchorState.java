package net.ripe.rpki.validator3.domain.validation;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * It is to keep track of the state of a TA. A TA transitions to the VALIDATED when
 * and only when the data for it has been downloaded and the tree validation has been
 * performed.
 */
@Component
@Slf4j
public class TrustAnchorState {
    private enum State {
        UNKNOWN,
        VALIDATED
    }

    private final Map<String, State> states = new HashMap<>();

    public boolean allValidated() {
        synchronized (states) {
            return states.values().stream().allMatch(s -> s.equals(State.VALIDATED));
        }
    }

    public void setUnknown(TrustAnchor ta) {
        setState(ta, State.UNKNOWN);
    }

    public void setValidatedAfterLastRepositoryUpdate(TrustAnchor ta) {
        setState(ta, State.VALIDATED);
    }

    private void setState(TrustAnchor ta, State state) {
        log.debug("Setting TA {} to {}", ta.getName(), state);
        synchronized (states) {
            states.put(ta.getName(), state);
        }
    }

}
