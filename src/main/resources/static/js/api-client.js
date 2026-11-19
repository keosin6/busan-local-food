/*
 * API 클라이언트와 Access Token 자동 갱신 (T13).
 *
 * 동일 출처의 /api/** 요청만 처리한다. 그 밖의 요청은 손대지 않고 그대로 fetch한다.
 * - API 요청이 401이면 Refresh를 호출하고, 성공하면 원래 요청을 정확히 한 번 재시도한다.
 * - 재시도가 다시 401이면 추가 갱신 없이 auth:reauth-required 이벤트를 낸다.
 * - 같은 탭에서 동시에 발생한 401은 진행 중인 복구 Promise 하나를 공유한다.
 * - 여러 탭은 Web Locks(busan-auth-refresh)로 직렬화한다. 잠금 안에서 /api/auth/me로 다른 탭의 갱신 여부를 확인한다.
 *   Web Locks를 지원하지 않는 환경에서는 같은 탭 공유만 보장된다. 여러 탭 동기화는 보장되지 않는다.
 * - Refresh 응답 429는 인증 상태를 바꾸지 않고 그 응답(Retry-After 포함)을 호출자에게 돌려준다.
 * - Refresh 403은 CSRF 오류로 보고 인증 만료로 단정하지 않는다. 5xx와 네트워크 오류도 인증 상태를 바꾸지 않는다.
 * - 쿠키는 읽기만 한다. 화면 세션(JSESSIONID)과 JWT 쿠키를 쓰거나 지우지 않는다. 토큰 원문은 읽거나 기록하지 않는다.
 *   XSRF-TOKEN 쿠키 값만 Refresh 헤더에 싣는다. 토큰이 없으면 임의 값을 만들지 않는다.
 *
 * 자동 갱신 대상에서 제외하는 경로: 가입, 로그인, Refresh, 로그아웃.
 * 이벤트에는 토큰이나 개인정보를 싣지 않는다.
 */
(function (root) {
    'use strict';

    var LOCK_NAME = 'busan-auth-refresh';
    var EVENT_REAUTH = 'auth:reauth-required';
    var EVENT_REFRESH_FAILED = 'auth:refresh-failed';
    var API_PREFIX = '/api/';
    var PATH_ME = '/api/auth/me';
    var PATH_REFRESH = '/api/auth/refresh';
    var UNMANAGED_PATHS = ['/api/auth/signup', '/api/auth/login', '/api/auth/refresh', '/api/auth/logout'];
    var CSRF_COOKIE = 'XSRF-TOKEN';
    var CSRF_HEADER = 'X-XSRF-TOKEN';

    function createApiClient(options) {
        var opts = options || {};
        var rawFetch = opts.fetch;
        var doc = opts.document;
        var eventTarget = opts.eventTarget || null;
        var locks = opts.locks || null;
        var origin = opts.origin;

        // 갱신이 성공할 때마다 증가한다. 요청은 보낼 때의 값을 기억해, 그 뒤에 끝난 갱신을 알아본다.
        var generation = 0;
        var inflight = null;

        function readCookie(name) {
            var prefix = name + '=';
            var parts = doc && doc.cookie ? doc.cookie.split('; ') : [];
            for (var i = 0; i < parts.length; i++) {
                if (parts[i].indexOf(prefix) === 0) {
                    return decodeURIComponent(parts[i].substring(prefix.length));
                }
            }
            return null;
        }

        function emit(name, reason) {
            if (eventTarget && typeof eventTarget.dispatchEvent === 'function') {
                eventTarget.dispatchEvent(new CustomEvent(name, { detail: { reason: reason } }));
            }
        }

        function toUrl(input) {
            try {
                if (typeof input === 'string' || input instanceof URL) {
                    return new URL(String(input), origin);
                }
                if (input && typeof input.url === 'string') {
                    return new URL(input.url, origin);
                }
            } catch (error) {
                return null;
            }
            return null;
        }

        function isSameOriginApi(url) {
            return url !== null && url.origin === origin && url.pathname.indexOf(API_PREFIX) === 0;
        }

        function isUnmanaged(url) {
            return UNMANAGED_PATHS.indexOf(url.pathname) !== -1;
        }

        function isStreamBody(init) {
            return !!(init && init.body && typeof ReadableStream !== 'undefined' && init.body instanceof ReadableStream);
        }

        // 원래 요청 객체를 바꾸지 않도록 매 시도마다 새 init을 만든다. Request 입력은 복제해서 쓴다.
        function prepare(input, init) {
            var copy = Object.assign({}, init || {});
            if (copy.credentials === undefined) {
                copy.credentials = 'same-origin';
            }
            if (copy.headers !== undefined) {
                copy.headers = new Headers(copy.headers);
            }
            var target = input;
            if (typeof Request !== 'undefined' && input instanceof Request) {
                target = input.clone();
            }
            return { input: target, init: copy };
        }

        function send(input, init) {
            var prepared = prepare(input, init);
            return rawFetch(prepared.input, prepared.init);
        }

        function probe(path) {
            return rawFetch(path, { method: 'GET', credentials: 'same-origin' });
        }

        function withLock(task) {
            if (locks && typeof locks.request === 'function') {
                return locks.request(LOCK_NAME, function () {
                    return task();
                });
            }
            return task();
        }

        // XSRF-TOKEN은 API 응답에서만 내려간다. 없으면 /api/auth/me로 한 번 받아 본다. 그래도 없으면 실패한다.
        function csrfToken() {
            var token = readCookie(CSRF_COOKIE);
            if (token) {
                return Promise.resolve(token);
            }
            return probe(PATH_ME).then(function () {
                return readCookie(CSRF_COOKIE);
            });
        }

        function callRefresh() {
            return csrfToken().then(function (token) {
                if (!token) {
                    return { kind: 'failed', reason: 'no-csrf' };
                }
                var headers = {};
                headers[CSRF_HEADER] = token;
                return rawFetch(PATH_REFRESH, {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: headers
                }).then(function (response) {
                    if (response.status === 200) {
                        generation++;
                        return { kind: 'ready' };
                    }
                    if (response.status === 401) {
                        return { kind: 'reauth' };
                    }
                    if (response.status === 429) {
                        return { kind: 'rate-limited', response: response };
                    }
                    return { kind: 'failed', reason: 'refresh-' + response.status };
                });
            });
        }

        // 잠금 안에서 다른 탭이 이미 갱신했는지 확인한다. 갱신이 필요할 때만 Refresh를 호출한다.
        // 같은 탭에서는 복구가 한 번에 하나만 진행되므로(inflight), 잠금을 기다리는 동안 세대가 바뀌지 않는다.
        function runRecovery() {
            return withLock(function () {
                return probe(PATH_ME).then(function (me) {
                    if (me.status === 200) {
                        return { kind: 'ready' };
                    }
                    if (me.status !== 401) {
                        return { kind: 'failed', reason: 'me-' + me.status };
                    }
                    return callRefresh();
                });
            });
        }

        // 같은 탭의 동시 401은 진행 중인 복구 하나를 공유한다.
        function recoverOnce(sentGeneration) {
            if (inflight) {
                return inflight;
            }
            if (generation > sentGeneration) {
                return Promise.resolve({ kind: 'ready' });
            }
            inflight = runRecovery().then(function (result) {
                inflight = null;
                return result;
            }, function () {
                inflight = null;
                return { kind: 'failed', reason: 'network' };
            });
            return inflight;
        }

        function request(input, init) {
            var url = toUrl(input);
            if (!isSameOriginApi(url)) {
                // 외부 출처와 /api 밖의 요청은 처리하지 않는다. 입력과 옵션을 그대로 넘긴다.
                return rawFetch(input, init);
            }
            if (isUnmanaged(url)) {
                return send(input, init);
            }

            var sentGeneration = generation;
            return send(input, init).then(function (first) {
                if (first.status !== 401) {
                    return first;
                }
                var replayable = !isStreamBody(init);
                return recoverOnce(sentGeneration).then(function (outcome) {
                    if (outcome.kind === 'rate-limited') {
                        return outcome.response;
                    }
                    if (outcome.kind === 'reauth') {
                        emit(EVENT_REAUTH, 'refresh-rejected');
                        return first;
                    }
                    if (outcome.kind !== 'ready') {
                        emit(EVENT_REFRESH_FAILED, outcome.reason);
                        return first;
                    }
                    if (!replayable) {
                        // 한 번만 읽을 수 있는 본문은 다시 보낼 수 없으므로 재시도하지 않는다.
                        return first;
                    }
                    return send(input, init).then(function (retried) {
                        if (retried.status === 401) {
                            emit(EVENT_REAUTH, 'retry-unauthorized');
                        }
                        return retried;
                    });
                });
            });
        }

        return {
            request: request,
            fetch: request,
            REAUTH_EVENT: EVENT_REAUTH,
            REFRESH_FAILED_EVENT: EVENT_REFRESH_FAILED,
            LOCK_NAME: LOCK_NAME
        };
    }

    var api = { createApiClient: createApiClient };

    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    } else if (root && root.document) {
        root.BusanApi = createApiClient({
            fetch: root.fetch.bind(root),
            document: root.document,
            eventTarget: root.document,
            locks: root.navigator && root.navigator.locks ? root.navigator.locks : null,
            origin: root.location.origin
        });
    }
})(typeof window !== 'undefined' ? window : (typeof globalThis !== 'undefined' ? globalThis : this));
