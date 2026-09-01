package com.bank.batch.config;

import com.bank.batch.service.DuplicateTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BatchJobExecutionListener implements JobExecutionListener {
    private static final Logger logger = LoggerFactory.getLogger(BatchJobExecutionListener.class);
    private final DuplicateTracker duplicateTracker;

    public BatchJobExecutionListener(DuplicateTracker duplicateTracker) {
        this.duplicateTracker = duplicateTracker;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        duplicateTracker.reset();
        logger.info("JOB_INICIO nombre={} id={} parametros={}", jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(), jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        for (StepExecution step : jobExecution.getStepExecutions()) {
            logger.info("STEP_RESUMEN nombre={} estado={} read={} write={} filter={} readSkip={} processSkip={} writeSkip={} commits={} rollbacks={}",
                    step.getStepName(), step.getStatus(), step.getReadCount(), step.getWriteCount(),
                    step.getFilterCount(), step.getReadSkipCount(), step.getProcessSkipCount(),
                    step.getWriteSkipCount(), step.getCommitCount(), step.getRollbackCount());
        }
        long duration = jobExecution.getStartTime() == null || jobExecution.getEndTime() == null
                ? -1 : Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
        logger.info("JOB_RESUMEN nombre={} estado={} exitCode={} duracionMs={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getStatus(),
                jobExecution.getExitStatus().getExitCode(), duration);
    }
}
