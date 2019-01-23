package net.ripe.rpki.validator3.config.background;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.TreeMap;

@Component
public class BackgroundJobInfo extends JobListenerSupport {

    private  TreeMap<String, Date> backgroundJobStats = new TreeMap<>();

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        backgroundJobStats.put(context.getJobDetail().getJobClass().toString(), new Date());
        super.jobWasExecuted(context, jobException);
    }

    @Override
    public String getName() {
        return "Stat collector";
    }

    public TreeMap<String, Date> getStat() {
        return backgroundJobStats;
    }
}
