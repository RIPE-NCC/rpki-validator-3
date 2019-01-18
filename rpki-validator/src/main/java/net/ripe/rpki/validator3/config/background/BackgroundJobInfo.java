package net.ripe.rpki.validator3.config.background;

import org.quartz.Trigger;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.quartz.listeners.SchedulerListenerSupport;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class BackgroundJobInfo extends SchedulerListenerSupport {

    private  Map<String, Date> backgroundJobStats = new HashMap<>();

    @Override
    public void jobScheduled(Trigger trigger) {
        backgroundJobStats.put(((SimpleTriggerImpl) trigger).getJobName(), trigger.getStartTime());
    }

    @Override
    public void triggerFinalized(Trigger trigger) {
        backgroundJobStats.put(((SimpleTriggerImpl) trigger).getJobName(), trigger.getFinalFireTime());
    }

    public Map<String, Date> getStat() {
        return backgroundJobStats;
    }
}
