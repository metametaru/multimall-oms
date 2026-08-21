/*
 * オペレーター画面。受注の一覧と操作、在庫の確認を行う。
 *
 * Why not: フロントエンドのフレームワークとビルド環境を持ち込まない。この画面の目的は
 * 主要フローを実際に触れるようにすることであり、画面そのものは成果物ではない。
 * ビルド工程を足すと、リポジトリを開いた人が動かすまでの手数が増える。
 *
 * Why not: 業務ルールをこちら側に持たない。「NEWなら確認とキャンセル」という対応表を
 * 画面が持つと遷移ルールが二重化し、遷移を1本追加したときに画面だけ古いまま残る。
 * サーバーが返す availableActions をそのまま描く。
 */
(() => {
    'use strict';

    /**
     * Why not: トークンを localStorage や Cookie に保存しない。保存先を作ると、
     * XSS を踏んだときに持ち出される対象になる。再読み込みでログインし直しになるが、
     * デモの手数としては許容できる。
     */
    let session = null;

    const el = (id) => document.getElementById(id);

    const dom = {
        loginPanel: el('login-panel'),
        loginForm: el('login-form'),
        username: el('username'),
        password: el('password'),
        work: el('work'),
        who: el('who'),
        whoName: el('who-name'),
        whoRole: el('who-role'),
        logout: el('logout'),
        filterStatus: el('filter-status'),
        filterMall: el('filter-mall'),
        reload: el('reload'),
        orders: el('orders'),
        ordersEmpty: el('orders-empty'),
        stocks: el('stocks'),
        toast: el('toast'),
    };

    // --- API 呼び出し ---

    /** 認証切れは呼び出し側で分岐させず、ログイン画面に戻して終わらせる */
    class Unauthorized extends Error {
    }

    async function callApi(path, options = {}) {
        const headers = {...(options.headers || {})};
        if (session) {
            headers['Authorization'] = `Bearer ${session.token}`;
        }

        const response = await fetch(path, {...options, headers});

        if (response.status === 401 && session) {
            logout();
            throw new Unauthorized('セッションの有効期限が切れました。ログインし直してください');
        }
        if (!response.ok) {
            throw new Error(await errorMessageOf(response));
        }
        return response.status === 204 ? null : response.json();
    }

    /** サーバーは ProblemDetail で理由を返す。読める理由があるならそれを使う */
    async function errorMessageOf(response) {
        try {
            const problem = await response.json();
            return problem.detail || problem.title || `エラーが発生しました (${response.status})`;
        } catch {
            return `エラーが発生しました (${response.status})`;
        }
    }

    function postJson(path, body) {
        return callApi(path, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body),
        });
    }

    // --- 認証 ---

    async function login(username, password) {
        const issued = await postJson('/api/auth/login', {username, password});
        session = {token: issued.accessToken, username, role: issued.role};

        dom.whoName.textContent = username;
        dom.whoRole.textContent = issued.role;
        dom.who.hidden = false;
        dom.loginPanel.hidden = true;
        dom.work.hidden = false;

        await loadStatusOptions();
        await refresh();
    }

    /** 絞り込みの選択肢はサーバーから受け取る。画面はステータスの一覧を知らない */
    async function loadStatusOptions() {
        const statuses = await callApi('/api/orders/statuses');
        const options = statuses.map((status) => {
            const option = document.createElement('option');
            option.value = status.value;
            option.textContent = status.label;
            return option;
        });
        dom.filterStatus.replaceChildren(dom.filterStatus.options[0], ...options);
    }

    function logout() {
        session = null;
        dom.who.hidden = true;
        dom.work.hidden = true;
        dom.loginPanel.hidden = false;
        dom.orders.replaceChildren();
        dom.stocks.replaceChildren();
        dom.filterStatus.value = '';
    }

    // --- 描画 ---

    async function refresh() {
        const [orders, stocks] = await Promise.all([fetchOrders(), callApi('/api/stocks')]);
        renderOrders(orders);
        renderStocks(stocks);
    }

    function fetchOrders() {
        const params = new URLSearchParams();
        if (dom.filterStatus.value) {
            params.set('status', dom.filterStatus.value);
        }
        if (dom.filterMall.value) {
            params.set('mallCode', dom.filterMall.value);
        }
        const query = params.toString();
        return callApi(query ? `/api/orders?${query}` : '/api/orders');
    }

    function renderOrders(page) {
        dom.orders.replaceChildren(...page.orders.map(orderRow));
        dom.ordersEmpty.hidden = page.orders.length > 0;
    }

    function orderRow(order) {
        const row = document.createElement('tr');
        // Why: 行と数値に識別子を付ける。E2Eテストが列の並びや見た目のためのクラスを
        // 頼りにすると、体裁を整えただけでテストが落ちる
        row.dataset.orderId = order.id;
        row.append(
            cell(order.id),
            cell(order.mallCode),
            cell(order.mallOrderNumber),
            cell(order.customerName),
            cell(formatAmount(order.totalAmount), 'num'),
            statusCell(order),
            actionCell(order),
        );
        return row;
    }

    function actionCell(order) {
        const td = document.createElement('td');
        const box = document.createElement('div');
        box.className = 'actions';

        // 参照権限しか無い場合、サーバーは操作を 403 で弾く。
        // ボタンを出さないのは防御ではなく、押せない操作を見せないため
        const operable = session.role === 'OPERATOR';

        if (order.availableActions.length === 0) {
            box.append(text('span', '—', 'none'));
        } else if (!operable) {
            box.append(text('span', '参照のみ', 'none'));
        } else {
            order.availableActions.forEach((action) => box.append(actionButton(order, action)));
        }

        td.append(box);
        return td;
    }

    function actionButton(order, action) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = action.label;
        button.className = action.operation === 'cancellation' ? 'quiet' : '';
        button.addEventListener('click', () => runAction(order, action, button));
        return button;
    }

    async function runAction(order, action, button) {
        button.disabled = true;
        try {
            // 表示していた版を送る。他の誰かが先に更新していれば 409 で弾かれる
            const updated = await postJson(
                `/api/orders/${order.id}/${action.operation}`, {version: order.version});
            await refresh();
            notify(`受注${order.id} を「${updated.statusLabel}」にしました`, 'ok');
        } catch (error) {
            handle(error);
            button.disabled = false;
        }
    }

    function renderStocks(stocks) {
        dom.stocks.replaceChildren(...stocks.map((stock) => {
            const row = document.createElement('tr');
            row.dataset.productCode = stock.productCode;
            row.append(
                cell(stock.productCode),
                cell(stock.quantityOnHand, 'num on-hand'),
                cell(stock.quantityAllocated, 'num allocated'),
                cell(stock.availableQuantity, 'num available'),
            );
            return row;
        }));
    }

    // --- 小物 ---

    function cell(value, className = '') {
        return text('td', value, className);
    }

    /**
     * 表示は日本語、値は enum 名のまま。data-status に enum 名を残すのは、
     * 見た目の出し分け(CSS)が表示名の言い回しに引きずられないようにするため
     */
    function statusCell(order) {
        const td = document.createElement('td');
        const badge = text('span', order.statusLabel, 'status');
        badge.dataset.status = order.status;
        td.append(badge);
        return td;
    }

    function text(tag, value, className = '') {
        const node = document.createElement(tag);
        node.textContent = String(value);
        if (className) {
            node.className = className;
        }
        return node;
    }

    function formatAmount(amount) {
        return Number(amount).toLocaleString('ja-JP');
    }

    let toastTimer = null;

    function notify(message, kind = 'ok') {
        // hidden のままだと支援技術の対象外で、内容を変えても読み上げられない。
        // 表示してから文言を入れる
        dom.toast.dataset.kind = kind;
        dom.toast.hidden = false;
        dom.toast.textContent = message;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => {
            dom.toast.hidden = true;
        }, 4000);
    }

    function handle(error) {
        notify(error.message, 'error');
    }

    // --- 起動 ---

    dom.loginForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        try {
            await login(dom.username.value, dom.password.value);
        } catch (error) {
            handle(error);
        }
    });

    dom.logout.addEventListener('click', logout);
    dom.reload.addEventListener('click', () => refresh().catch(handle));
    dom.filterStatus.addEventListener('change', () => refresh().catch(handle));
    dom.filterMall.addEventListener('change', () => refresh().catch(handle));
})();
