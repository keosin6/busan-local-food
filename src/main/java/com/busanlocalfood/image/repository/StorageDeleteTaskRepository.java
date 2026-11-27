package com.busanlocalfood.image.repository;

import com.busanlocalfood.image.domain.StorageDeleteTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageDeleteTaskRepository extends JpaRepository<StorageDeleteTask, Long> {

    Optional<StorageDeleteTask> findByS3Key(String s3Key);

    /** 재시도 순회용. id가 큰 행부터 pageable 크기만큼 읽는다. */
    List<StorageDeleteTask> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);

    /** 관리자 목록. 오래된 순이다. */
    Page<StorageDeleteTask> findAllByOrderByCreatedAtAscIdAsc(Pageable pageable);
}
