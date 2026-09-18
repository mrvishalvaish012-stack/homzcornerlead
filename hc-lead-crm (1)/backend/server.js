const express = require('express');
const cors = require('cors');
const axios = require('axios');
const path = require('path');
const fs = require('fs');
require('dotenv').config();

const app = express();
// Allow custom PORT from environment, default to 3000 (avoid container preview port 8080 if set)
const PORT = process.env.BACKEND_PORT || (process.env.PORT && process.env.PORT !== '8080' ? process.env.PORT : 3000);
const VERIFY_TOKEN = process.env.VERIFY_TOKEN || 'leadpulse_meta_verify_2026';
const PAGE_ACCESS_TOKEN = process.env.PAGE_ACCESS_TOKEN || '';

app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Persistent storage setup
const DATA_DIR = path.join(__dirname, 'data');
const DB_FILE = path.join(DATA_DIR, 'crm_store.json');

if (!fs.existsSync(DATA_DIR)) {
  try {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  } catch (e) {
    console.error('Failed to create data dir:', e);
  }
}

// Initial default state
let leadsStore = [];
let agentsStore = [
  { id: 1, name: 'Vikram Malhotra', email: 'vikram.m@sales.leadpulse.com', phoneNumber: '+91 98765 43210', role: 'AGENT', isActive: true, leadsAssignedCount: 12, leadsConvertedCount: 4, lastAssignedAt: Date.now() - 3600000, avatarColorHex: 0xFF2563EB },
  { id: 2, name: 'Priya Sharma', email: 'priya.s@sales.leadpulse.com', phoneNumber: '+91 98765 43211', role: 'AGENT', isActive: true, leadsAssignedCount: 14, leadsConvertedCount: 6, lastAssignedAt: Date.now() - 1800000, avatarColorHex: 0xFF10B981 },
  { id: 3, name: 'Amit Verma', email: 'amit.v@sales.leadpulse.com', phoneNumber: '+91 98765 43212', role: 'AGENT', isActive: true, leadsAssignedCount: 10, leadsConvertedCount: 3, lastAssignedAt: Date.now() - 7200000, avatarColorHex: 0xFFF59E0B },
  { id: 4, name: 'Neha Gupta', email: 'neha.g@sales.leadpulse.com', phoneNumber: '+91 98765 43213', role: 'AGENT', isActive: true, leadsAssignedCount: 11, leadsConvertedCount: 5, lastAssignedAt: Date.now() - 900000, avatarColorHex: 0xFF8B5CF6 }
];
let feedbacksStore = [];
const webhookLogs = [];

// Load persisted data if exists
function loadPersistedData() {
  try {
    if (fs.existsSync(DB_FILE)) {
      const data = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
      if (Array.isArray(data.leads)) leadsStore = data.leads;
      if (Array.isArray(data.agents) && data.agents.length > 0) agentsStore = data.agents;
      if (Array.isArray(data.feedbacks)) feedbacksStore = data.feedbacks;
      console.log(`[STORE] Loaded ${leadsStore.length} leads, ${agentsStore.length} agents, ${feedbacksStore.length} feedbacks from disk`);
    }
  } catch (err) {
    console.error('[STORE ERROR] Could not read db file:', err.message);
  }
}

function savePersistedData() {
  try {
    const payload = {
      leads: leadsStore,
      agents: agentsStore,
      feedbacks: feedbacksStore,
      savedAt: new Date().toISOString()
    };
    fs.writeFileSync(DB_FILE, JSON.stringify(payload, null, 2), 'utf8');
  } catch (err) {
    console.error('[STORE ERROR] Could not write db file:', err.message);
  }
}

loadPersistedData();

function addLog(type, message, data = null) {
  const entry = {
    id: 'log_' + Date.now() + '_' + Math.floor(Math.random() * 1000),
    timestamp: new Date().toISOString(),
    type,
    message,
    data
  };
  webhookLogs.unshift(entry);
  if (webhookLogs.length > 100) webhookLogs.pop();
  console.log(`[${entry.timestamp}] [${type}] ${message}`);
}

/**
 * 1. META WEBHOOK VERIFICATION (GET /webhook)
 * Meta pings this URL with hub.mode, hub.challenge, and hub.verify_token
 * to verify endpoint ownership before activating the webhook subscription.
 */
app.get('/webhook', (req, res) => {
  const mode = req.query['hub.mode'];
  const token = req.query['hub.verify_token'];
  const challenge = req.query['hub.challenge'];

  addLog('HANDSHAKE_REQUEST', `Received GET handshake from Meta. Mode: ${mode}, Token: ${token}`);

  if (mode === 'subscribe' && token === VERIFY_TOKEN) {
    addLog('HANDSHAKE_SUCCESS', 'Verification token matches! Returning challenge string.');
    res.status(200).set('Content-Type', 'text/plain').send(challenge);
  } else {
    addLog('HANDSHAKE_FAILED', `Verification failed. Expected "${VERIFY_TOKEN}", but received "${token}".`);
    res.status(403).send('Verification token mismatch');
  }
});

/**
 * 2. META INCOMING EVENTS (POST /webhook)
 * Receives real-time events for Facebook Lead Ads and WhatsApp Cloud API messages.
 */
app.post('/webhook', async (req, res) => {
  // Always respond with 200 OK immediately within 3 seconds as required by Meta
  res.status(200).send('EVENT_RECEIVED');

  try {
    const body = req.body;
    addLog('INCOMING_EVENT', `Received event for object: ${body.object}`, body);

    if (body.object === 'page') {
      // Handle Facebook & Instagram Lead Ads
      for (const entry of body.entry || []) {
        for (const change of entry.changes || []) {
          if (change.field === 'leadgen') {
            const val = change.value || {};
            const leadgenId = val.leadgen_id;
            const formId = val.form_id;
            const pageId = val.page_id;
            const createdTime = val.created_time || Math.floor(Date.now() / 1000);

            addLog('LEAD_DETECTED', `New Meta Leadgen ID received: ${leadgenId} on form ${formId}`);

            // If Page Access Token is configured, fetch full lead details via Graph API
            let leadDetails = null;
            if (PAGE_ACCESS_TOKEN) {
              try {
                const graphUrl = `https://graph.facebook.com/v21.0/${leadgenId}?access_token=${PAGE_ACCESS_TOKEN}`;
                const resp = await axios.get(graphUrl);
                leadDetails = resp.data;
                addLog('GRAPH_API_SUCCESS', `Fetched lead details for ${leadgenId}`, leadDetails);
              } catch (err) {
                addLog('GRAPH_API_ERROR', `Failed to fetch lead details: ${err.message}`);
              }
            }

            // Extract lead details
            let fullName = `Facebook Lead #${leadgenId.slice(-4)}`;
            let phoneNumber = '';
            let email = '';
            const otherFields = [];

            if (leadDetails && Array.isArray(leadDetails.field_data)) {
              for (const field of leadDetails.field_data) {
                const fName = (field.name || '').toLowerCase();
                const fVal = (field.values && field.values[0]) || '';
                if (fName.includes('full_name') || fName === 'name' || fName.includes('first_name')) {
                  fullName = fVal || fullName;
                } else if (fName.includes('phone')) {
                  phoneNumber = fVal;
                } else if (fName.includes('email')) {
                  email = fVal;
                } else if (fVal) {
                  otherFields.push(`${field.name}: ${fVal}`);
                }
              }
            }

            const leadRecord = {
              id: 'lead_' + leadgenId,
              source: 'Meta Lead Ads',
              leadId: leadgenId,
              formId: formId || 'default',
              pageId: pageId || '',
              name: fullName,
              phone: phoneNumber || '+91 98000 00000',
              email: email || '',
              message: otherFields.join(' | ') || 'Lead submitted via Facebook / Instagram Lead Form',
              createdAt: new Date(createdTime * 1000).toISOString(),
              status: 'NEW',
              rawPayload: leadDetails || val
            };

            leadsStore.unshift(leadRecord);
            addLog('LEAD_STORED', `Lead stored: ${leadRecord.name} (${leadRecord.phone})`);
          }
        }
      }
    } else if (body.object === 'whatsapp_business_account') {
      // Handle WhatsApp Cloud API Incoming Messages
      for (const entry of body.entry || []) {
        for (const change of entry.changes || []) {
          const val = change.value || {};
          const contacts = val.contacts || [];
          const contactName = contacts[0]?.profile?.name || 'WhatsApp Customer';

          for (const msg of val.messages || []) {
            const senderPhone = msg.from ? (msg.from.startsWith('+') ? msg.from : `+${msg.from}`) : '';
            const textBody = msg.text?.body || msg.type || 'Inbound WhatsApp Message';

            const leadRecord = {
              id: 'wa_' + (msg.id || Date.now()),
              source: 'Meta WhatsApp Ad',
              leadId: msg.id || 'wa_' + Date.now(),
              formId: 'whatsapp_click_to_chat',
              pageId: val.metadata?.phone_number_id || '',
              name: contactName,
              phone: senderPhone,
              email: '',
              message: textBody,
              createdAt: new Date(parseInt(msg.timestamp || Date.now() / 1000) * 1000).toISOString(),
              status: 'NEW',
              rawPayload: msg
            };

            leadsStore.unshift(leadRecord);
            addLog('WHATSAPP_LEAD_STORED', `WhatsApp lead received from ${leadRecord.name} (${leadRecord.phone}): "${textBody}"`);
          }
        }
      }
    }
  } catch (error) {
    addLog('EVENT_PROCESSING_ERROR', `Error processing webhook event: ${error.message}`);
  }
});

/**
 * 3. REST API ENDPOINTS FOR ANDROID CRM APP & MULTI-DEVICE CLOUD SYNC
 */

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    uptime: process.uptime(),
    verifyTokenConfigured: VERIFY_TOKEN,
    leadsCount: leadsStore.length,
    agentsCount: agentsStore.length,
    feedbacksCount: feedbacksStore.length,
    serverTime: Date.now(),
    timestamp: new Date().toISOString()
  });
});

/**
 * PULL CHANGES FROM CLOUD
 * Called by Android apps periodically (every 15-20s) or on manual refresh.
 * Returns leads and feedbacks updated on the server since query param `since`.
 */
app.get('/api/sync/pull', (req, res) => {
  const since = parseInt(req.query.since) || 0;
  const filteredLeads = leadsStore.filter(l => (l.updatedAt || l.createdAt || 0) > since);
  const filteredFeedbacks = feedbacksStore.filter(f => (f.timestamp || 0) > since);

  res.json({
    success: true,
    serverTime: Date.now(),
    leads: filteredLeads,
    feedbacks: filteredFeedbacks,
    agents: agentsStore,
    totalServerLeads: leadsStore.length
  });
});

/**
 * FULL SYNC / INITIAL DOWNLOAD
 * Called on first app launch or full re-sync.
 */
app.get('/api/sync/full', (req, res) => {
  res.json({
    success: true,
    serverTime: Date.now(),
    leads: leadsStore,
    feedbacks: feedbacksStore,
    agents: agentsStore,
    totalServerLeads: leadsStore.length
  });
});

/**
 * PUSH CHANGES TO CLOUD
 * Called by an Android app when an agent creates/edits a lead, logs call feedback, or moves pipeline stage.
 */
app.post('/api/sync/push', (req, res) => {
  try {
    const { clientDeviceId, clientAgentName, leads = [], feedbacks = [], agents = [] } = req.body;
    let mergedLeadsCount = 0;
    let mergedFeedbacksCount = 0;
    const now = Date.now();

    // 1. Merge Incoming Leads
    for (const inLead of leads) {
      if (!inLead.phoneNumber && !inLead.id) continue;

      // Find existing by ID or phone number
      const existingIdx = leadsStore.findIndex(
        l => (inLead.id && String(l.id) === String(inLead.id)) ||
             (inLead.phoneNumber && l.phoneNumber === inLead.phoneNumber)
      );

      const normalizedLead = {
        ...inLead,
        id: inLead.id || 'lead_' + Date.now() + '_' + Math.floor(Math.random() * 1000),
        name: inLead.name || 'Unnamed Customer',
        phoneNumber: inLead.phoneNumber || '+91 98000 00000',
        status: inLead.status || 'NEW',
        priority: inLead.priority || 'HOT',
        dealValue: Number(inLead.dealValue) || 25000.0,
        budget: inLead.budget || '₹15,000 - ₹50,000',
        assignedAgentId: inLead.assignedAgentId || 1,
        assignedAgentName: inLead.assignedAgentName || 'Vikram Malhotra',
        updatedAt: inLead.updatedAt || now,
        createdAt: inLead.createdAt || now
      };

      if (existingIdx !== -1) {
        // Conflict resolution: whichever update is newer wins
        const existing = leadsStore[existingIdx];
        if ((normalizedLead.updatedAt || 0) >= (existing.updatedAt || 0)) {
          leadsStore[existingIdx] = { ...existing, ...normalizedLead, updatedAt: now };
          mergedLeadsCount++;
        }
      } else {
        leadsStore.unshift(normalizedLead);
        mergedLeadsCount++;
      }
    }

    // 2. Merge Feedbacks
    for (const inFb of feedbacks) {
      const exists = feedbacksStore.some(
        f => (inFb.id && String(f.id) === String(inFb.id)) ||
             (f.leadId === inFb.leadId && f.timestamp === inFb.timestamp)
      );
      if (!exists) {
        feedbacksStore.unshift({
          ...inFb,
          id: inFb.id || 'fb_' + Date.now() + '_' + Math.floor(Math.random() * 1000),
          timestamp: inFb.timestamp || now
        });
        mergedFeedbacksCount++;
      }
    }

    // 3. Merge Agents (if updated)
    if (Array.isArray(agents) && agents.length > 0) {
      for (const inAg of agents) {
        const agIdx = agentsStore.findIndex(a => a.id === inAg.id || a.phoneNumber === inAg.phoneNumber);
        if (agIdx !== -1) {
          agentsStore[agIdx] = { ...agentsStore[agIdx], ...inAg };
        } else {
          agentsStore.push(inAg);
        }
      }
    }

    savePersistedData();

    addLog(
      'SYNC_PUSH',
      `Synced from ${clientAgentName || 'Device'} (${clientDeviceId || 'app'}): +${mergedLeadsCount} leads, +${mergedFeedbacksCount} feedbacks.`
    );

    res.json({
      success: true,
      serverTime: now,
      mergedLeads: mergedLeadsCount,
      mergedFeedbacks: mergedFeedbacksCount,
      totalServerLeads: leadsStore.length
    });
  } catch (err) {
    addLog('SYNC_ERROR', `Error merging push sync: ${err.message}`);
    res.status(500).json({ success: false, error: err.message });
  }
});

// Fetch all captured leads
app.get('/api/leads', (req, res) => {
  const limit = parseInt(req.query.limit) || 100;
  res.json({
    success: true,
    total: leadsStore.length,
    leads: leadsStore.slice(0, limit)
  });
});

// Quick Lead Status Update Endpoint
app.post('/api/leads/:id/status', (req, res) => {
  const { id } = req.params;
  const { status, agentId, agentName, notes } = req.body;
  const lead = leadsStore.find(l => String(l.id) === String(id));

  if (!lead) {
    return res.status(404).json({ success: false, message: 'Lead not found' });
  }

  const prevStatus = lead.status;
  lead.status = status || lead.status;
  lead.updatedAt = Date.now();

  if (agentId) lead.assignedAgentId = agentId;
  if (agentName) lead.assignedAgentName = agentName;

  if (notes) {
    feedbacksStore.unshift({
      id: 'fb_' + Date.now(),
      leadId: lead.id,
      agentId: agentId || lead.assignedAgentId,
      agentName: agentName || lead.assignedAgentName,
      timestamp: Date.now(),
      disposition: `Status changed to ${lead.status}`,
      notes: notes,
      previousStatus: prevStatus,
      newStatus: lead.status
    });
  }

  savePersistedData();
  addLog('LEAD_STATUS_UPDATE', `Lead #${id} status changed from ${prevStatus} to ${lead.status} by ${agentName || 'Agent'}`);
  res.json({ success: true, lead });
});

// Sales Team API
app.get('/api/agents', (req, res) => {
  res.json({ success: true, agents: agentsStore });
});

app.post('/api/agents', (req, res) => {
  const newAgent = {
    id: req.body.id || Date.now(),
    name: req.body.name,
    email: req.body.email,
    phoneNumber: req.body.phoneNumber,
    role: req.body.role || 'AGENT',
    isActive: req.body.isActive !== false,
    leadsAssignedCount: 0,
    leadsConvertedCount: 0,
    lastAssignedAt: 0,
    avatarColorHex: req.body.avatarColorHex || 0xFF2563EB
  };
  agentsStore.push(newAgent);
  savePersistedData();
  res.json({ success: true, agent: newAgent });
});

// Clear leads (for testing/resetting)
app.delete('/api/leads', (req, res) => {
  leadsStore.length = 0;
  feedbacksStore.length = 0;
  savePersistedData();
  res.json({ success: true, message: 'Leads and feedback store cleared' });
});

// Fetch recent webhook logs
app.get('/api/logs', (req, res) => {
  res.json({
    success: true,
    logs: webhookLogs.slice(0, 50)
  });
});

// Simulation test endpoint (allows manually creating a lead to verify end-to-end sync)
app.post('/api/test-lead', (req, res) => {
  const now = Date.now();
  const testLead = {
    id: 'test_' + now,
    source: req.body.source || 'Meta Lead Ads (Test)',
    leadId: 'test_meta_' + Math.floor(Math.random() * 100000),
    formId: 'test_form',
    pageId: 'test_page',
    name: req.body.name || 'Rahul Sharma',
    phone: req.body.phone || '+91 98765 43210',
    phoneNumber: req.body.phone || '+91 98765 43210',
    email: req.body.email || 'rahul.sharma@example.com',
    message: req.body.message || 'Interested in 3 BHK Luxury Apartment inquiry from Instagram',
    initialMessage: req.body.message || 'Interested in 3 BHK Luxury Apartment inquiry from Instagram',
    createdAt: now,
    updatedAt: now,
    assignedAgentId: 1,
    assignedAgentName: 'Vikram Malhotra',
    status: 'NEW',
    priority: 'HOT',
    budget: '₹50,000 - ₹1,00,000',
    dealValue: 75000.0
  };

  leadsStore.unshift(testLead);
  savePersistedData();
  addLog('SIMULATED_LEAD', `Simulated test lead created: ${testLead.name} (${testLead.phone})`);
  res.json({ success: true, lead: testLead });
});

/**
 * 4. WEB DASHBOARD UI
 */
app.get('/', (req, res) => {
  const host = req.get('host');
  const protocol = req.protocol === 'https' || req.get('x-forwarded-proto') === 'https' ? 'https' : 'http';
  const fullWebhookUrl = `${protocol}://${host}/webhook`;

  const html = `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>LeadPulse Meta Webhook Server</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { background: #0f172a; color: #f8fafc; padding: 24px; }
    .container { max-width: 960px; margin: 0 auto; }
    .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid #334155; }
    .badge { background: #10b981; color: #022c22; font-size: 12px; font-weight: 700; padding: 4px 10px; border-radius: 9999px; }
    .card { background: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 20px; margin-bottom: 20px; }
    h1 { font-size: 24px; font-weight: 800; color: #38bdf8; }
    h2 { font-size: 16px; font-weight: 700; margin-bottom: 12px; color: #94a3b8; }
    .field { margin-bottom: 14px; }
    .label { font-size: 12px; font-weight: 600; color: #94a3b8; margin-bottom: 4px; }
    .input-box { display: flex; gap: 8px; background: #0f172a; border: 1px solid #475569; border-radius: 8px; padding: 10px; font-family: monospace; font-size: 14px; color: #e2e8f0; }
    .copy-btn { background: #0284c7; color: white; border: none; padding: 6px 14px; border-radius: 6px; cursor: pointer; font-size: 12px; font-weight: 600; }
    .copy-btn:hover { background: #0369a1; }
    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 20px; }
    .stat-box { background: #1e293b; border: 1px solid #334155; border-radius: 12px; padding: 16px; text-align: center; }
    .stat-num { font-size: 28px; font-weight: 800; color: #38bdf8; }
    .stat-label { font-size: 12px; color: #94a3b8; }
    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
    th, td { text-align: left; padding: 10px; border-bottom: 1px solid #334155; font-size: 13px; }
    th { color: #94a3b8; }
    .log-entry { font-family: monospace; font-size: 12px; padding: 8px 0; border-bottom: 1px solid #334155; color: #cbd5e1; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <div>
        <h1>⚡ LeadPulse Meta & WhatsApp Webhook Server</h1>
        <p style="color: #94a3b8; font-size: 13px; margin-top: 4px;">Live backend service for Meta Lead Ads and WhatsApp Cloud API</p>
      </div>
      <span class="badge">● SERVER ONLINE</span>
    </div>

    <div class="stats">
      <div class="stat-box">
        <div class="stat-num">${leadsStore.length}</div>
        <div class="stat-label">Total Leads Captured</div>
      </div>
      <div class="stat-box">
        <div class="stat-num">${leadsStore.filter(l => l.source.includes('WhatsApp')).length}</div>
        <div class="stat-label">WhatsApp Leads</div>
      </div>
      <div class="stat-box">
        <div class="stat-num">${leadsStore.filter(l => l.source.includes('Lead Ads')).length}</div>
        <div class="stat-label">Meta Lead Ads</div>
      </div>
      <div class="stat-box">
        <div class="stat-num">${webhookLogs.length}</div>
        <div class="stat-label">Webhook Events Logged</div>
      </div>
    </div>

    <div class="card">
      <h2>📋 Meta Developers Console Credentials</h2>
      <div class="field">
        <div class="label">1. Callback URL (Paste in Meta):</div>
        <div class="input-box">
          <span style="flex:1;">${fullWebhookUrl}</span>
          <button class="copy-btn" onclick="navigator.clipboard.writeText('${fullWebhookUrl}');alert('Callback URL copied!')">Copy</button>
        </div>
      </div>
      <div class="field">
        <div class="label">2. Verify Token (Paste in Meta):</div>
        <div class="input-box">
          <span style="flex:1;">${VERIFY_TOKEN}</span>
          <button class="copy-btn" onclick="navigator.clipboard.writeText('${VERIFY_TOKEN}');alert('Verify Token copied!')">Copy</button>
        </div>
      </div>
      <div style="margin-top: 12px; display: flex; gap: 8px;">
        <button class="copy-btn" style="background:#10b981;" onclick="fetch('/api/test-lead', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({})}).then(r=>r.json()).then(()=>location.reload())">Send Test Lead</button>
        <button class="copy-btn" style="background:#ef4444;" onclick="fetch('/api/leads', {method:'DELETE'}).then(()=>location.reload())">Clear Leads</button>
      </div>
    </div>

    <div class="card">
      <h2>📥 Live Captured Leads (${leadsStore.length})</h2>
      ${leadsStore.length === 0 ? '<p style="color:#64748b; font-size: 13px;">No leads received yet. Send a test lead above or connect your Meta Webhook.</p>' : `
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Source</th>
              <th>Name</th>
              <th>Phone</th>
              <th>Message / Requirement</th>
            </tr>
          </thead>
          <tbody>
            ${leadsStore.slice(0, 10).map(l => `
              <tr>
                <td style="color:#94a3b8;">${new Date(l.createdAt).toLocaleTimeString()}</td>
                <td><span style="background:#0369a1; color:white; padding:2px 6px; border-radius:4px; font-size:11px;">${l.source}</span></td>
                <td><b>${l.name}</b></td>
                <td style="font-family:monospace; color:#38bdf8;">${l.phone}</td>
                <td>${l.message}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      `}
    </div>

    <div class="card">
      <h2>📜 Webhook Event Logs</h2>
      <div style="max-height: 240px; overflow-y: auto;">
        ${webhookLogs.length === 0 ? '<p style="color:#64748b; font-size: 13px;">No webhook calls received yet.</p>' : 
          webhookLogs.slice(0, 20).map(log => `
            <div class="log-entry">
              <span style="color:#38bdf8;">[${log.timestamp.slice(11, 19)}]</span> 
              <b style="color:#f59e0b;">${log.type}</b>: ${log.message}
            </div>
          `).join('')
        }
      </div>
    </div>
  </div>
</body>
</html>
  `;
  res.send(html);
});

process.on('uncaughtException', (err) => {
  console.error('[UNCAUGHT_EXCEPTION]', err);
});

process.on('unhandledRejection', (reason, promise) => {
  console.error('[UNHANDLED_REJECTION]', reason);
});

const server = app.listen(PORT, () => {
  addLog('SERVER_START', `Server started successfully on port ${PORT}`);
  console.log(`🚀 LeadPulse Meta Webhook Backend running on http://localhost:${PORT}`);
  console.log(`🔗 Webhook endpoint: http://localhost:${PORT}/webhook`);
  console.log(`🔑 Verify Token: ${VERIFY_TOKEN}`);
});
