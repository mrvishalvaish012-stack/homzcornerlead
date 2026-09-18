# ⚡ LeadPulse Meta & WhatsApp Webhook Backend

A production-ready Node.js Express webhook service designed to integrate **Meta Lead Ads (Facebook & Instagram)** and **WhatsApp Cloud API** directly with the **LeadPulse CRM Android App**.

---

## 🌟 What This Backend Does

1. **Meta Handshake Verification (`GET /webhook`)**:
   - Automatically handles Meta's verification challenge (`hub.challenge`) using the `hub.verify_token` (`leadpulse_meta_verify_2026`).
   - Responds with HTTP 200 and passes Meta's verification instantly.
2. **Meta Lead Ads Ingestion (`POST /webhook`)**:
   - Listens to the `leadgen` field.
   - Automatically fetches lead field data (Name, Phone, Email, Questions) using Meta Graph API if `PAGE_ACCESS_TOKEN` is supplied.
3. **WhatsApp Cloud API Ingestion (`POST /webhook`)**:
   - Listens to incoming messages from Click-to-WhatsApp ads.
   - Captures customer phone number, message text, and name.
4. **REST API for Android App (`GET /api/leads`)**:
   - Android CRM app syncs leads from this endpoint with 1 click.
5. **Live Web Dashboard (`GET /`)**:
   - Beautiful dark-mode dashboard showing total leads, real-time webhook logs, test lead generator, and credential copy buttons.

---

## 🚀 Free 1-Click Hosting Options (Get an HTTPS URL in 2 Minutes)

Meta **requires a live HTTPS URL**. You can deploy this backend for 100% free on any of these platforms:

### Option 1: Render.com (Recommended - 100% Free)
1. Go to [render.com](https://render.com) and create a free account.
2. Click **New +** > **Web Service**.
3. Connect your GitHub repository (or upload this `/backend` folder).
4. Settings:
   - **Environment**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `node server.js`
   - **Environment Variables**:
     - `VERIFY_TOKEN` = `leadpulse_meta_verify_2026`
5. Click **Create Web Service**.
6. In ~60 seconds, Render will give you an HTTPS URL, for example:
   `https://leadpulse-webhook.onrender.com`
7. Your Meta Webhook URL is:
   `https://leadpulse-webhook.onrender.com/webhook`

---

### Option 2: Railway.app (Free & Fast)
1. Go to [railway.app](https://railway.app).
2. Click **New Project** > **Deploy from GitHub repo**.
3. Railway automatically detects `Dockerfile` or `package.json` and gives you a free domain:
   `https://your-app.up.railway.app/webhook`

---

### Option 3: Run Locally with ngrok (For Testing Right Now)
If you are developing locally:
1. Open terminal in the `/backend` folder:
   ```bash
   npm install
   npm start
   ```
2. In a second terminal window, run:
   ```bash
   npx ngrok http 3000
   ```
3. Copy the HTTPS forwarding URL given by ngrok:
   `https://xxxx-xx-xx.ngrok-free.app/webhook`
4. Paste this URL into Meta Developers Console.

---

## 📋 Meta Developers Console Settings

When configuring your Webhook in [developers.facebook.com](https://developers.facebook.com):

| Field | Value |
| :--- | :--- |
| **Callback URL** | `https://your-backend-domain.com/webhook` |
| **Verify Token** | `leadpulse_meta_verify_2026` |
| **Subscriptions** | For Lead Ads: `leadgen`<br>For WhatsApp: `messages` |

---

## 📱 Connecting to the LeadPulse Android App

In the Android App:
1. Open **Meta Ads** tab.
2. Go to **Meta Webhook & Graph API** > **🖥️ Custom Backend**.
3. Enter your backend URL: `https://your-backend-domain.com`.
4. Click **Sync Leads from Backend** to import leads into your CRM.
