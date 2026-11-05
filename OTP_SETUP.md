# Omlet Reborn — Email OTP backend setup

The Android client must call these Firebase callable functions:
- `requestEmailOtp({ email })`
- `verifyEmailOtp({ email, code })` → returns `verificationToken`
- `completeRegistration({ email, password, username, verificationToken })` → returns `customToken`

The backend sends a six-digit email code through Resend. Codes expire after 10 minutes, can be resent after 60 seconds, and allow at most five guesses. Passwords are never stored in Firestore; Firebase Authentication stores them.

## One-time setup

1. Create/verify a sending domain in Resend: https://resend.com/domains
2. Use a sender address on that verified domain, e.g. `Omlet Reborn <no-reply@your-domain.example>`. Do not use the example literally.
3. In Firebase Console for project `omlet-reborn-7adaa`, make sure Firestore Database and Authentication are enabled.
4. Install Firebase CLI on a computer/Cloud Shell environment, then from the repository root run:
   ```sh
   npm --prefix functions install
   firebase login
   firebase use omlet-reborn-7adaa
   firebase functions:secrets:set RESEND_API_KEY
   firebase functions:secrets:set OTP_HMAC_SECRET
   firebase functions:secrets:set OTP_FROM_EMAIL
   firebase deploy --only functions
   ```
   When prompted, enter the Resend API key, a long random secret (at least 32 random bytes), and the verified sender address. Never commit these values to GitHub or paste them into chat.
5. Firebase Cloud Functions deployment generally requires the Firebase project to use the Blaze billing plan and a billing account. Review Firebase and Resend pricing/quotas before enabling it.

## Important

- The Android app cannot send secure OTP emails by itself. OTP generation, storage, expiry, attempt limits, and email delivery must stay on the server.
- This backend does not expose OTP codes in responses or logs.
- Firestore client rules do not grant access to `registrationOtps` or `usernames`; Admin SDK calls run on the server.
- Configure Firebase App Check before public release and consider additional abuse/rate controls and scheduled cleanup for expired OTP documents.
- This repository change only adds the backend and setup guide. The Android registration screen still needs to be wired to these functions before OTP registration is usable.
