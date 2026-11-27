package com.busanlocalfood.favorite.repository;

import com.busanlocalfood.favorite.domain.Favorite;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    boolean existsByMemberIdAndRestaurantId(Long memberId, Long restaurantId);

    Optional<Favorite> findByMemberIdAndRestaurantId(Long memberId, Long restaurantId);

    /** 맛집별 찜 수. 상세 화면과 찜 응답에 쓴다. idx_favorite_restaurant을 쓴다. */
    long countByRestaurantId(Long restaurantId);

    List<Favorite> findByRestaurantId(Long restaurantId);

    /**
     * 목록의 찜 상태를 한 번에 읽는다. 회원이 찜한 맛집 ID만 돌려준다. 맛집마다 따로 묻지 않아 N+1이 생기지 않는다.
     * 찜하지 않은 맛집은 결과에 없다.
     */
    @Query("select f.restaurant.id from Favorite f where f.member.id = :memberId and f.restaurant.id in :restaurantIds")
    List<Long> findFavoritedRestaurantIds(@Param("memberId") Long memberId,
                                          @Param("restaurantIds") Collection<Long> restaurantIds);

    /**
     * 내 찜 목록, 최신 찜 순 (API_SPEC 8.3). 맛집과 카테고리를 한 번에 읽어 항목마다 추가 조회가 생기지 않게 한다.
     * 맛집 행은 CASCADE로 함께 지워지므로 삭제된 맛집은 나타나지 않는다.
     */
    @EntityGraph(attributePaths = {"restaurant", "restaurant.category"})
    Page<Favorite> findByMemberIdOrderByCreatedAtDescIdDesc(Long memberId, Pageable pageable);
}
