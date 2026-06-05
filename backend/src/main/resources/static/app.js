const ACCESS_TOKEN_KEY  = "jagent.accessToken";
const REFRESH_TOKEN_KEY = "jagent.refreshToken";

let accessToken  = "";
let refreshToken = "";
let currentMode  = "login";

const loginSection     = document.getElementById("loginSection");
const registerSection  = document.getElementById("registerSection");
const switchModeButton = document.getElementById("switchModeButton");
const cardKicker       = document.getElementById("cardKicker");
const cardTitle        = document.getElementById("cardTitle");
const formHint         = document.getElementById("formHint");
const sendCodeButton   = document.getElementById("sendCodeButton");

function setHint(msg, type) {
  formHint.textContent = msg;
  formHint.className   = "form-hint " + (type || "");
  if (!msg) formHint.style.display = "none";
}

function renderMode(mode) {
  currentMode = mode;
  const isLogin = mode === "login";
  loginSection.classList.toggle("active", isLogin);
  registerSection.classList.toggle("active", !isLogin);
  cardKicker.textContent = isLogin ? "Welcome Back" : "Create Account";
  cardTitle.textContent  = isLogin ? "登录账号" : "注册账号";
  switchModeButton.textContent = isLogin ? "去注册" : "已有账号";
  setHint("");
}

function saveTokens(a, r) {
  accessToken  = a || "";
  refreshToken = r || "";
  accessToken  ? localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
               : localStorage.removeItem(ACCESS_TOKEN_KEY);
  refreshToken ? localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
               : localStorage.removeItem(REFRESH_TOKEN_KEY);
}

function loadTokens() {
  accessToken  = localStorage.getItem(ACCESS_TOKEN_KEY)  || "";
  refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY) || "";
}

async function callApi(url, payload, options = {}) {
  const response = await fetch(url, {
    method:  options.method || "POST",
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    body:    payload ? JSON.stringify(payload) : undefined,
  });
  return response.json();
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

function showSuccessAnimation(displayName, callback) {
  const overlay = document.getElementById("successOverlay");
  const nameEl  = document.getElementById("svName");
  nameEl.innerHTML = `欢迎回来，<span class="script-name">${escapeHtml(displayName || "用户")}</span>`;
  overlay.classList.remove("hide");
  overlay.classList.add("show");
  setTimeout(() => {
    overlay.classList.add("hide");
    setTimeout(callback, 560);
  }, 2400);
}

switchModeButton.addEventListener("click", () => {
  renderMode(currentMode === "login" ? "register" : "login");
});

document.getElementById("loginForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  const btn = document.getElementById("loginSubmit");
  btn.disabled = true;
  btn.textContent = "登录中…";
  setHint("");

  const data = await callApi("/api/auth/login", {
    email:    document.getElementById("loginEmail").value.trim(),
    password: document.getElementById("loginPassword").value.trim(),
  }).catch(() => null);

  btn.disabled = false;
  btn.textContent = "登录";

  if (!data) { setHint("网络错误，请重试。", "err"); return; }
  if (data.code !== 200 || !data.data?.tokenPair) {
    setHint(data.message || "登录失败，请检查邮箱和密码。", "err");
    return;
  }

  saveTokens(data.data.tokenPair.accessToken, data.data.tokenPair.refreshToken);
  showSuccessAnimation(data.data.displayName, () => {
    window.location.href = "/workspace.html";
  });
});

document.getElementById("registerForm").addEventListener("submit", async (e) => {
  e.preventDefault();
  setHint("");

  const data = await callApi("/api/auth/register", {
    displayName:      document.getElementById("registerDisplayName").value.trim(),
    email:            document.getElementById("registerEmail").value.trim(),
    verificationCode: document.getElementById("registerCode").value.trim(),
    password:         document.getElementById("registerPassword").value.trim(),
  }).catch(() => null);

  if (!data) { setHint("网络错误，请重试。", "err"); return; }
  if (data.code !== 200) { setHint(data.message || "注册失败。", "err"); return; }

  setHint("注册成功，请登录。", "ok");
  document.getElementById("loginEmail").value =
    document.getElementById("registerEmail").value.trim();
  renderMode("login");
});

sendCodeButton.addEventListener("click", async () => {
  const email = document.getElementById("registerEmail").value.trim();
  if (!email) { setHint("请先填写邮箱。", "err"); return; }

  sendCodeButton.disabled = true;
  sendCodeButton.textContent = "发送中…";

  const data = await callApi("/api/auth/register/code", { email }).catch(() => null);

  sendCodeButton.disabled = false;
  sendCodeButton.textContent = "发送";

  if (!data || data.code !== 200) {
    setHint(data?.message || "发送失败，请重试。", "err");
  } else {
    setHint("验证码已发送，请查收邮件。", "ok");
  }
});

renderMode("login");

/* Restore existing session → redirect directly */
loadTokens();
if (accessToken || refreshToken) {
  callApi("/api/auth/me", null, {
    method: "GET",
    headers: { Authorization: `Bearer ${accessToken}` },
  }).then(data => {
    if (data?.code === 200) window.location.href = "/workspace.html";
  }).catch(() => {});
}
