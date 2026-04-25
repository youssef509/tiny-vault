package com.youssef.storageservice.health;

import com.youssef.storageservice.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;

/**
 * Custom disk space health indicator for the storage directory.
 *
 * Reports UNHEALTHY when available disk space drops below the threshold.
 * The threshold is configurable via app.storage.disk-health-threshold-percent (default 5%).
 *
 * Exposed at: GET /actuator/health (under the "storageSpace" component)
 *
 * Example healthy response:
 * {
 *   "status": "UP",
 *   "details": {
 *     "path": "/var/storage",
 *     "freeSpaceBytes": 80000000000,
 *     "totalSpaceBytes": 161061273600,
 *     "freePercent": 49.7,
 *     "threshold": "5%"
 *   }
 * }
 */
@Slf4j
@Component("storageSpace")
@RequiredArgsConstructor
public class StorageHealthIndicator implements HealthIndicator {

    private static final double DEFAULT_THRESHOLD_PERCENT = 5.0;

    private final StorageProperties storageProperties;

    @Override
    public Health health() {
        Path storagePath = Path.of(storageProperties.getBasePath());
        File storageDir  = storagePath.toFile();

        if (!storageDir.exists()) {
            return Health.down()
                    .withDetail("path", storagePath.toString())
                    .withDetail("error", "Storage directory does not exist")
                    .build();
        }

        long totalBytes = storageDir.getTotalSpace();
        long freeBytes  = storageDir.getUsableSpace();

        if (totalBytes == 0) {
            return Health.unknown()
                    .withDetail("path", storagePath.toString())
                    .withDetail("error", "Cannot determine disk space")
                    .build();
        }

        double freePercent = (freeBytes * 100.0) / totalBytes;

        Health.Builder builder = freePercent <= DEFAULT_THRESHOLD_PERCENT
                ? Health.down()
                : Health.up();

        return builder
                .withDetail("path",           storagePath.toString())
                .withDetail("freeSpaceBytes",  freeBytes)
                .withDetail("totalSpaceBytes", totalBytes)
                .withDetail("freePercent",     String.format("%.1f%%", freePercent))
                .withDetail("threshold",       String.format("%.0f%%", DEFAULT_THRESHOLD_PERCENT))
                .build();
    }
}
