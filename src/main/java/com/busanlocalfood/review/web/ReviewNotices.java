package com.busanlocalfood.review.web;

/** 맛집 상세 화면의 리뷰 안내 문구. 리다이렉트 파라미터 값을 사용자에게 보일 문장으로 바꾼다. */
public final class ReviewNotices {

    private ReviewNotices() {
    }

    public static String notice(String review, String reviewError) {
        if (reviewError != null) {
            return switch (reviewError) {
                case "duplicate" -> "이 맛집에는 이미 리뷰를 작성했습니다. 리뷰는 맛집마다 하나만 쓸 수 있습니다.";
                case "invalid" -> "평점은 1~5점, 내용은 1~500자로 입력해 주세요.";
                case "forbidden" -> "본인이 작성한 리뷰만 삭제할 수 있습니다.";
                case "not_found" -> "리뷰를 찾을 수 없습니다. 이미 삭제되었을 수 있습니다.";
                default -> "리뷰를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
            };
        }
        if ("added".equals(review)) {
            return "리뷰를 등록했습니다.";
        }
        if ("deleted".equals(review)) {
            return "리뷰를 삭제했습니다.";
        }
        return null;
    }
}
