'use strict';

/*
 * 화면 429 안내(T20)의 표시 로직 단위 테스트. 실행: node --test src/test/js/app.test.js
 * DOM은 필요한 메서드만 흉내 낸다. 실제 Bootstrap 닫기 동작은 브라우저에서 확인한다.
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const { createRateLimitNotice, formatWait } = require('../../main/resources/static/js/app.js');

const NOTICE_ID = 'api-rate-limit-notice';
const MESSAGE_ID = 'api-rate-limit-message';

function fakeDocument() {
    const byId = {};
    const body = {
        children: [],
        appendChild(element) {
            this.children.push(element);
            if (element.attributes.id) {
                byId[element.attributes.id] = element;
            }
            return element;
        }
    };
    return {
        body,
        createElement() {
            const element = {
                attributes: {},
                children: [],
                textContent: '',
                setAttribute(name, value) {
                    this.attributes[name] = value;
                    if (name === 'id') {
                        byId[value] = this;
                    }
                },
                appendChild(child) {
                    this.children.push(child);
                    if (child.attributes.id) {
                        byId[child.attributes.id] = child;
                    }
                    return child;
                }
            };
            return element;
        },
        getElementById(id) {
            return byId[id] || null;
        },
        noticeCount() {
            return body.children.filter((el) => el.attributes.id === NOTICE_ID).length;
        }
    };
}

test('Retry-After 초 단위 값을 초 문구로 보여 준다', () => {
    assert.equal(formatWait(42), '약 42초 후에 다시 시도해 주세요.');
    assert.equal(formatWait(1), '약 1초 후에 다시 시도해 주세요.');
});

test('60초 이상은 올림한 분 문구로 보여 준다', () => {
    assert.equal(formatWait(60), '약 1분 후에 다시 시도해 주세요.');
    assert.equal(formatWait(61), '약 2분 후에 다시 시도해 주세요.');
    assert.equal(formatWait(900), '약 15분 후에 다시 시도해 주세요.');
});

test('Retry-After가 없거나 잘못되었으면 대기 시간 없는 안전한 문구를 쓴다', () => {
    for (const value of [null, undefined, 0, -3, NaN, 'abc']) {
        assert.equal(formatWait(value), '잠시 후 다시 시도해 주세요.', String(value));
    }
});

test('일반 API 429 안내가 표시되고 메시지에 대기 시간이 들어간다', () => {
    const doc = fakeDocument();
    const notice = createRateLimitNotice(doc);

    notice.show({ retryAfterSeconds: 42 });

    const message = doc.getElementById(MESSAGE_ID);
    assert.equal(message.textContent, '요청이 너무 많습니다. 약 42초 후에 다시 시도해 주세요.');
    assert.equal(doc.getElementById(NOTICE_ID).attributes.role, 'alert');
    assert.equal(doc.getElementById(NOTICE_ID).attributes['data-bs-dismiss'], undefined);
    assert.equal(doc.getElementById(NOTICE_ID).children[1].attributes['data-bs-dismiss'], 'alert');
});

test('Retry-After가 없으면 안전한 기본 문구로 안내한다', () => {
    const doc = fakeDocument();
    createRateLimitNotice(doc).show({ retryAfterSeconds: null });

    assert.equal(doc.getElementById(MESSAGE_ID).textContent, '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.');
});

test('여러 번 알려도 안내 요소는 하나만 쓰고 메시지만 갱신한다', () => {
    const doc = fakeDocument();
    const notice = createRateLimitNotice(doc);

    notice.show({ retryAfterSeconds: 42 });
    notice.show({ retryAfterSeconds: 42 });
    notice.show({ retryAfterSeconds: 900 });

    assert.equal(doc.noticeCount(), 1);
    assert.equal(doc.getElementById(MESSAGE_ID).textContent, '요청이 너무 많습니다. 약 15분 후에 다시 시도해 주세요.');
});

test('안내 표시는 document.cookie를 쓰지 않는다', () => {
    const doc = fakeDocument();
    const cookieWrites = [];
    Object.defineProperty(doc, 'cookie', { set(value) { cookieWrites.push(value); }, get() { return ''; } });

    createRateLimitNotice(doc).show({ retryAfterSeconds: 10 });

    assert.deepEqual(cookieWrites, []);
});
