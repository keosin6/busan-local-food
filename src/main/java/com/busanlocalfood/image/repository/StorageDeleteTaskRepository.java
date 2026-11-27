package com.busanlocalfood.image.repository;

import com.busanlocalfood.image.domain.StorageDeleteTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageDeleteTaskRepository extends JpaRepository<StorageDeleteTask, Long> {

    Optional<StorageDeleteTask> findByS3Key(String s3Key);
}
