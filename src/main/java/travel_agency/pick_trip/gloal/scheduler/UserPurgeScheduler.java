package travel_agency.pick_trip.gloal.scheduler;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 유예 기간이 지난 계정을 하드 삭제하는 {@code userPurgeJob} 스케줄러.
 *
 * <p>{@code @Scheduled}는 {@code @EnableScheduling}이 활성화된 운영 프로파일에서만 동작한다
 * ({@link travel_agency.pick_trip.gloal.config.SchedulingConfig}). 실행마다 {@code run.time}
 * 파라미터가 달라 새 JobInstance 가 만들어지며, 실행 이력은 BATCH_* 테이블에 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPurgeScheduler {

    private final JobOperator jobOperator;
    private final Job userPurgeJob;

    /** 기본값: 매일 새벽 4시. */
    @Scheduled(cron = "${user.purge.cron:0 0 4 * * *}")
    public void purgeExpiredWithdrawnUsers() {
        log.info("[스케줄] 탈퇴 계정 하드 삭제 시작");
        try {
            JobParameters parameters = new JobParametersBuilder()
                    .addLocalDateTime("run.time", LocalDateTime.now())
                    .toJobParameters();
            JobExecution execution = jobOperator.start(userPurgeJob, parameters);
            log.info("[스케줄] 탈퇴 계정 하드 삭제 완료 - status: {}", execution.getStatus());
        } catch (Exception e) {
            log.error("[스케줄] 탈퇴 계정 하드 삭제 실패: {}", e.getMessage(), e);
        }
    }
}
