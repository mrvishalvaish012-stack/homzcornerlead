const express = require('express');
const cors = require('cors');
const axios = require('axios');
const mongoose = require('mongoose');
require('dotenv').config();

const app = express();
const PORT = process.env.BACKEND_PORT || (process.env.PORT && process.env.PORT !== '8080' ? process.env.PORT : 3000);
const VERIFY_TOKEN = process.env.VERIFY_TOKEN || 'leadpulse_meta_verify_2026';
const PAGE_ACCESS_TOKEN = process.env.PAGE_ACCESS_TOKEN || '';
const MONGODB_URI = process.env.MONGODB_URI;

if (!MONGODB_URI) {
  console.error('❌ ERROR: MONGODB_URI is not defined in environment variables!');
} else {
  mongoose.connect(MONGODB_URI)
    .then(() => console.log('✅ Connected to MongoDB Atlas'))
    .catch(err => console.error('❌ MongoDB Connection Error:', err));
}

app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// --- DATABASE SCHEMAS ---

const LeadSchema = new mongoose.Schema({
  id: { type: String, unique: true, required: true },
  source: String,
  leadId: String,
  formId: String,
  pageId: String,
  name: String,
  phone: String,
  phoneNumber: String,
  email: String,
  message: String,
  createdAt: { type: Date, default: Date.now },
  updatedAt: { type: Date, default: Date.now },
  status: { type: String, default: 'NEW' },
  priority: { type: String, default: 'HOT' },
  dealValue: Number,
  budget: String,
  assignedAgentId: mongoose.Schema.Types.Mixed,
  assignedAgentName: String,
  rawPayload: mongoose.Schema.Types.Mixed
});

const AgentSchema = new mongoose.Schema({
  id: { type: mongoose.Schema.Types.Mixed, unique: true },
  name: String,
  email: String,
  phoneNumber: String,
  role: { type: String, default: 'AGENT' },
  isActive: { type: Boolean, default: true },
  leadsAssignedCount: { type: Number, default: 0 },
  leadsConvertedCount: { type: Number, default: 0 },
  lastAssignedAt: Date,
  avatarColorHex: mongoose.Schema.Types.Mixed
});

const FeedbackSchema = new mongoose.Schema({
  id: { type: String, unique: true },
  leadId: String,
  agentId: mongoose.Schema.Types.Mixed,
  agentName: String,
  timestamp: { type: Date, default: Date.now },
  disposition: String,
  notes: String,
  previousStatus: String,
  newStatus: String
});

const Lead = mongoose.model('Lead', LeadSchema);
const Agent = mongoose.model('Agent', AgentSchema);
const Feedback = mongoose.model('Feedback', FeedbackSchema);

const webhookLogs = [];
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

// --- ENDPOINTS ---

app.get('/webhook', (req, res) => {
  const mode = req.query['hub.mode'];
  const token = req.query['hub.verify_token'];
  const challenge = req.query['hub.challenge'];
  if (mode === 'subscribe' && token === VERIFY_TOKEN) {
    res.status(200).set('Content-Type', 'text/plain').send(challenge);
  } else {
    res.status(403).send('Verification token mismatch');
  }
});

app.post('/webhook', async (req, res) => {
  res.status(200).send('EVENT_RECEIVED');
  try {
    const body = req.body;
    if (body.object === 'page') {
      for (const entry of body.entry || []) {
        for (const change of entry.changes || []) {
          if (change.field === 'leadgen') {
            const val = change.value || {};
            const leadgenId = val.leadgen_id;
            let leadDetails = null;
            if (PAGE_ACCESS_TOKEN) {
              try {
                const graphUrl = `https://graph.facebook.com/v21.0/${leadgenId}?access_token=${PAGE_ACCESS_TOKEN}`;
                const resp = await axios.get(graphUrl);
                leadDetails = resp.data;
              } catch (err) { addLog('GRAPH_API_ERROR', err.message); }
            }
            let fullName = `Facebook Lead #${leadgenId.slice(-4)}`;
            let phoneNumber = '';
            let email = '';
            const otherFields = [];
            if (leadDetails && Array.isArray(leadDetails.field_data)) {
              for (const field of leadDetails.field_data) {
                const fName = (field.name || '').toLowerCase();
                const fVal = (field.values && field.values[0]) || '';
                if (fName.includes('full_name') || fName === 'name') fullName = fVal;
                else if (fName.includes('phone')) phoneNumber = fVal;
                else if (fName.includes('email')) email = fVal;
                else if (fVal) otherFields.push(`${field.name}: ${fVal}`);
              }
            }
            await Lead.findOneAndUpdate(
              { id: 'lead_' + leadgenId },
              {
                id: 'lead_' + leadgenId,
                source: 'Meta Lead Ads',
                leadId: leadgenId,
                formId: val.form_id || 'default',
                pageId: val.page_id || '',
                name: fullName,
                phone: phoneNumber || '+91 98000 00000',
                phoneNumber: phoneNumber || '+91 98000 00000',
                email: email || '',
                message: otherFields.join(' | ') || 'Lead submitted via Facebook / Instagram',
                createdAt: new Date((val.created_time || Date.now() / 1000) * 1000),
                status: 'NEW',
                rawPayload: leadDetails || val
              },
              { upsert: true }
            );
            addLog('LEAD_STORED', `Stored lead: ${fullName}`);
          }
        }
      }
    } else if (body.object === 'whatsapp_business_account') {
      for (const entry of body.entry || []) {
        for (const change of entry.changes || []) {
          const val = change.value || {};
          for (const msg of val.messages || []) {
            const senderPhone = msg.from ? (msg.from.startsWith('+') ? msg.from : `+${msg.from}`) : '';
            const textBody = msg.text?.body || 'Inbound WhatsApp Message';
            await Lead.findOneAndUpdate(
              { id: 'wa_' + (msg.id || Date.now()) },
              {
                id: 'wa_' + (msg.id || Date.now()),
                source: 'Meta WhatsApp Ad',
                leadId: msg.id || 'wa_' + Date.now(),
                name: val.contacts?.[0]?.profile?.name || 'WhatsApp Customer',
                phone: senderPhone,
                phoneNumber: senderPhone,
                message: textBody,
                createdAt: new Date((msg.timestamp || Date.now() / 1000) * 1000),
                status: 'NEW'
              },
              { upsert: true }
            );
            addLog('WHATSAPP_LEAD_STORED', `Stored WhatsApp lead from ${senderPhone}`);
          }
        }
      }
    }
  } catch (error) { addLog('EVENT_PROCESSING_ERROR', error.message); }
});

app.get('/health', async (req, res) => {
  res.json({
    status: 'ok',
    leadsCount: await Lead.countDocuments(),
    agentsCount: await Agent.countDocuments(),
    feedbacksCount: await Feedback.countDocuments(),
    timestamp: new Date().toISOString()
  });
});

app.get('/api/sync/pull', async (req, res) => {
  const since = parseInt(req.query.since) || 0;
  const filteredLeads = await Lead.find({ $or: [{ updatedAt: { $gt: new Date(since) } }, { createdAt: { $gt: new Date(since) } }] });
  const filteredFeedbacks = await Feedback.find({ timestamp: { $gt: new Date(since) } });
  const agents = await Agent.find({});
  res.json({ success: true, serverTime: Date.now(), leads: filteredLeads, feedbacks: filteredFeedbacks, agents, totalServerLeads: await Lead.countDocuments() });
});

app.get('/api/sync/full', async (req, res) => {
  res.json({ success: true, serverTime: Date.now(), leads: await Lead.find({}), feedbacks: await Feedback.find({}), agents: await Agent.find({}), totalServerLeads: await Lead.countDocuments() });
});

app.post('/api/sync/push', async (req, res) => {
  try {
    const { leads = [], feedbacks = [], agents = [] } = req.body;
    let mLeads = 0, mFb = 0;
    const now = new Date();

    for (const l of leads) {
      if (!l.phoneNumber && !l.id) continue;
      const updated = await Lead.findOneAndUpdate(
        { $or: [{ id: l.id }, { phoneNumber: l.phoneNumber }] },
        { ...l, updatedAt: now },
        { upsert: true, new: true }
      );
      if (updated) mLeads++;
    }
    for (const f of feedbacks) {
      const exists = await Feedback.findOne({ $or: [{ id: f.id }, { leadId: f.leadId, timestamp: f.timestamp }] });
      if (!exists) {
        await Feedback.create({ ...f, timestamp: f.timestamp || now });
        mFb++;
      }
    }
    for (const a of agents) {
      await Agent.findOneAndUpdate({ $or: [{ id: a.id }, { phoneNumber: a.phoneNumber }] }, a, { upsert: true });
    }

    res.json({ success: true, serverTime: Date.now(), mergedLeads: mLeads, mergedFeedbacks: mFb, totalServerLeads: await Lead.countDocuments() });
  } catch (err) { res.status(500).json({ success: false, error: err.message }); }
});

app.get('/api/leads', async (req, res) => {
  const limit = parseInt(req.query.limit) || 100;
  const leads = await Lead.find({}).limit(limit);
  res.json({ success: true, total: await Lead.countDocuments(), leads });
});

app.post('/api/leads/:id/status', async (req, res) => {
  const { id } = req.params;
  const { status, agentId, agentName, notes } = req.body;
  const lead = await Lead.findOne({ id });
  if (!lead) return res.status(404).json({ success: false, message: 'Lead not found' });

  const prevStatus = lead.status;
  lead.status = status || lead.status;
  lead.updatedAt = new Date();
  if (agentId) lead.assignedAgentId = agentId;
  if (agentName) lead.assignedAgentName = agentName;
  await lead.save();

  if (notes) {
    await Feedback.create({
      id: 'fb_' + Date.now(),
      leadId: lead.id,
      agentId,
      agentName,
      timestamp: new Date(),
      disposition: `Status changed to ${lead.status}`,
      notes,
      previousStatus: prevStatus,
      newStatus: lead.status
    });
  }
  res.json({ success: true, lead });
});

app.get('/api/agents', async (req, res) => {
  res.json({ success: true, agents: await Agent.find({}) });
});

app.post('/api/agents', async (req, res) => {
  const agent = await Agent.create(req.body);
  res.json({ success: true, agent });
});

app.delete('/api/leads', async (req, res) => {
  await Lead.deleteMany({});
  await Feedback.deleteMany({});
  res.json({ success: true, message: 'Store cleared' });
});

app.get('/api/logs', (req, res) => {
  res.json({ success: true, logs: webhookLogs.slice(0, 50) });
});

app.post('/api/test-lead', async (req, res) => {
  const lead = await Lead.create({
    id: 'test_' + Date.now(),
    source: req.body.source || 'Meta Lead Ads (Test)',
    name: req.body.name || 'Rahul Sharma',
    phone: req.body.phone || '+91 98765 43210',
    phoneNumber: req.body.phone || '+91 98765 43210',
    email: req.body.email || 'rahul.sharma@example.com',
    message: req.body.message || 'Interested in 3 BHK Luxury Apartment',
    createdAt: new Date(),
    status: 'NEW',
    priority: 'HOT'
  });
  res.json({ success: true, lead });
});

app.get('/', (req, res) => {
  const host = req.get('host');
  const protocol = req.protocol === 'https' || req.get('x-forwarded-proto') === 'https' ? 'https' : 'http';
  const fullWebhookUrl = `${protocol}://${host}/webhook`;
  res.send(`<h1 style="font-family:sans-serif;">⚡ LeadPulse MongoDB Backend Online</h1><p style="font-family:sans-serif;">Webhook URL: <b>${fullWebhookUrl}</b></p><p style="font-family:sans-serif;">Verify Token: <b>${VERIFY_TOKEN}</b></p>`);
});

const server = app.listen(PORT, () => {
  console.log(`🚀 LeadPulse MongoDB Backend running on port ${PORT}`);
});
