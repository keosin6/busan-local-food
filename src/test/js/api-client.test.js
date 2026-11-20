'use strict';

/*
 * API 클라이언트의 Access Token 자동 갱신(T13) 단위 테스트.
 * 실행: node --test src/test/js/
 * Node 내장 테스트 러너만 쓴다. 새 의존성은 없다.
 * 실제 쿠키, Web Locks, 여러 탭은 브라우저에서 따로 검증한다(PR 본문과 PROGRESS 참고).
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const { createApiClient } = require('../../main/resources/static/js/api-client.js');

const ORIGIN = 'http://localhost:8080';

function resp(status, headers) {
    return new Response(status === 204 ? null : '', { status, headers: headers || {} });
}

/** 가짜 서버. route(path, method, init)가 Response를 돌려준다. 모든 호출을 기록한다. */
function recorder(route) {
    const calls = [];
    const fetchFn = async (input, init) => {
        const options = init || {};
        const href = typeof input === 'string' ? input : input.url;
        const url = new URL(href, ORIGIN);
        const method = (options.method || 'GET').toUpperCase();
        calls.push({ path: url.pathname, href, method, initRef: init, headers: options.headers,
            body: options.body, credentials: options.credentials,
            inputCredentials: typeof input === 'string' ? undefined : input.credentials,
            inputBodyIsStream: typeof input === 'string' ? false : input.body instanceof ReadableStream,
            inputRef: typeof input === 'string' ? undefined : input });
        return route(url.pathname, method, options, calls);
    };
    return {
        fetchFn,
        calls,
        count(path, method) {
            return calls.filter((c) => c.path === path && (!method || c.method === method)).length;
        }
    };
}

/** 직렬화된 잠금. Web Locks의 배타 잠금과 같은 순서로 작업을 실행한다. */
function fakeLocks() {
    let chain = Promise.resolve();
    return {
        request(name, callback) {
            const run = chain.then(() => callback({ name }));
            chain = run.then(() => undefined, () => undefined);
            return run;
        }
    };
}

/** XSRF-TOKEN 쿠키만 있는 문서. 쓰기 시도를 기록한다. */
function fakeDocument(cookie) {
    const writes = [];
    const doc = {
        _cookie: cookie === undefined ? 'XSRF-TOKEN=tok123' : cookie,
        get cookie() { return this._cookie; },
        set cookie(value) { writes.push(value); }
    };
    return { doc, writes };
}

function makeTab({ fetchFn, locks, doc }) {
    const events = [];
    const eventTarget = {
        dispatchEvent(event) {
            events.push({ type: event.type, detail: event.detail });
            return true;
        }
    };
    const client = createApiClient({ fetch: fetchFn, document: doc, eventTarget, locks, origin: ORIGIN });
    return { client, events };
}

/** 가짜 서버 상태: accessValid가 false면 보호 API와 me는 401이다. refresh는 설정된 응답을 준다. */
function serverRoute(state) {
    return (path, method) => {
        if (path === '/api/x') {
            return state.apiStatus ? state.apiStatus() : (state.accessValid ? resp(200) : resp(401));
        }
        if (path === '/api/auth/me') {
            return state.accessValid ? resp(200) : resp(401);
        }
        if (path === '/api/auth/refresh') {
            const result = state.refreshResult ? state.refreshResult() : resp(200);
            if (result.status === 200) {
                state.accessValid = true;
            }
            return result;
        }
        return resp(404);
    };
}

test('1. 401이면 Refresh를 한 번 호출하고 원래 요청을 한 번 재시도한다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client, events } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 200);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1);
    assert.equal(server.count('/api/x'), 2);
    assert.deepEqual(events, []);
});

test('2. 재시도도 401이면 추가 Refresh 없이 재로그인 이벤트를 낸다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200), apiStatus: () => resp(401) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client, events } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 401);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1);
    assert.equal(server.count('/api/x'), 2);
    assert.deepEqual(events, [{ type: 'auth:reauth-required', detail: { reason: 'retry-unauthorized' } }]);
});

test('3. 같은 탭의 동시 401은 Refresh 요청을 하나로 합친다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const results = await Promise.all([1, 2, 3, 4, 5].map(() => client.request('/api/x')));

    assert.deepEqual(results.map((r) => r.status), [200, 200, 200, 200, 200]);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1);
    assert.equal(server.count('/api/auth/me'), 1);
    // 각 요청은 최초 한 번 + 재시도 한 번씩이다.
    assert.equal(server.count('/api/x'), 10);
});

test('3b. 갱신이 끝난 뒤 늦게 도착한 401은 Refresh 없이 재시도한다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    let xCalls = 0;
    let releaseLate;
    const late = new Promise((resolve) => { releaseLate = resolve; });
    const route = (path, method, init, calls) => {
        if (path === '/api/x') {
            xCalls += 1;
            if (xCalls === 1) return resp(401);          // 요청 A의 첫 시도
            if (xCalls === 2) return late.then(() => resp(401)); // 요청 B의 첫 시도는 늦게 도착한다
            return resp(state.accessValid ? 200 : 401);
        }
        return serverRoute(state)(path, method, init, calls);
    };
    const server = recorder(route);
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const requestA = client.request('/api/x');
    const requestB = client.request('/api/x');
    const resultA = await requestA;
    assert.equal(resultA.status, 200);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1);

    releaseLate();
    const resultB = await requestB;
    assert.equal(resultB.status, 200);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1, '늦은 401이 추가 갱신을 만들면 안 된다');
    assert.equal(server.count('/api/auth/me'), 1);
});

test('4. Web Lock 안에서 me가 200이면 다른 탭이 갱신한 것이므로 Refresh를 호출하지 않는다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const locks = fakeLocks();

    // 다른 탭이 먼저 갱신했다고 가정한다.
    state.accessValid = true;
    const firstCall = [];
    const apiStatus = () => {
        firstCall.push(true);
        return firstCall.length === 1 ? resp(401) : resp(200);
    };
    state.apiStatus = apiStatus;
    const { client } = makeTab({ fetchFn: server.fetchFn, locks, doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 200);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 0);
    assert.equal(server.count('/api/auth/me'), 1);
});

test('5. Web Lock 안에서 me가 401이면 Refresh를 호출한다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 200);
    assert.equal(server.count('/api/auth/me'), 1);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1);
});

test('7. 여러 탭이 동시에 401을 받아도 Refresh는 한 번만 호출된다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const locks = fakeLocks();
    const tabs = [1, 2, 3].map(() => {
        const { doc } = fakeDocument();
        return makeTab({ fetchFn: server.fetchFn, locks, doc });
    });

    const results = await Promise.all(tabs.map(({ client }) => client.request('/api/x')));

    assert.deepEqual(results.map((r) => r.status), [200, 200, 200]);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1);
});

test('8. Refresh가 401이면 재로그인 이벤트를 내고 원래 401을 돌려준다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(401) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client, events } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 401);
    assert.equal(server.count('/api/x'), 1);
    assert.deepEqual(events, [{ type: 'auth:reauth-required', detail: { reason: 'refresh-rejected' } }]);
});

test('9. Refresh가 429이면 로그아웃·재로그인 이벤트 없이 429와 Retry-After를 돌려준다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(429, { 'Retry-After': '42' }) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client, events } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 429);
    assert.equal(response.headers.get('Retry-After'), '42');
    assert.equal(server.count('/api/x'), 1);
    assert.deepEqual(events, []);
});

test('10. Refresh가 403이면 인증 만료로 단정하지 않고 재로그인 이벤트를 내지 않는다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(403) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client, events } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 401);
    assert.equal(server.count('/api/x'), 1);
    assert.deepEqual(events, [{ type: 'auth:refresh-failed', detail: { reason: 'refresh-403' } }]);
});

test('11. 네트워크 오류와 5xx에서는 재시도하지 않고 인증 상태를 바꾸지 않는다', async () => {
    const networkServer = recorder((path) => {
        if (path === '/api/x') return resp(401);
        if (path === '/api/auth/me') return resp(401);
        return resp(500);
    });
    const failingRefresh = recorder((path, method) => {
        if (path === '/api/x') return resp(401);
        if (path === '/api/auth/me') return resp(401);
        if (path === '/api/auth/refresh') throw new TypeError('network down');
        return resp(404);
    });
    const { doc } = fakeDocument();

    const network = makeTab({ fetchFn: failingRefresh.fetchFn, locks: fakeLocks(), doc });
    const first = await network.client.request('/api/x');
    assert.equal(first.status, 401);
    assert.equal(failingRefresh.count('/api/auth/refresh', 'POST'), 1);
    assert.equal(failingRefresh.count('/api/x'), 1);
    assert.deepEqual(network.events, [{ type: 'auth:refresh-failed', detail: { reason: 'network' } }]);

    const fiveXx = makeTab({ fetchFn: networkServer.fetchFn, locks: fakeLocks(), doc });
    const second = await fiveXx.client.request('/api/x');
    assert.equal(second.status, 401);
    assert.equal(networkServer.count('/api/auth/refresh', 'POST'), 1);
    assert.deepEqual(fiveXx.events, [{ type: 'auth:refresh-failed', detail: { reason: 'refresh-500' } }]);
});

test('12. 재시도할 때 HTTP 메서드, 헤더, 본문이 그대로 보존된다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });
    const headers = { 'Content-Type': 'application/json', 'X-Custom': 'keep-me' };

    await client.request('/api/x', { method: 'POST', headers, body: '{"a":1}' });

    const attempts = server.calls.filter((c) => c.path === '/api/x');
    assert.equal(attempts.length, 2);
    for (const attempt of attempts) {
        assert.equal(attempt.method, 'POST');
        assert.equal(attempt.body, '{"a":1}');
        assert.equal(attempt.headers.get('Content-Type'), 'application/json');
        assert.equal(attempt.headers.get('X-Custom'), 'keep-me');
        assert.equal(attempt.credentials, 'same-origin');
    }
    // 호출자의 옵션 객체는 바뀌지 않는다.
    assert.deepEqual(Object.keys(headers).sort(), ['Content-Type', 'X-Custom']);
});

test('13. 외부 출처 요청은 입력과 옵션을 그대로 넘기고 처리하지 않는다', async () => {
    const server = recorder(() => resp(401));
    const { doc } = fakeDocument();
    const { client, events } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });
    const init = { method: 'GET' };

    const response = await client.request('https://other.example.com/api/x', init);

    assert.equal(response.status, 401);
    assert.equal(server.calls.length, 1);
    assert.equal(server.calls[0].initRef, init);
    assert.equal(server.calls[0].credentials, undefined);
    assert.equal(server.count('/api/auth/refresh'), 0);
    assert.deepEqual(events, []);
});

test('14. 가입, 로그인, 재발급, 로그아웃 요청은 자동 갱신 대상에서 제외된다', async () => {
    const server = recorder(() => resp(401));
    const { doc } = fakeDocument();
    const { client, events } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    for (const path of ['/api/auth/logout', '/api/auth/login', '/api/auth/signup', '/api/auth/refresh']) {
        const response = await client.request(path, { method: 'POST' });
        assert.equal(response.status, 401, path);
    }

    assert.equal(server.count('/api/auth/me'), 0);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1, '직접 호출한 재발급 한 번만 나간다');
    assert.deepEqual(events, []);
});

test('15. 복구 과정에서 쿠키를 쓰거나 지우지 않는다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc, writes } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    await client.request('/api/x');

    assert.deepEqual(writes, []);
});

test('Refresh 요청은 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument('XSRF-TOKEN=tok123');
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    await client.request('/api/x');

    const refresh = server.calls.find((c) => c.path === '/api/auth/refresh');
    assert.equal(refresh.headers['X-XSRF-TOKEN'], 'tok123');
});

test('CSRF 쿠키가 없으면 임의 토큰을 만들지 않고 Refresh 없이 실패한다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument('');
    const { client, events } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 401);
    assert.equal(server.count('/api/auth/refresh'), 0);
    assert.deepEqual(events, [{ type: 'auth:refresh-failed', detail: { reason: 'no-csrf' } }]);
});

test('이벤트와 호출 기록에는 토큰 값이 들어가지 않는다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(401) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument('XSRF-TOKEN=tok123');
    const { client, events } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    await client.request('/api/x');

    assert.equal(JSON.stringify(events).includes('tok123'), false);
    assert.equal(JSON.stringify(events).includes('Bearer'), false);
});

test('스트림 본문은 한 번만 보낼 수 있으므로 재시도하지 않는다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });
    const body = new ReadableStream({
        start(controller) {
            controller.enqueue(new TextEncoder().encode('payload'));
            controller.close();
        }
    });

    const response = await client.request('/api/x', { method: 'POST', body });

    assert.equal(response.status, 401);
    assert.equal(server.count('/api/x'), 1);
});

test('Web Locks가 없으면 같은 탭 공유만 적용되고 예외 없이 동작한다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: null, doc });

    const results = await Promise.all([1, 2].map(() => client.request('/api/x')));

    assert.deepEqual(results.map((r) => r.status), [200, 200]);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1);
});

// ---- PR #32 보완: Request credentials 보존과 Request 본문 스트림 재시도 방지 ----

function streamBody() {
    return new ReadableStream({
        start(controller) {
            controller.enqueue(new TextEncoder().encode('payload'));
            controller.close();
        }
    });
}

test('보완 1-a. Request 입력의 credentials include는 재시도에서도 유지된다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });
    const request = new Request(ORIGIN + '/api/x', { credentials: 'include' });

    const response = await client.request(request);

    assert.equal(response.status, 200);
    const attempts = server.calls.filter((c) => c.path === '/api/x');
    assert.equal(attempts.length, 2);
    for (const attempt of attempts) {
        assert.equal(attempt.inputCredentials, 'include');
        assert.equal(attempt.credentials, undefined, 'init에 기본값을 끼워 넣지 않는다');
    }
    assert.equal(request.credentials, 'include', '원본 Request는 바뀌지 않는다');
});

test('보완 1-b. init에 credentials를 명시하면 Request 입력보다 우선한다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });
    const request = new Request(ORIGIN + '/api/x', { credentials: 'include' });
    const init = { credentials: 'omit' };

    await client.request(request, init);

    const attempts = server.calls.filter((c) => c.path === '/api/x');
    assert.equal(attempts.length, 2);
    for (const attempt of attempts) {
        assert.equal(attempt.credentials, 'omit');
    }
    assert.deepEqual(init, { credentials: 'omit' }, '원본 init 객체는 바뀌지 않는다');
});

test('보완 1-c. 문자열 입력에 init credentials가 없으면 same-origin 기본값이 재시도에서도 적용된다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    await client.request('/api/x', { method: 'GET' });

    const attempts = server.calls.filter((c) => c.path === '/api/x');
    assert.equal(attempts.length, 2);
    for (const attempt of attempts) {
        assert.equal(attempt.credentials, 'same-origin');
    }
});

test('보완 2-c. Request 객체에 본문이 있으면 문자열이어도 재사용을 가정하지 않고 재시도하지 않는다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });
    const request = new Request(ORIGIN + '/api/x', {
        method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: 'hello'
    });

    const response = await client.request(request);

    assert.equal(response.status, 401);
    assert.equal(server.count('/api/x'), 1);
});

test('보완 1-d. 본문이 없는 Request 객체는 재시도에서도 method와 헤더가 보존된다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(200) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });
    const request = new Request(ORIGIN + '/api/x', { method: 'PUT', headers: { 'X-Custom': 'keep' } });

    const response = await client.request(request);

    assert.equal(response.status, 200);
    const attempts = server.calls.filter((c) => c.path === '/api/x');
    assert.equal(attempts.length, 2);
    for (const attempt of attempts) {
        assert.equal(attempt.inputRef.method, 'PUT');
        assert.equal(attempt.inputRef.headers.get('X-Custom'), 'keep');
    }
});

for (const refreshStatus of [200, 401]) {
    test(`보완 2-a. init.body가 스트림이면 재발급 ${refreshStatus}과 관계없이 원래 요청은 한 번만 나간다`, async () => {
        const state = { accessValid: false, refreshResult: () => resp(refreshStatus) };
        const server = recorder(serverRoute(state));
        const { doc } = fakeDocument();
        const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

        const response = await client.request('/api/x', { method: 'POST', body: streamBody() });

        assert.equal(response.status, 401);
        assert.equal(server.count('/api/x'), 1);
    });

    test(`보완 2-b. Request 입력의 body가 스트림이면 재발급 ${refreshStatus}과 관계없이 원래 요청은 한 번만 나간다`, async () => {
        const state = { accessValid: false, refreshResult: () => resp(refreshStatus) };
        const server = recorder(serverRoute(state));
        const { doc } = fakeDocument();
        const { client } = makeTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });
        const request = new Request(ORIGIN + '/api/x', { method: 'POST', body: streamBody(), duplex: 'half' });

        const response = await client.request(request);

        assert.equal(response.status, 401);
        assert.equal(server.count('/api/x'), 1);
    });
}

// ---- T20: 프런트엔드 429 처리 ----
// 429는 인증 만료가 아니다. 쿠키, 세션, 이벤트에 영향을 주지 않고, 자동 재요청도 하지 않는다.
// 안내는 onRateLimited 콜백으로 받는다. 기존 테스트의 이벤트 목록과 분리하기 위해서다.

function makeNotifyingTab({ fetchFn, locks, doc, onRateLimitedThrows }) {
    const events = [];
    const notices = [];
    const eventTarget = {
        dispatchEvent(event) {
            events.push({ type: event.type, detail: event.detail });
            return true;
        }
    };
    const client = createApiClient({
        fetch: fetchFn,
        document: doc,
        eventTarget,
        locks,
        origin: ORIGIN,
        onRateLimited(info) {
            notices.push(info);
            if (onRateLimitedThrows) {
                throw new Error('handler failure');
            }
        }
    });
    return { client, events, notices };
}

const AUTH_EVENTS = ['auth:reauth-required', 'auth:refresh-failed'];

test('T20-1. 일반 API 429는 안내 콜백 한 번과 Retry-After 초 값을 준다. 응답은 그대로 돌려주고 재요청하지 않는다', async () => {
    const server = recorder((path) => (path === '/api/x' ? resp(429, { 'Retry-After': '42' }) : resp(404)));
    const { doc, writes } = fakeDocument();
    const { client, events, notices } = makeNotifyingTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 429);
    assert.equal(response.headers.get('Retry-After'), '42');
    assert.deepEqual(notices, [{ retryAfterSeconds: 42 }]);
    assert.equal(server.count('/api/x'), 1, '자동 재요청이 없어야 한다');
    assert.equal(server.count('/api/auth/refresh'), 0, '429는 재발급 대상이 아니다');
    assert.deepEqual(events, [], '인증 이벤트가 없어야 한다');
    assert.deepEqual(writes, [], '쿠키를 쓰지 않는다');
});

test('T20-2. 제외 경로인 로그인의 429도 안내하고 재발급하지 않는다', async () => {
    const server = recorder((path) => (path === '/api/auth/login' ? resp(429, { 'Retry-After': '900' }) : resp(404)));
    const { doc } = fakeDocument();
    const { client, events, notices } = makeNotifyingTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/auth/login', { method: 'POST', body: '{}' });

    assert.equal(response.status, 429);
    assert.deepEqual(notices, [{ retryAfterSeconds: 900 }]);
    assert.equal(server.count('/api/auth/login', 'POST'), 1);
    assert.equal(server.count('/api/auth/refresh'), 0);
    assert.deepEqual(events, []);
});

test('T20-3. Retry-After 누락과 잘못된 값은 null로 보고 날짜 형식도 해석하지 않는다', async () => {
    const cases = [
        [undefined, null],
        ['', null],
        ['abc', null],
        ['-5', null],
        ['1.5', null],
        ['Wed, 21 Oct 2015 07:28:00 GMT', null],
        ['0', 0],
        ['  60 ', 60]
    ];
    for (const [header, expected] of cases) {
        const headers = header === undefined ? {} : { 'Retry-After': header };
        const server = recorder(() => resp(429, headers));
        const { doc } = fakeDocument();
        const { client, notices } = makeNotifyingTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

        const response = await client.request('/api/x');

        assert.equal(response.status, 429, String(header));
        assert.deepEqual(notices, [{ retryAfterSeconds: expected }], String(header));
    }
});

test('T20-4. 재발급 429도 한 번 알리고, 쿠키와 인증 이벤트를 바꾸지 않는다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(429, { 'Retry-After': '42' }) };
    const server = recorder(serverRoute(state));
    const { doc, writes } = fakeDocument();
    const { client, events, notices } = makeNotifyingTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 429);
    assert.equal(response.headers.get('Retry-After'), '42');
    assert.deepEqual(notices, [{ retryAfterSeconds: 42 }]);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1);
    assert.equal(server.count('/api/x'), 1);
    assert.deepEqual(events.filter((e) => AUTH_EVENTS.includes(e.type)), []);
    assert.deepEqual(writes, []);
});

test('T20-5. 같은 재발급 429 응답을 공유하는 동시 요청 다섯 개는 안내를 한 번만 보낸다', async () => {
    const state = { accessValid: false, refreshResult: () => resp(429, { 'Retry-After': '30' }) };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client, events, notices } = makeNotifyingTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const responses = await Promise.all([1, 2, 3, 4, 5].map(() => client.request('/api/x')));

    assert.deepEqual(responses.map((r) => r.status), [429, 429, 429, 429, 429]);
    assert.deepEqual(notices, [{ retryAfterSeconds: 30 }]);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1);
    assert.deepEqual(events.filter((e) => AUTH_EVENTS.includes(e.type)), []);
});

test('T20-6. 재발급 성공 뒤 재시도가 429면 안내하고, 재로그인 이벤트 없이 재시도는 한 번만 한다', async () => {
    let attempts = 0;
    const state = {
        accessValid: false,
        refreshResult: () => resp(200),
        apiStatus: () => {
            attempts += 1;
            return attempts === 1 ? resp(401) : resp(429, { 'Retry-After': '12' });
        }
    };
    const server = recorder(serverRoute(state));
    const { doc } = fakeDocument();
    const { client, events, notices } = makeNotifyingTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('/api/x');

    assert.equal(response.status, 429);
    assert.deepEqual(notices, [{ retryAfterSeconds: 12 }]);
    assert.equal(server.count('/api/auth/refresh', 'POST'), 1);
    assert.equal(server.count('/api/x'), 2);
    assert.deepEqual(events.filter((e) => AUTH_EVENTS.includes(e.type)), []);
});

test('T20-7. 외부 출처의 429는 이 클라이언트가 알리지 않는다', async () => {
    const server = recorder(() => resp(429, { 'Retry-After': '5' }));
    const { doc } = fakeDocument();
    const { client, notices } = makeNotifyingTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    const response = await client.request('https://other.example.com/api/x');

    assert.equal(response.status, 429);
    assert.deepEqual(notices, []);
});

test('T20-8. 안내 콜백이 실패해도 429 응답은 호출자에게 그대로 돌아간다', async () => {
    const server = recorder(() => resp(429, { 'Retry-After': '7' }));
    const { doc } = fakeDocument();
    const { client, notices } = makeNotifyingTab({
        fetchFn: server.fetchFn, locks: fakeLocks(), doc, onRateLimitedThrows: true
    });

    const response = await client.request('/api/x');

    assert.equal(response.status, 429);
    assert.equal(notices.length, 1);
});

test('T20-9. 429 뒤에도 시간이 지나 자동으로 다시 보내지 않는다', async () => {
    const server = recorder(() => resp(429, { 'Retry-After': '1' }));
    const { doc } = fakeDocument();
    const { client, notices } = makeNotifyingTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    await client.request('/api/x');
    await new Promise((resolve) => setTimeout(resolve, 60));

    assert.equal(server.count('/api/x'), 1);
    assert.equal(notices.length, 1);
});

test('T20-10. 서로 다른 429 응답은 요청마다 한 번씩 알린다. 화면 쪽에서 한 줄로 합친다', async () => {
    const server = recorder(() => resp(429, { 'Retry-After': '9' }));
    const { doc } = fakeDocument();
    const { client, notices } = makeNotifyingTab({ fetchFn: server.fetchFn, locks: fakeLocks(), doc });

    await client.request('/api/x');
    await client.request('/api/x');

    assert.equal(notices.length, 2);
    assert.deepEqual(notices[0], { retryAfterSeconds: 9 });
});
