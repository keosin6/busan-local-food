/*
 * 공통 화면 스크립트. 현재 기능은 두 가지다.
 * 1) 정적 스크립트가 로드되었음을 표시한다 (data-app-ready).
 * 2) API 429 안내 (T20). api-client.js가 보내는 api:rate-limited 이벤트를 받아 안내 한 줄을 보여 준다.
 *    안내는 항상 요소 하나만 쓴다. 다시 오면 메시지만 갱신하므로 여러 개가 쌓이지 않는다.
 *    재요청은 하지 않는다. 사용자가 닫거나 직접 다시 시도할 수 있다. 인증 상태, 쿠키, 페이지 이동에는 관여하지 않는다.
 */
(function (root) {
    'use strict';

    var RATE_LIMITED_EVENT = 'api:rate-limited';
    var NOTICE_ID = 'api-rate-limit-notice';
    var MESSAGE_ID = 'api-rate-limit-message';
    var GENERIC_WAIT = '잠시 후 다시 시도해 주세요.';

    // Retry-After 초를 사용자에게 보일 문구로 바꾼다. 값이 없거나 0이면 대기 시간을 말하지 않는다.
    function formatWait(seconds) {
        if (typeof seconds !== 'number' || !(seconds > 0)) {
            return GENERIC_WAIT;
        }
        if (seconds < 60) {
            return '약 ' + seconds + '초 후에 다시 시도해 주세요.';
        }
        return '약 ' + Math.ceil(seconds / 60) + '분 후에 다시 시도해 주세요.';
    }

    function createRateLimitNotice(doc) {
        function ensureElement() {
            var existing = doc.getElementById(NOTICE_ID);
            if (existing) {
                return existing;
            }
            var notice = doc.createElement('div');
            notice.setAttribute('id', NOTICE_ID);
            notice.setAttribute('class', 'alert alert-warning alert-dismissible position-fixed top-0 start-50 translate-middle-x mt-3 shadow-sm');
            notice.setAttribute('role', 'alert');
            notice.setAttribute('style', 'z-index: 1080; max-width: 520px; width: calc(100% - 2rem);');

            var message = doc.createElement('span');
            message.setAttribute('id', MESSAGE_ID);
            notice.appendChild(message);

            var close = doc.createElement('button');
            close.setAttribute('type', 'button');
            close.setAttribute('class', 'btn-close');
            close.setAttribute('data-bs-dismiss', 'alert');
            close.setAttribute('aria-label', '닫기');
            notice.appendChild(close);

            doc.body.appendChild(notice);
            return notice;
        }

        return {
            show: function (info) {
                var seconds = info && typeof info.retryAfterSeconds === 'number' ? info.retryAfterSeconds : null;
                var element = ensureElement();
                var message = doc.getElementById(MESSAGE_ID);
                message.textContent = '요청이 너무 많습니다. ' + formatWait(seconds);
                return element;
            }
        };
    }

    var app = { formatWait: formatWait, createRateLimitNotice: createRateLimitNotice };

    if (typeof module === 'object' && module.exports) {
        module.exports = app;
    } else if (root && root.document) {
        root.document.documentElement.dataset.appReady = 'true';
        var notice = createRateLimitNotice(root.document);
        root.document.addEventListener(RATE_LIMITED_EVENT, function (event) {
            notice.show(event.detail);
        });
        // 삭제처럼 되돌릴 수 없는 폼은 data-confirm 문구로 한 번 확인한다. 취소하면 제출하지 않는다.
        root.document.addEventListener('submit', function (event) {
            var form = event.target;
            if (form && form.hasAttribute && form.hasAttribute('data-confirm')) {
                if (!root.confirm(form.getAttribute('data-confirm'))) {
                    event.preventDefault();
                }
            }
        });
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this));
