/*
 * 화면 로그아웃 (API_SPEC 2.11, T12 A안). 순서를 지킨다.
 * 1) POST /api/auth/logout 성공 시에만 2) 화면 폼으로 POST /logout을 보낸다.
 * API 로그아웃이 실패하면 화면 세션을 끊지 않고 재시도를 안내한다.
 * 화면 로그아웃이 실패해도 API 토큰은 이미 폐기되었으므로, 버튼을 다시 누르면 두 단계를 안전하게 반복한다.
 * 토큰 원문과 쿠키 값은 읽지 않는다. XSRF-TOKEN 쿠키 값만 헤더에 싣는다.
 */
(function () {
    'use strict';

    function readCookie(name) {
        var prefix = name + '=';
        var parts = document.cookie ? document.cookie.split('; ') : [];
        for (var i = 0; i < parts.length; i++) {
            if (parts[i].indexOf(prefix) === 0) {
                return decodeURIComponent(parts[i].substring(prefix.length));
            }
        }
        return null;
    }

    function showError(element, message) {
        element.textContent = message;
        element.classList.remove('d-none');
    }

    function hideError(element) {
        element.textContent = '';
        element.classList.add('d-none');
    }

    async function logoutApi() {
        // API 체인의 XSRF-TOKEN 쿠키는 API 응답에서만 내려간다. 인증 여부와 상관없이 응답을 받아 쿠키를 얻는다.
        await fetch('/api/auth/me', { method: 'GET', credentials: 'same-origin' });
        var token = readCookie('XSRF-TOKEN');
        if (!token) {
            return false;
        }
        var response = await fetch('/api/auth/logout', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'X-XSRF-TOKEN': token }
        });
        return response.ok;
    }

    document.addEventListener('DOMContentLoaded', function () {
        var form = document.getElementById('logout-form');
        if (!form) {
            return;
        }
        var errorElement = document.getElementById('logout-error');
        var button = form.querySelector('button[type="submit"]');

        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            button.disabled = true;
            hideError(errorElement);
            try {
                var apiLoggedOut = await logoutApi();
                if (!apiLoggedOut) {
                    // 화면 세션은 유지한다. 사용자가 다시 시도할 수 있다.
                    showError(errorElement, '로그아웃에 실패했습니다. 다시 시도해 주세요.');
                    button.disabled = false;
                    return;
                }
                // API 로그아웃 성공 뒤에만 화면 로그아웃을 요청한다 (T12).
                form.submit();
            } catch (error) {
                showError(errorElement, '로그아웃에 실패했습니다. 다시 시도해 주세요.');
                button.disabled = false;
            }
        });
    });
})();
