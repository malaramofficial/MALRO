(() => {
  const cfg = window.MALRO_CONFIG || {};
  const root = document.getElementById("app");
  const state = { access:"", refresh:"", expires:0, user:null, applications:[], query:"", page:"dashboard", busy:false, credentials:null, selected:null };
  const esc = (s="") => String(s).replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
  const phoneClean = s => { const p=s.replace(/\s+/g,"").replace(/[^+0-9]/g,""); return /^\d{10}$/.test(p) ? "+91"+p : p; };
  const api = async (path, method="GET", body=null, token=state.access) => {
    const headers = {"apikey":cfg.anonKey,"Accept":"application/json"};
    if(token) headers.Authorization = "Bearer " + token;
    if(body!==null) headers["Content-Type"]="application/json";
    const res = await fetch(cfg.supabaseUrl + path, {method,headers,body:body===null?undefined:JSON.stringify(body)});
    const raw = await res.text();
    let data={}; try{data=raw?JSON.parse(raw):{}}catch{data={message:raw}}
    if(!res.ok) throw new Error(data.msg || data.message || data.error_description || data.error || "सर्वर अनुरोध असफल ("+res.status+")");
    return data;
  };
  const saveSession = data => {
    state.access=data.access_token;state.refresh=data.refresh_token;
    state.expires=Math.floor(Date.now()/1000)+(data.expires_in||3600);
    sessionStorage.setItem("malro_admin_session",JSON.stringify({access:state.access,refresh:state.refresh,expires:state.expires}));
  };
  const clearSession = () => {
    state.access="";state.refresh="";state.expires=0;state.user=null;
    sessionStorage.removeItem("malro_admin_session");
  };
  const checkAdmin = async () => {
    const u=await api("/auth/v1/user");
    if(u?.app_metadata?.role!=="admin") throw new Error("इस नंबर को MALRO Admin की अनुमति नहीं मिली है।");
    state.user=u;return u;
  };
  const restore = async () => {
    try{
      const raw=sessionStorage.getItem("malro_admin_session");if(!raw)return false;
      const s=JSON.parse(raw);state.access=s.access;state.refresh=s.refresh;state.expires=s.expires;
      if(!state.access||!state.refresh) return false;
      if(state.expires < Math.floor(Date.now()/1000)+90){
        const t=await api("/auth/v1/token?grant_type=refresh_token","POST",{refresh_token:state.refresh},"");
        saveSession(t);
      }
      await checkAdmin();return true;
    }catch(e){clearSession();return false;}
  };
  const toast = (message, type="") => {
    let el=document.querySelector(".toast");if(!el){el=document.createElement("div");el.className="toast";document.body.appendChild(el);}
    el.className="toast "+type;el.textContent=message;setTimeout(()=>el.remove(),4200);
  };
  const loadApplications = async () => {
    state.busy=true;render();
    try{
      const rows=await api("/rest/v1/seller_applications?select=id,application_code,seller_code,setup_completed_at,full_name,phone,shop_name,description,address,locality,status,admin_note,submitted_at&order=submitted_at.desc");
      state.applications=Array.isArray(rows)?rows:[];
    }catch(e){toast(e.message,"error");}
    finally{state.busy=false;render();}
  };
  const loginView = () => {
    root.innerHTML=`
      <div class="login-wrap">
        <section class="login-story">
          <div class="brand"><div class="brand-mark small">M</div><span>MALRO</span></div>
          <div><div class="eyebrow">LOCAL MARKETPLACE • BARMER</div><div class="story-title">हर दुकान, हर ऑर्डर — एक जगह से नियंत्रित करें।</div><p class="story-copy">MALRO का सुरक्षित प्रशासनिक कंट्रोल पैनल। केवल अधिकृत Admin ही प्रवेश कर सकते हैं।</p></div>
          <div class="story-foot">Malaram Official • MALRO Admin</div>
        </section>
        <section class="login-side"><form class="login-card" id="loginForm">
          <div class="brand-mark">M</div><h1 style="margin-top:22px">Admin Login</h1><p class="muted">अपना Admin पासवर्ड दर्ज करें।</p>
          <div class="field"><label>Admin पासवर्ड</label><input id="adminPassword" type="password" autocomplete="current-password" placeholder="पासवर्ड दर्ज करें" required maxlength="100" /></div>
          <button class="primary full" id="loginButton" type="submit">सुरक्षित Login करें</button>
          <div id="loginNotice" aria-live="polite"></div>
        </form></section>
      </div>`;
    const form=root.querySelector("#loginForm");
    const input=root.querySelector("#adminPassword");
    const button=root.querySelector("#loginButton");
    const notice=root.querySelector("#loginNotice");
    form.onsubmit=async e=>{
      e.preventDefault();
      button.disabled=true;button.textContent="जाँच हो रही है…";
      notice.innerHTML="";
      try{
        const login=await api("/functions/v1/admin-time-login","POST",{password:input.value},"");
        if(!login.token_hash) throw new Error("सुरक्षित Login token नहीं मिला।");
        const session=await api("/auth/v1/verify","POST",{type:"magiclink",token_hash:login.token_hash},"");
        saveSession(session);
        await checkAdmin();
        await dashboard();
      }catch(err){
        clearSession();
        notice.innerHTML='<div class="notice error">'+esc(err.message)+'</div>';
        button.disabled=false;button.textContent="सुरक्षित Login करें";
      }
    };
  };
  const navButton = (page,icon,label) => `<button data-page="${page}" class="${state.page===page?"active":""}"><span class="nav-icon">${icon}</span>${label}</button>`;
  const shell = () => {
    root.innerHTML=`
      <div class="layout">
        <aside class="sidebar" id="sidebar">
          <div class="side-brand"><div class="brand-mark small">M</div><span>MALRO ADMIN</span></div>
          <div><div class="side-label">WORKSPACE</div><nav class="nav">
            ${navButton("dashboard","⌂","डैशबोर्ड")}
            ${navButton("sellers","▤","विक्रेता आवेदन")}
          </nav></div>
          <div class="side-bottom"><div class="admin-user">लॉगिन: ${esc(state.user?.phone||state.user?.email||"Admin")}</div><button class="nav" id="logout" style="margin-top:12px;width:100%;border:0;background:transparent;color:#e3c37b;text-align:left;padding:10px">↪ लॉगआउट</button></div>
        </aside>
        <main class="main">
          <header class="topbar"><div style="display:flex;align-items:center;gap:12px"><button class="ghost mobile-menu" id="menu">☰</button><div class="top-title">MALRO Control Center</div></div><div class="top-actions"><span class="muted" style="font-size:12px">Administrator</span><div class="avatar">A</div></div></header>
          <section class="content" id="page"></section>
        </main>
      </div>`;
    root.querySelectorAll("[data-page]").forEach(b=>b.onclick=()=>{state.page=b.dataset.page;renderPage();root.querySelector("#sidebar").classList.remove("open");});
    root.querySelector("#logout").onclick=logout;
    root.querySelector("#menu").onclick=()=>root.querySelector("#sidebar").classList.toggle("open");
    renderPage();
  };
  const pending = state.applications.filter(a=>a.status==="pending");
  const dashboardPage = () => {
    const approved=state.applications.filter(a=>a.status==="approved").length;
    const rejected=state.applications.filter(a=>a.status==="rejected").length;
    return `
      <div class="page-head"><div><div class="eyebrow">OVERVIEW</div><h1>नमस्ते, Admin 👋</h1><p>MALRO के आज के संचालन का सारांश।</p></div><button class="ghost" id="refresh">↻ रिफ्रेश</button></div>
      <div class="stats">
        <div class="stat"><span class="stat-icon">▤</span><div class="stat-label">लंबित विक्रेता आवेदन</div><div class="stat-value">${pending.length}</div></div>
        <div class="stat"><span class="stat-icon">✓</span><div class="stat-label">स्वीकृत आवेदन</div><div class="stat-value">${approved}</div></div>
        <div class="stat"><span class="stat-icon">×</span><div class="stat-label">अस्वीकृत आवेदन</div><div class="stat-value">${rejected}</div></div>
      </div>
      <div class="panel"><div class="panel-head"><div><h2>नए विक्रेता आवेदन</h2><small class="muted">मंजूरी से पहले विक्रेता और दुकान की जानकारी जाँचें। मोबाइल नंबर वैकल्पिक है।</small></div><button class="ghost" id="allSellers">सभी आवेदन देखें →</button></div>
      ${pending.length?tableMarkup(pending.slice(0,5)):'<div class="empty"><div class="empty-icon">✓</div>अभी कोई लंबित आवेदन नहीं है।</div>'}</div>`;
  };
  const tableMarkup = rows => `<div class="table-wrap"><table><thead><tr><th>दुकान / विक्रेता</th><th>आवेदन ID</th><th>मोबाइल</th><th>स्थिति</th><th>कार्यवाही</th></tr></thead><tbody>${rows.map(a=>`
    <tr><td><strong>${esc(a.shop_name)}</strong><small>${esc(a.full_name)} • ${esc(a.locality)}</small></td><td>${esc(a.application_code)}${a.seller_code?'<small>Seller ID: '+esc(a.seller_code)+'</small>':''}</td><td>${esc(a.phone||"—")}</td><td><span class="status ${esc(a.status)}">${a.status==="pending"?"लंबित":a.status==="approved"?"स्वीकृत":"अस्वीकृत"}</span></td><td><button class="action" data-open="${esc(a.id)}">विवरण देखें</button></td></tr>`).join("")}</tbody></table></div>`;
  const sellersPage = () => {
    const q=state.query.toLowerCase();
    const rows=state.applications.filter(a=>[a.shop_name,a.full_name,a.phone,a.application_code].some(v=>(v||"").toLowerCase().includes(q)));
    return `
      <div class="page-head"><div><div class="eyebrow">SELLER MANAGEMENT</div><h1>विक्रेता आवेदन</h1><p>दुकान की जानकारी जाँचें और योग्य आवेदन स्वीकृत करें।</p></div><button class="ghost" id="refresh">↻ रिफ्रेश</button></div>
      <div class="panel"><div class="panel-head"><div><h2>सभी आवेदन <span class="muted">(${rows.length})</span></h2></div><input class="search" id="search" placeholder="दुकान, नाम, मोबाइल या ID खोजें" value="${esc(state.query)}" /></div>
      ${rows.length?tableMarkup(rows):'<div class="empty"><div class="empty-icon">⌕</div>कोई आवेदन नहीं मिला।</div>'}</div>`;
  };
  const renderPage = () => {
    const page=root.querySelector("#page");if(!page)return;
    page.innerHTML=state.page==="dashboard"?dashboardPage():sellersPage();
    const refresh=page.querySelector("#refresh");if(refresh)refresh.onclick=loadApplications;
    const all=page.querySelector("#allSellers");if(all)all.onclick=()=>{state.page="sellers";render();};
    const search=page.querySelector("#search");if(search)search.oninput=()=>{state.query=search.value;const pos=search.selectionStart;renderPage();const n=page.querySelector("#search");n.focus();n.setSelectionRange(pos,pos);};
    page.querySelectorAll("[data-open]").forEach(b=>b.onclick=()=>showApplication(b.dataset.open));
  };
  const render = () => {if(!state.user){loginView();return;}shell();};
  const showApplication = id => {
    const a=state.applications.find(x=>x.id===id);if(!a)return;
    state.selected=a;
    const backdrop=document.createElement("div");backdrop.className="modal-backdrop";backdrop.id="applicationModal";
    backdrop.innerHTML=`<div class="modal"><div class="eyebrow">APPLICATION DETAILS</div><h2>${esc(a.shop_name)}</h2><p class="muted">आवेदन ID: <strong>${esc(a.application_code)}</strong></p>
      <div class="details">
        <div><div class="detail-label">विक्रेता का नाम</div><div class="detail-value">${esc(a.full_name)}</div></div>
        <div><div class="detail-label">मोबाइल नंबर</div><div class="detail-value">${esc(a.phone||"नहीं दिया गया")}</div></div>
        <div><div class="detail-label">दुकान का नाम</div><div class="detail-value">${esc(a.shop_name)}</div></div>
        <div><div class="detail-label">क्षेत्र</div><div class="detail-value">${esc(a.locality)}</div></div>
        <div style="grid-column:1/-1"><div class="detail-label">पूरा पता</div><div class="detail-value">${esc(a.address)}</div></div>
        <div style="grid-column:1/-1"><div class="detail-label">दुकान का विवरण</div><div class="detail-value">${esc(a.description||"—")}</div></div>
        <div><div class="detail-label">आवेदन की तारीख</div><div class="detail-value">${new Date(a.submitted_at).toLocaleString("hi-IN")}</div></div>
        <div><div class="detail-label">स्थिति</div><div class="detail-value">${esc(a.status)}</div></div>
        <div><div class="detail-label">Seller अकाउंट</div><div class="detail-value">${a.setup_completed_at?"पासवर्ड सेट • अकाउंट तैयार":a.status==="approved"?"पासवर्ड सेटअप लंबित":"अभी स्वीकृति लंबित"}</div></div>
        ${a.seller_code?'<div><div class="detail-label">Seller ID</div><div class="detail-value">'+esc(a.seller_code)+'</div></div>':''}
      </div>
      <div class="notice">मोबाइल नंबर केवल संपर्क के लिए वैकल्पिक है; इस पंजीकरण में OTP या मोबाइल Login नहीं है। आवेदन स्वीकृत होने पर Seller ID जारी होगी और विक्रेता अपना पासवर्ड स्वयं बनाएगा।</div>
      <div style="display:flex;gap:10px;justify-content:flex-end;margin-top:18px"><button class="ghost" id="closeModal">बंद करें</button>${a.status==="pending"?'<button class="danger" id="reject">अस्वीकृत करें</button><button class="primary" id="approve">स्वीकृत करें</button>':""}</div>
    </div>`;
    document.body.appendChild(backdrop);
    backdrop.querySelector("#closeModal").onclick=()=>backdrop.remove();
    backdrop.onclick=e=>{if(e.target===backdrop)backdrop.remove();};
    const approve=backdrop.querySelector("#approve");if(approve)approve.onclick=()=>approveApplication(a,approve,backdrop);
    const reject=backdrop.querySelector("#reject");if(reject)reject.onclick=()=>rejectApplication(a,reject,backdrop);
  };
  const rejectApplication = async (a,button,backdrop) => {
    const note=prompt("अस्वीकृति का कारण (वैकल्पिक):");
    if(note===null)return;
    if(!confirm("क्या आप यह Seller आवेदन अस्वीकृत करना चाहते हैं?"))return;
    button.disabled=true;button.textContent="अस्वीकृत कर रहे हैं…";
    try{
      await api("/functions/v1/admin-approve-seller","POST",{application_id:a.id,decision:"rejected",admin_note:note});
      state.applications=state.applications.map(x=>x.id===a.id?{...x,status:"rejected",admin_note:note}:x);
      backdrop.remove();renderPage();toast("आवेदन अस्वीकृत कर दिया गया।");
    }catch(e){toast(e.message,"error");button.disabled=false;button.textContent="अस्वीकृत करें";}
  };
  const approveApplication = async (a,button,backdrop) => {
    if(!confirm("क्या आपने विक्रेता और दुकान की जानकारी जाँच ली है? स्वीकृति पर Seller ID जारी होगी और विक्रेता स्वयं पासवर्ड बनाएगा।"))return;
    button.disabled=true;button.textContent="स्वीकृति हो रही है…";
    try{
      const result=await api("/functions/v1/admin-approve-seller","POST",{application_id:a.id,decision:"approved"});
      state.credentials={id:result.seller_code,code:result.application_code};
      state.applications=state.applications.map(x=>x.id===a.id?{...x,status:"approved",seller_code:result.seller_code}:x);
      backdrop.remove();showCredentials();renderPage();
    }catch(e){toast(e.message,"error");button.disabled=false;button.textContent="स्वीकृत करें";}
  };
  const showCredentials = () => {
    const c=state.credentials;if(!c)return;
    const modal=document.createElement("div");modal.className="modal-backdrop";
    modal.innerHTML=`<div class="modal"><div class="eyebrow">SELLER ID ISSUED</div><h2>आवेदन स्वीकृत हो गया ✓</h2><p class="muted">अब विक्रेता Seller ऐप में आवेदन कोड और सुरक्षा कोड से पहचान सत्यापित करके अपना पासवर्ड स्वयं बनाएगा।</p>
      <div class="credential"><div class="detail-label">Seller ID</div><div class="credential-row"><span id="sellerId">${esc(c.id)}</span><button class="copy" data-copy="sellerId">कॉपी</button></div></div>
      <div class="credential"><div class="detail-label">आवेदन ID</div><div class="credential-row"><span id="applicationCode">${esc(c.code)}</span><button class="copy" data-copy="applicationCode">कॉपी</button></div></div>
      <div class="notice">Admin पासवर्ड नहीं बनाता और न ही देखता है। विक्रेता अपने पास सुरक्षित रखे गए सुरक्षा कोड से अकाउंट सेटअप करेगा।</div>
      <button class="primary full" id="done" style="margin-top:16px">समझ गया</button>
    </div>`;
    document.body.appendChild(modal);
    modal.querySelectorAll("[data-copy]").forEach(b=>b.onclick=async()=>{const v=modal.querySelector("#"+b.dataset.copy).textContent;try{await navigator.clipboard.writeText(v);b.textContent="कॉपी हो गया";}catch{toast("कॉपी नहीं हुआ; टेक्स्ट चुनकर कॉपी करें","error");}});
    modal.querySelector("#done").onclick=()=>{modal.remove();state.credentials=null;};
  };
  const logout = async () => {try{await api("/auth/v1/logout","POST",{});}catch{}clearSession();loginView();};
  const dashboard = async () => {await checkAdmin();await loadApplications();state.page="dashboard";render();};
  const start = async () => {
    if(!cfg.supabaseUrl||!cfg.anonKey){root.innerHTML='<div class="boot"><h2>सेटअप अधूरा है</h2><p>Render Environment Variables जाँचें।</p></div>';return;}
    if(await restore()){await dashboard();}else loginView();
  };
  start();
})();