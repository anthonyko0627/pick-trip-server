package travel_agency.pick_trip.domain.user.batch;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import travel_agency.pick_trip.domain.user.repository.UserRepository;
import travel_agency.pick_trip.domain.user.service.UserPurgeService;

/**
 * 탈퇴 후 {@code user.purge.retention-days} 가 지난 계정을 하드 삭제하는 {@code userPurgeJob}.
 *
 * <p>reader 는 step 시작 시 대상 uid 목록을 한 번에 읽는다. 삭제하면서 페이징 리더를 쓰면
 * 남은 행이 앞으로 밀려 건너뛰는 항목이 생기므로 {@link ListItemReader} 를 쓴다.
 * 실행은 {@link travel_agency.pick_trip.gloal.scheduler.UserPurgeScheduler} 가 담당하며,
 * 부팅 시 자동 실행은 {@code spring.batch.job.enabled=false} 로 막는다.
 */
@Configuration
public class UserPurgeJobConfig {

    public static final String JOB_NAME = "userPurgeJob";
    private static final int CHUNK_SIZE = 10;

    @Bean
    public Job userPurgeJob(JobRepository jobRepository, Step userPurgeStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(userPurgeStep)
                .build();
    }

    @Bean
    public Step userPurgeStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<UUID> expiredWithdrawnUserReader,
            UserPurgeService userPurgeService
    ) {
        return new StepBuilder("userPurgeStep", jobRepository)
                .<UUID, UUID>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(expiredWithdrawnUserReader)
                .writer(chunk -> chunk.getItems().forEach(userPurgeService::purge))
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<UUID> expiredWithdrawnUserReader(
            UserRepository userRepository,
            @Value("${user.purge.retention-days:30}") int retentionDays
    ) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        return new ListItemReader<>(userRepository.findUidsWithdrawnBefore(cutoff));
    }
}
