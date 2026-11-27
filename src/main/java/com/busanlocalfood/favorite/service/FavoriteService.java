package com.busanlocalfood.favorite.service;

import com.busanlocalfood.favorite.domain.Favorite;
import com.busanlocalfood.favorite.dto.FavoriteDtos.FavoriteState;
import com.busanlocalfood.favorite.repository.FavoriteRepository;
import com.busanlocalfood.global.exception.BusinessException;
import com.busanlocalfood.global.exception.ErrorCode;
import com.busanlocalfood.member.domain.Member;
import com.busanlocalfood.member.repository.MemberRepository;
import com.busanlocalfood.restaurant.domain.Restaurant;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.PageResponse;
import com.busanlocalfood.restaurant.dto.RestaurantDtos.RestaurantSummary;
import com.busanlocalfood.restaurant.repository.RestaurantRepository;
import com.busanlocalfood.restaurant.service.RestaurantService;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 찜 서비스 (FUNCTION_SPEC F1-06, API_SPEC 8). 찜하기, 찜 취소, 내 찜 목록을 다룬다.
 *
 * 모든 처리는 로그인한 회원 본인의 찜만 대상으로 한다. 찜 취소도 회원 ID로 찾으므로 다른 회원의 찜에는 닿지 않는다.
 * 찜하기와 찜 취소는 맛집 행을 잠근 뒤 처리한다. 맛집 삭제와 겹치면 FK 오류 대신 맛집 없음으로 끝난다.
 * 같은 회원의 동시 찜하기는 잠금으로 줄을 세우고, 최종적으로는 uk_favorite_member_restaurant가 막는다.
 */
@Service
public class FavoriteService {

    static final int DEFAULT_SIZE = 12;
    static final int MAX_SIZE = 50;

    private final FavoriteRepository favorites;
    private final RestaurantRepository restaurants;
    private final MemberRepository members;
    private final RestaurantService restaurantService;
    private final TransactionTemplate tx;

    public FavoriteService(FavoriteRepository favorites, RestaurantRepository restaurants, MemberRepository members,
                           RestaurantService restaurantService, PlatformTransactionManager transactionManager) {
        this.favorites = favorites;
        this.restaurants = restaurants;
        this.members = members;
        this.restaurantService = restaurantService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** 찜하기 (API_SPEC 8.1). 이미 찜했으면 FAVORITE_ALREADY_EXISTS다. */
    public FavoriteState favorite(long restaurantId, long memberId) {
        return tx.execute(status -> {
            Restaurant restaurant = restaurants.findByIdForUpdate(restaurantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
            Member member = members.findById(memberId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID));
            if (favorites.existsByMemberIdAndRestaurantId(memberId, restaurantId)) {
                throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
            }
            try {
                favorites.saveAndFlush(Favorite.create(member, restaurant));
            } catch (DataIntegrityViolationException e) {
                // 잠금 밖에서 같은 제약이 먼저 걸린 경우의 안전장치다. 찜 행은 롤백된다.
                throw new BusinessException(ErrorCode.FAVORITE_ALREADY_EXISTS);
            }
            return new FavoriteState(restaurantId, true, countOf(restaurantId));
        });
    }

    /** 찜 취소 (API_SPEC 8.2). 본인이 찜하지 않았으면 FAVORITE_NOT_FOUND다. 다른 회원의 찜은 건드리지 않는다. */
    public FavoriteState unfavorite(long restaurantId, long memberId) {
        return tx.execute(status -> {
            restaurants.findByIdForUpdate(restaurantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
            Favorite favorite = favorites.findByMemberIdAndRestaurantId(memberId, restaurantId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FAVORITE_NOT_FOUND));
            favorites.delete(favorite);
            favorites.flush();
            return new FavoriteState(restaurantId, false, countOf(restaurantId));
        });
    }

    /**
     * 내 찜 목록 (API_SPEC 8.3). 본인 찜만, 최신 찜 순이다. 항목은 맛집 목록 항목과 같다.
     * 대표 이미지는 맛집 목록과 같은 방식으로 한 번의 쿼리로 모은다.
     */
    @Transactional(readOnly = true)
    public PageResponse<RestaurantSummary> list(long memberId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Page<Favorite> result = favorites.findByMemberIdOrderByCreatedAtDescIdDesc(memberId,
                PageRequest.of(page, size));
        List<Restaurant> items = result.getContent().stream().map(Favorite::getRestaurant).toList();
        return new PageResponse<>(restaurantService.summaries(items), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private int countOf(long restaurantId) {
        return (int) favorites.countByRestaurantId(restaurantId);
    }
}
