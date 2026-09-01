package com.bank.batch.config;

import com.bank.batch.mapper.AccountFieldSetMapper;
import com.bank.batch.mapper.AnnualStatementFieldSetMapper;
import com.bank.batch.mapper.FlexibleLocalDateParser;
import com.bank.batch.mapper.TransactionFieldSetMapper;
import com.bank.batch.model.Account;
import com.bank.batch.model.AnnualStatement;
import com.bank.batch.model.Transaction;
import com.bank.batch.processor.AnnualStatementProcessor;
import com.bank.batch.processor.InterestProcessor;
import com.bank.batch.processor.TransactionProcessor;
import com.bank.batch.repository.AccountRepository;
import com.bank.batch.repository.AnnualStatementRepository;
import com.bank.batch.repository.TransactionRepository;
import com.bank.batch.service.SummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;

@Configuration
public class BatchConfig {
    private static final Logger logger = LoggerFactory.getLogger(BatchConfig.class);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CustomSkipPolicy customSkipPolicy;
    private final CustomSkipListener customSkipListener;
    private final CustomRetryListener customRetryListener;

    public BatchConfig(JobRepository jobRepository,
                       PlatformTransactionManager transactionManager,
                       CustomSkipPolicy customSkipPolicy,
                       CustomSkipListener customSkipListener,
                       CustomRetryListener customRetryListener) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.customSkipPolicy = customSkipPolicy;
        this.customSkipListener = customSkipListener;
        this.customRetryListener = customRetryListener;
    }

    @Bean(name = "batchTaskExecutor")
    public TaskExecutor batchTaskExecutor(
            @Value("${bank.batch.core-pool-size:3}") int corePoolSize,
            @Value("${bank.batch.max-pool-size:3}") int maxPoolSize,
            @Value("${bank.batch.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("Batch-Thread-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setKeepAliveSeconds(1);
        executor.setAllowCoreThreadTimeOut(true);
        executor.initialize();
        logger.info("ESCALAMIENTO_CONFIGURADO corePool={} maxPool={} queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    @Bean
    public ExponentialBackOffPolicy batchBackOffPolicy(
            @Value("${bank.batch.retry-initial-interval-ms:250}") long initialInterval) {
        ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
        policy.setInitialInterval(initialInterval);
        policy.setMultiplier(2.0);
        policy.setMaxInterval(2_000L);
        return policy;
    }

    @Bean
    public Partitioner transactionPartitioner(
            @Value("${bank.batch.input-directory:data}") String inputDirectory) throws IOException {
        return createPartitioner(inputDirectory, "transacciones.csv");
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Transaction> transactionReader(
            @Value("#{stepExecutionContext['fileName']}") String filename,
            FlexibleLocalDateParser dateParser) throws MalformedURLException {
        UrlResource resource = new UrlResource(filename);
        return new FlatFileItemReaderBuilder<Transaction>()
                .name("transactionReader-" + resource.getFilename())
                .resource(resource)
                .strict(true)
                .linesToSkip(1)
                .delimited()
                .names("id", "fecha", "monto", "tipo")
                .fieldSetMapper(new TransactionFieldSetMapper(dateParser, resource.getFilename()))
                .build();
    }

    @Bean
    public ItemWriter<Transaction> transactionWriter(TransactionRepository repository) {
        return repository::saveAll;
    }

    @Bean
    public Step transactionWorkerStep(
            @Qualifier("transactionReader") FlatFileItemReader<Transaction> reader,
            TransactionProcessor processor,
            @Qualifier("transactionWriter") ItemWriter<Transaction> writer,
            ExponentialBackOffPolicy batchBackOffPolicy,
            @Value("${bank.batch.chunk-size:50}") int chunkSize,
            @Value("${bank.batch.retry-limit:3}") int retryLimit) {
        return new StepBuilder("transactionWorkerStep", jobRepository)
                .<Transaction, Transaction>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(retryLimit)
                .backOffPolicy(batchBackOffPolicy)
                .processorNonTransactional()
                .skipPolicy(customSkipPolicy)
                .listener(customSkipListener)
                .listener(customRetryListener)
                .build();
    }

    @Bean
    public Step transactionPartitionStep(
            @Qualifier("transactionPartitioner") Partitioner partitioner,
            @Qualifier("transactionWorkerStep") Step workerStep,
            @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor,
            @Value("${bank.batch.grid-size:3}") int gridSize) {
        return partitionStep("transactionPartitionStep", "transactionWorkerStep",
                partitioner, workerStep, taskExecutor, gridSize);
    }

    @Bean
    public Step transactionSummaryStep(SummaryService summaryService) {
        return new StepBuilder("transactionSummaryStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    summaryService.rebuildDailySummary();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Job dailyTransactionJob(
            @Qualifier("transactionPartitionStep") Step partitionStep,
            @Qualifier("transactionSummaryStep") Step summaryStep,
            BatchJobExecutionListener listener) {
        return new JobBuilder("dailyTransactionJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(partitionStep)
                .next(summaryStep)
                .build();
    }

    @Bean
    public Partitioner interestPartitioner(
            @Value("${bank.batch.input-directory:data}") String inputDirectory) throws IOException {
        return createPartitioner(inputDirectory, "intereses.csv");
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Account> interestReader(
            @Value("#{stepExecutionContext['fileName']}") String filename) throws MalformedURLException {
        UrlResource resource = new UrlResource(filename);
        return new FlatFileItemReaderBuilder<Account>()
                .name("interestReader-" + resource.getFilename())
                .resource(resource)
                .strict(true)
                .linesToSkip(1)
                .delimited()
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .fieldSetMapper(new AccountFieldSetMapper(resource.getFilename()))
                .build();
    }

    @Bean
    public ItemWriter<Account> interestWriter(AccountRepository repository) {
        return repository::saveAll;
    }

    @Bean
    public Step interestWorkerStep(
            @Qualifier("interestReader") FlatFileItemReader<Account> reader,
            InterestProcessor processor,
            @Qualifier("interestWriter") ItemWriter<Account> writer,
            ExponentialBackOffPolicy batchBackOffPolicy,
            @Value("${bank.batch.chunk-size:50}") int chunkSize,
            @Value("${bank.batch.retry-limit:3}") int retryLimit) {
        return new StepBuilder("interestWorkerStep", jobRepository)
                .<Account, Account>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(retryLimit)
                .backOffPolicy(batchBackOffPolicy)
                .processorNonTransactional()
                .skipPolicy(customSkipPolicy)
                .listener(customSkipListener)
                .listener(customRetryListener)
                .build();
    }

    @Bean
    public Step interestPartitionStep(
            @Qualifier("interestPartitioner") Partitioner partitioner,
            @Qualifier("interestWorkerStep") Step workerStep,
            @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor,
            @Value("${bank.batch.grid-size:3}") int gridSize) {
        return partitionStep("interestPartitionStep", "interestWorkerStep",
                partitioner, workerStep, taskExecutor, gridSize);
    }

    @Bean
    public Job monthlyInterestJob(
            @Qualifier("interestPartitionStep") Step partitionStep,
            BatchJobExecutionListener listener) {
        return new JobBuilder("monthlyInterestJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(partitionStep)
                .build();
    }

    @Bean
    public Partitioner annualStatementPartitioner(
            @Value("${bank.batch.input-directory:data}") String inputDirectory) throws IOException {
        return createPartitioner(inputDirectory, "cuentas_anuales.csv");
    }

    @Bean
    @StepScope
    public FlatFileItemReader<AnnualStatement> annualStatementReader(
            @Value("#{stepExecutionContext['fileName']}") String filename,
            FlexibleLocalDateParser dateParser) throws MalformedURLException {
        UrlResource resource = new UrlResource(filename);
        return new FlatFileItemReaderBuilder<AnnualStatement>()
                .name("annualStatementReader-" + resource.getFilename())
                .resource(resource)
                .strict(true)
                .linesToSkip(1)
                .delimited()
                .names("cuentaId", "fecha", "transaccion", "monto", "descripcion")
                .fieldSetMapper(new AnnualStatementFieldSetMapper(dateParser, resource.getFilename()))
                .build();
    }

    @Bean
    public ItemWriter<AnnualStatement> annualStatementWriter(AnnualStatementRepository repository) {
        return repository::saveAll;
    }

    @Bean
    public Step annualStatementWorkerStep(
            @Qualifier("annualStatementReader") FlatFileItemReader<AnnualStatement> reader,
            AnnualStatementProcessor processor,
            @Qualifier("annualStatementWriter") ItemWriter<AnnualStatement> writer,
            ExponentialBackOffPolicy batchBackOffPolicy,
            @Value("${bank.batch.chunk-size:50}") int chunkSize,
            @Value("${bank.batch.retry-limit:3}") int retryLimit) {
        return new StepBuilder("annualStatementWorkerStep", jobRepository)
                .<AnnualStatement, AnnualStatement>chunk(chunkSize, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(retryLimit)
                .backOffPolicy(batchBackOffPolicy)
                .processorNonTransactional()
                .skipPolicy(customSkipPolicy)
                .listener(customSkipListener)
                .listener(customRetryListener)
                .build();
    }

    @Bean
    public Step annualStatementPartitionStep(
            @Qualifier("annualStatementPartitioner") Partitioner partitioner,
            @Qualifier("annualStatementWorkerStep") Step workerStep,
            @Qualifier("batchTaskExecutor") TaskExecutor taskExecutor,
            @Value("${bank.batch.grid-size:3}") int gridSize) {
        return partitionStep("annualStatementPartitionStep", "annualStatementWorkerStep",
                partitioner, workerStep, taskExecutor, gridSize);
    }

    @Bean
    public Step annualReportStep(SummaryService summaryService) {
        return new StepBuilder("annualReportStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    summaryService.rebuildAnnualSummary();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Job annualStatementJob(
            @Qualifier("annualStatementPartitionStep") Step partitionStep,
            @Qualifier("annualReportStep") Step reportStep,
            BatchJobExecutionListener listener) {
        return new JobBuilder("annualStatementJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(partitionStep)
                .next(reportStep)
                .build();
    }

    private Partitioner createPartitioner(String inputDirectory, String fileName) throws IOException {
        String normalized = Path.of(inputDirectory).toAbsolutePath().normalize().toString().replace('\\', '/');
        String pattern = "file:" + normalized + "/semana_*/" + fileName;
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
        if (resources.length == 0) {
            throw new IllegalStateException("No se encontraron archivos para el patrón " + pattern);
        }
        MultiResourcePartitioner partitioner = new MultiResourcePartitioner();
        partitioner.setKeyName("fileName");
        partitioner.setResources(resources);
        logger.info("PARTICIONES_PREPARADAS archivo={} recursos={} patrón={}",
                fileName, resources.length, pattern);
        return partitioner;
    }

    private Step partitionStep(String managerStepName,
                               String workerStepName,
                               Partitioner partitioner,
                               Step workerStep,
                               TaskExecutor taskExecutor,
                               int gridSize) {
        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setGridSize(gridSize);
        partitionHandler.setTaskExecutor(taskExecutor);
        partitionHandler.setStep(workerStep);

        return new StepBuilder(managerStepName, jobRepository)
                .partitioner(workerStepName, partitioner)
                .partitionHandler(partitionHandler)
                .build();
    }
}
