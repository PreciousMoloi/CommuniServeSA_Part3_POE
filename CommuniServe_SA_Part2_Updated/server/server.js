const express = require('express');
const cors = require('cors');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const app = express();
const PORT = process.env.PORT || 3000;
const SECRET = process.env.JWT_SECRET || 'CHANGE_THIS_IN_PRODUCTION';
app.use(cors()); app.use(express.json({limit:'1mb'}));

const users = [];
const services = [
 {id:'s1',title:'Community Plumbing Services',category:'Plumber',description:'Emergency and maintenance plumbing for homes and small businesses.',phone:'',rating:4.8,distanceKm:2.1,verified:true},
 {id:'s2',title:'Reliable Electrical Repairs',category:'Electrician',description:'Residential electrical repairs and installations.',phone:'',rating:4.6,distanceKm:3.4,verified:true},
 {id:'s3',title:'Maths Tutor',category:'Tutor',description:'One-to-one tutoring for school learners.',phone:'',rating:4.9,distanceKm:1.8,verified:true}
];
const events = [
 {id:'e1',title:'Community Clean-up',category:'Community',description:'Join neighbours for a Saturday clean-up.',venue:'Local Community Hall',dateTime:'Saturday 09:00',distanceKm:1.2,verified:true,latitude:null,longitude:null,organiser:'CommuniServe Community'},
 {id:'e2',title:'Youth Coding Meetup',category:'Education',description:'A beginner-friendly coding and technology session.',venue:'Innovation Hub',dateTime:'Sunday 13:00',distanceKm:4.0,verified:true,latitude:null,longitude:null,organiser:'CommuniServe Community'}
];
const reviews = [];
const rsvps = [];
const pending = [];
const resetRequests = new Map();

function auth(req,res,next){
 const h=req.headers.authorization||''; const token=h.startsWith('Bearer ')?h.slice(7):null;
 if(!token) return res.status(401).json({message:'Authentication required'});
 try{ req.user=jwt.verify(token,SECRET); next(); }catch(e){ return res.status(401).json({message:'Invalid or expired token'}); }
}
function userView(u){ return {id:u.id,name:u.name,email:u.email,language:u.language,notifications:u.notifications,radius:u.radius,contributions:u.contributions,latitude:u.latitude??null,longitude:u.longitude??null}; }

app.get('/health',(req,res)=>res.json({status:'ok',name:'CommuniServe SA API',version:'2.0'}));
app.post('/api/auth/register',async(req,res)=>{
 const {name,email,password}=req.body||{};
 if(!name||!email||!password||password.length<8) return res.status(400).json({message:'Name, valid email and 8+ character password required'});
 if(users.some(u=>u.email===email.toLowerCase())) return res.status(409).json({message:'Email already registered'});
 const u={id:crypto.randomUUID(),name,email:email.toLowerCase(),passwordHash:await bcrypt.hash(password,12),language:'English',notifications:true,radius:10,contributions:0,latitude:null,longitude:null}; users.push(u);
 console.log(`[REGISTER] New user stored: id=${u.id} email=${u.email} name=${u.name}`);
 const token=jwt.sign({id:u.id,email:u.email},SECRET,{expiresIn:'24h'}); res.status(201).json({token,user:userView(u)});
});
app.post('/api/auth/forgot-password',async(req,res)=>{
 const email=String(req.body?.email||'').toLowerCase();
 const u=users.find(x=>x.email===email);
 if(u){ resetRequests.set(email, Date.now()+15*60*1000); }
 // Deliberately generic response prevents account enumeration. A production deployment should send a time-limited email link.
 res.json({message:'If the account exists, a password-reset link will be sent.'});
});
app.post('/api/auth/change-password',auth,async(req,res)=>{
 const u=users.find(x=>x.id===req.user.id); const {currentPassword,newPassword}=req.body||{};
 if(!u) return res.status(404).json({message:'User not found'});
 if(!newPassword || newPassword.length<8) return res.status(400).json({message:'New password must be at least 8 characters'});
 if(!(await bcrypt.compare(currentPassword||'',u.passwordHash))) return res.status(401).json({message:'Current password is incorrect'});
 u.passwordHash=await bcrypt.hash(newPassword,12); res.json({message:'Password changed successfully'});
});
app.post('/api/auth/login',async(req,res)=>{
 const {email,password}=req.body||{}; const u=users.find(x=>x.email===String(email||'').toLowerCase());
 if(!u || !(await bcrypt.compare(password||'',u.passwordHash))) return res.status(401).json({message:'Incorrect email or password'});
 console.log(`[LOGIN] User authenticated: id=${u.id} email=${u.email}`);
 const token=jwt.sign({id:u.id,email:u.email},SECRET,{expiresIn:'24h'}); res.json({token,user:userView(u)});
});
app.get('/api/services',(req,res)=>{const q=String(req.query.q||'').toLowerCase();const r=Number(req.query.radius||50);res.json(services.filter(s=>(!q||(s.title+' '+s.category+' '+s.description).toLowerCase().includes(q))&&s.distanceKm<=r));});
app.get('/api/services/:id',(req,res)=>{const s=services.find(x=>x.id===req.params.id);s?res.json(s):res.status(404).json({message:'Service not found'});});
app.post('/api/services',auth,(req,res)=>{const {title,category,description,phone,latitude,longitude}=req.body||{};if(!title||!category)return res.status(400).json({message:'Title and category required'});const s={id:'s'+Date.now(),title,category,description:description||'',phone:phone||'',rating:0,distanceKm:0,verified:false,latitude:latitude??null,longitude:longitude??null};services.push(s);pending.push({type:'service',id:s.id,status:'PENDING'});console.log(`[SERVICE CREATED] id=${s.id} title="${s.title}" by user=${req.user.email} status=PENDING`);res.status(201).json(s);});
app.get('/api/pending', auth, (req, res) => {
  res.json(pending.filter(p => p.status === 'PENDING'));
});

app.post('/api/pending/:id/approve', auth, (req, res) => {
  const item = pending.find(p => p.id === req.params.id);
  if (!item) return res.status(404).json({ message: 'Not found' });
  item.status = 'APPROVED';
  if (item.type === 'service') {
    const svc = services.find(s => s.id === item.id);
    if (svc) svc.verified = true;
  }
  if (item.type === 'event') {
    const evt = events.find(e => e.id === item.id);
    if (evt) evt.verified = true;
  }
  res.json(item);
});
app.get('/api/events',(req,res)=>{const q=String(req.query.q||'').toLowerCase();const r=Number(req.query.radius||50);res.json(events.filter(e=>(!q||(e.title+' '+e.category+' '+e.description).toLowerCase().includes(q))&&e.distanceKm<=r));});
app.get('/api/events/:id',(req,res)=>{const e=events.find(x=>x.id===req.params.id);e?res.json(e):res.status(404).json({message:'Event not found'});});
app.post('/api/events',auth,(req,res)=>{const {title,category,description,phone,latitude,longitude}=req.body||{};if(!title||!category)return res.status(400).json({message:'Title and category required'});const e={id:'e'+Date.now(),title,category,description:description||'',venue:phone||'',dateTime:'To be confirmed',distanceKm:0,verified:false,latitude:latitude??null,longitude:longitude??null,organiser:req.user.email};events.push(e);pending.push({type:'event',id:e.id,status:'PENDING'});console.log(`[EVENT CREATED] id=${e.id} title="${e.title}" by user=${req.user.email} status=PENDING`);res.status(201).json(e);});
app.get('/api/reviews/:serviceId',(req,res)=>res.json(reviews.filter(r=>r.serviceId===req.params.serviceId && r.moderated !== false)));
app.post('/api/events/:id/rsvp',auth,(req,res)=>{
 const event=events.find(e=>e.id===req.params.id); if(!event) return res.status(404).json({message:'Event not found'});
 if(!rsvps.some(r=>r.eventId===event.id&&r.userId===req.user.id)) rsvps.push({eventId:event.id,userId:req.user.id,createdAt:new Date().toISOString()});
 res.status(201).json({message:'RSVP saved'});
});
app.post('/api/reviews/:serviceId',auth,(req,res)=>{const rating=Number(req.body?.rating);const text=String(req.body?.text||'').trim();if(rating<1||rating>5||!text)return res.status(400).json({message:'Rating 1-5 and review text required'});const r={id:crypto.randomUUID(),serviceId:req.params.serviceId,rating,text,author:req.user.email};reviews.push(r);const u=users.find(x=>x.id===req.user.id);if(u)u.contributions++;console.log(`[REVIEW STORED] id=${r.id} service=${r.serviceId} rating=${r.rating} author=${r.author}`);res.status(201).json(r);});
app.get('/api/users/profile',auth,(req,res)=>{const u=users.find(x=>x.id===req.user.id);u?res.json(userView(u)):res.status(404).json({message:'User not found'});});
app.put('/api/users/profile',auth,(req,res)=>{const u=users.find(x=>x.id===req.user.id);if(!u)return res.status(404).json({message:'User not found'});if(req.body.language)u.language=req.body.language;if(typeof req.body.notifications==='boolean')u.notifications=req.body.notifications;if(req.body.radius)u.radius=Math.min(50,Math.max(1,Number(req.body.radius)));if(req.body.name)u.name=String(req.body.name).trim();if(req.body.latitude!==undefined)u.latitude=Number(req.body.latitude);if(req.body.longitude!==undefined)u.longitude=Number(req.body.longitude);res.json(userView(u));});
app.listen(PORT,()=>console.log(`CommuniServe SA API running on port ${PORT}`));
