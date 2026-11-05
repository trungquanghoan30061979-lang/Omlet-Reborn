const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const crypto = require("node:crypto");

initializeApp();

const RESEND_API_KEY = defineSecret("RESEND_API_KEY");
const OTP_HMAC_SECRET = defineSecret("OTP_HMAC_SECRET");
const OTP_FROM_EMAIL = defineSecret("OTP_FROM_EMAIL");

const REGION = "asia-southeast1";
const OTP_TTL_MS = 10 * 60 * 1000;
const RESEND_WAIT_MS = 60 * 1000;
const MAX_ATTEMPTS = 5;

function normalizeEmail(value) {
  return String(value || "").trim().toLowerCase();
}

function digest(value, secret) {
  return crypto.createHmac("sha256", secret).update(value).digest("hex");
}

function randomDigits() {
  return String(crypto.randomInt(0, 1000000)).padStart(6, "0");
}

function cleanUsername(value) {
  return String(value || "").trim().replace(/\s+/g, " ");
}

async function sendOtpEmail(email, code, apiKey, fromEmail) {
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      from: fromEmail,
      to: [email],
      subject: "Mã xác minh Omlet Reborn",
      html: `<div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:24px;background:#071426;color:#fff;border-radius:16px"><h2>OMLET REBORN</h2><p>Mã xác minh email của bạn:</p><div style="font-size:34px;font-weight:700;letter-spacing:9px;padding:18px;background:#10233d;border-radius:12px;text-align:center">${code}</div><p>Mã có hiệu lực trong 10 phút. Không chia sẻ mã này với bất kỳ ai.</p><p style="color:#9fb2c9;font-size:12px">Nếu bạn không yêu cầu đăng ký, hãy bỏ qua email này.</p></div>`
    })
  });
  if (!response.ok) {
    const detail = await response.text();
    console.error("Resend rejected email:", response.status, detail);
    throw new HttpsError("unavailable", "Không gửi được email lúc này. Hãy thử lại sau.");
  }
}

exports.requestEmailOtp = onCall({
  region: REGION,
  secrets: [RESEND_API_KEY, OTP_HMAC_SECRET, OTP_FROM_EMAIL],
  enforceAppCheck: false
}, async (request) => {
  const email = normalizeEmail(request.data?.email);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254) {
    throw new HttpsError("invalid-argument", "Email không hợp lệ.");
  }

  const db = getFirestore();
  const ref = db.collection("registrationOtps").doc(digest(email, OTP_HMAC_SECRET.value()));
  const now = Date.now();
  const existing = await ref.get();
  const lastSent = existing.get("lastSentAt")?.toMillis?.() || 0;
  if (now - lastSent < RESEND_WAIT_MS) {
    throw new HttpsError("resource-exhausted", "Vui lòng đợi 60 giây trước khi gửi mã mới.");
  }

  const code = randomDigits();
  await ref.set({
    email,
    codeHash: digest(code, OTP_HMAC_SECRET.value()),
    createdAt: FieldValue.serverTimestamp(),
    expiresAt: Timestamp.fromMillis(now + OTP_TTL_MS),
    lastSentAt: Timestamp.fromMillis(now),
    attempts: 0,
    verified: false,
    consumed: false,
    sessionHash: null
  });

  await sendOtpEmail(email, code, RESEND_API_KEY.value(), OTP_FROM_EMAIL.value());
  return { ok: true, expiresInSeconds: 600, resendAfterSeconds: 60 };
});

exports.verifyEmailOtp = onCall({
  region: REGION,
  secrets: [OTP_HMAC_SECRET]
}, async (request) => {
  const email = normalizeEmail(request.data?.email);
  const code = String(request.data?.code || "").trim();
  if (!/^\d{6}$/.test(code)) {
    throw new HttpsError("invalid-argument", "Mã xác minh phải có 6 chữ số.");
  }

  const db = getFirestore();
  const ref = db.collection("registrationOtps").doc(digest(email, OTP_HMAC_SECRET.value()));
  const sessionToken = crypto.randomBytes(32).toString("hex");
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists || snap.get("consumed") === true) {
      throw new HttpsError("not-found", "Không tìm thấy mã. Hãy yêu cầu gửi mã mới.");
    }
    if (snap.get("expiresAt")?.toMillis?.() < Date.now()) {
      throw new HttpsError("deadline-exceeded", "Mã đã hết hạn. Hãy gửi mã mới.");
    }
    if ((snap.get("attempts") || 0) >= MAX_ATTEMPTS) {
      throw new HttpsError("resource-exhausted", "Bạn đã nhập sai quá số lần cho phép. Hãy gửi mã mới.");
    }
    if (snap.get("codeHash") !== digest(code, OTP_HMAC_SECRET.value())) {
      tx.update(ref, { attempts: FieldValue.increment(1) });
      throw new HttpsError("permission-denied", "Mã xác minh không chính xác.");
    }
    tx.update(ref, {
      verified: true,
      verifiedAt: FieldValue.serverTimestamp(),
      sessionHash: digest(sessionToken, OTP_HMAC_SECRET.value())
    });
  });
  return { ok: true, verificationToken: sessionToken };
});

exports.completeRegistration = onCall({
  region: REGION,
  secrets: [OTP_HMAC_SECRET]
}, async (request) => {
  const email = normalizeEmail(request.data?.email);
  const password = String(request.data?.password || "");
  const username = cleanUsername(request.data?.username);
  const verificationToken = String(request.data?.verificationToken || "");

  if (password.length < 8 || password.length > 128) {
    throw new HttpsError("invalid-argument", "Mật khẩu phải có ít nhất 8 ký tự.");
  }
  if (username.length < 3 || username.length > 24 || !/^[\p{L}\p{N}_. -]+$/u.test(username)) {
    throw new HttpsError("invalid-argument", "Username phải dài 3–24 ký tự và không chứa ký tự đặc biệt.");
  }
  if (!verificationToken) throw new HttpsError("failed-precondition", "Bạn cần xác minh email trước.");

  const db = getFirestore();
  const otpRef = db.collection("registrationOtps").doc(digest(email, OTP_HMAC_SECRET.value()));
  const otpSnap = await otpRef.get();
  if (!otpSnap.exists || otpSnap.get("verified") !== true || otpSnap.get("consumed") === true ||
      otpSnap.get("expiresAt")?.toMillis?.() < Date.now() ||
      otpSnap.get("sessionHash") !== digest(verificationToken, OTP_HMAC_SECRET.value())) {
    throw new HttpsError("failed-precondition", "Phiên xác minh không hợp lệ hoặc đã hết hạn. Hãy xác minh lại email.");
  }

  const usernameKey = username.toLowerCase();
  const usernameRef = db.collection("usernames").doc(usernameKey);
  try {
    const userRecord = await getAuth().createUser({
      email,
      password,
      displayName: username,
      emailVerified: true
    });
    try {
      await db.runTransaction(async (tx) => {
        const nameSnap = await tx.get(usernameRef);
        if (nameSnap.exists) throw new HttpsError("already-exists", "Username này đã được sử dụng.");
        tx.create(usernameRef, { uid: userRecord.uid, createdAt: FieldValue.serverTimestamp() });
        tx.set(db.collection("users").doc(userRecord.uid), {
          uid: userRecord.uid,
          email,
          username,
          createdAt: FieldValue.serverTimestamp()
        });
        tx.update(otpRef, { consumed: true, consumedAt: FieldValue.serverTimestamp() });
      });
    } catch (error) {
      await getAuth().deleteUser(userRecord.uid);
      throw error;
    }
    const customToken = await getAuth().createCustomToken(userRecord.uid);
    return { ok: true, customToken, uid: userRecord.uid, username };
  } catch (error) {
    if (error instanceof HttpsError) throw error;
    if (error.code === "auth/email-already-exists") {
      throw new HttpsError("already-exists", "Email này đã được đăng ký.");
    }
    console.error("Registration failed:", error);
    throw new HttpsError("internal", "Không thể tạo tài khoản lúc này. Hãy thử lại.");
  }
});
