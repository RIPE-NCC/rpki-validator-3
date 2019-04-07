package net.ripe.rpki.validator3.jobs;

import lombok.AllArgsConstructor;
import lombok.Value;

public class JobExecutor {

    enum Type {
        TA_VALIDATION, CAT_VALIDATION, REPO_VALIDATION
    }

    @Value
    @AllArgsConstructor
    public static class Job<T> {
        private Type type;
        private T tag;
        private Runnable r;
    }

    public void scheduleTaValidation(long trustAnchorId) {

    }

    public void scheduleCaTValidation(long trustAnchorId) {

    }

    public void scheduleRrdpValidation(long trustAnchorId) {

    }

}
