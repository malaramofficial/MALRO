import { useCallback, useEffect, useMemo, useState } from 'react'
import { supabase } from './lib/supabase'

type Row = Record<string, any>
const role = import.meta.env.VITE_APP_ROLE as string
const config: Record<string, { title: string; subtitle: string; accent: string; tabs: string[] }> = {
  customer: { title: 'MALRO Customer', subtitle: 'बाड़मेर का अपना बाज़ार', accent: '#173b62', tabs: ['बाज़ार', 'मेरे ऑर्डर', 'खाता'] },
  seller: { title: 'MALRO Seller', subtitle: 'दुकान और ऑर्डर प्रबंधन', accent: '#17665b', tabs: ['डैशबोर्ड', 'उत्पाद', 'ऑर्डर', 'दुकान'] },
  delivery: { title: 'MALRO Delivery', subtitle: 'डिलीवरी संचालन', accent: '#72502d', tabs: ['मेरे काम', 'इतिहास', 'खाता'] },
  admin: { title: 'MALRO Admin', subtitle: 'प्लेटफ़ॉर्म संचालन', accent: '#56356c', tabs: ['अवलोकन', 'दुकानें', 'उत्पाद', 'ऑर्डर'] },
}
const money = (p: number) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format((p || 0) / 100)
const errText = (e: any) => e?.message || 'कुछ गलत हुआ। दोबारा कोशिश करें।'

export default function App() {
  const meta = config[role] || config.customer
  const [session, setSession] = useState<any>(null)
  const [profile, setProfile] = useState<Row | null>(null)
  const [phone, setPhone] = useState('+91')
  const [otp, setOtp] = useState('')
  const [otpSent, setOtpSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [tab, setTab] = useState(0)
  const [products, setProducts] = useState<Row[]>([])
  const [shops, setShops] = useState<Row[]>([])
  const [orders, setOrders] = useState<Row[]>([])
  const [jobs, setJobs] = useState<Row[]>([])
  const [cart, setCart] = useState<Record<string, number>>({})
  const [address, setAddress] = useState({ name: '', phone: '', address_line: '', locality: 'Barmer', pincode: '' })
  const [shopForm, setShopForm] = useState({ name: '', phone: '', address: '', locality: 'Barmer' })
  const [productForm, setProductForm] = useState({ name: '', description: '', category: 'किराना', price: '', stock: '' })
  const [activeShop, setActiveShop] = useState('')

  const refresh = useCallback(async (userId?: string) => {
    if (!userId) return
    if (role === 'customer') {
      const [p, o] = await Promise.all([
        supabase.from('products').select('id,shop_id,name,description,category,price_paise,stock_quantity,image_path').eq('status', 'active').gt('stock_quantity', 0).order('created_at', { ascending: false }),
        supabase.from('orders').select('id,status,total_paise,created_at,delivery_address,shop_id').eq('customer_id', userId).order('created_at', { ascending: false }),
      ])
      if (p.error) throw p.error
      if (o.error) throw o.error
      setProducts(p.data || [])
      setOrders(o.data || [])
    } else if (role === 'seller') {
      const s = await supabase.from('shops').select('id,name,address,locality,status,phone').eq('owner_id', userId).order('created_at', { ascending: false })
      if (s.error) throw s.error
      setShops(s.data || [])
      const ids = (s.data || []).map((x: Row) => x.id)
      if (ids.length) {
        const [p, o] = await Promise.all([
          supabase.from('products').select('*').in('shop_id', ids).order('created_at', { ascending: false }),
          supabase.from('orders').select('id,status,total_paise,created_at,shop_id,delivery_address').in('shop_id', ids).order('created_at', { ascending: false }),
        ])
        if (p.error) throw p.error
        if (o.error) throw o.error
        setProducts(p.data || [])
        setOrders(o.data || [])
        if (!activeShop || !ids.includes(activeShop)) setActiveShop(ids[0])
      } else { setProducts([]); setOrders([]) }
    } else if (role === 'delivery') {
      const j = await supabase.from('delivery_jobs').select('id,order_id,pickup_at,delivered_at,last_latitude,last_longitude').eq('delivery_user_id', userId).order('created_at', { ascending: false })
      if (j.error) throw j.error
      setJobs(j.data || [])
    } else if (role === 'admin') {
      const [s, o, p] = await Promise.all([
        supabase.from('shops').select('id,name,owner_id,address,locality,status,created_at').order('created_at', { ascending: false }),
        supabase.from('orders').select('id,status,total_paise,created_at,shop_id,customer_id').order('created_at', { ascending: false }).limit(100),
        supabase.from('products').select('id,name,category,price_paise,stock_quantity,status,shop_id').order('created_at', { ascending: false }).limit(200),
      ])
      if (s.error) throw s.error
      if (o.error) throw o.error
      if (p.error) throw p.error
      setShops(s.data || [])
      setOrders(o.data || [])
      setProducts(p.data || [])
    }
  }, [activeShop])

  const loadSession = useCallback(async (s: any) => {
    setSession(s)
    if (!s?.user) { setProfile(null); return }
    const { data, error } = await supabase.from('profiles').select('id,full_name,phone,account_role').eq('id', s.user.id).single()
    if (error) throw error
    setProfile(data)
    await refresh(s.user.id)
  }, [refresh])

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => { if (data.session) loadSession(data.session).catch(e => setNotice(errText(e))) })
    const { data: listener } = supabase.auth.onAuthStateChange((_event, s) => {
      if (!s) { setSession(null); setProfile(null) }
      else setSession(s)
    })
    return () => listener.subscription.unsubscribe()
  }, [loadSession])

  useEffect(() => {
    if (!session?.user?.id) return
    const tables = role === 'customer' ? ['products', 'orders'] : role === 'seller' ? ['shops', 'products', 'orders'] : role === 'delivery' ? ['delivery_jobs', 'orders'] : ['shops', 'products', 'orders']
    const channel = supabase.channel('malro-' + role + '-' + session.user.id)
    tables.forEach(table => channel.on('postgres_changes', { event: '*', schema: 'public', table }, () => refresh(session.user.id).catch(e => setNotice(errText(e)))))
    channel.subscribe()
    return () => { supabase.removeChannel(channel) }
  }, [session?.user?.id, refresh])

  const run = async (fn: () => Promise<void>) => {
    setBusy(true); setNotice('')
    try { await fn() } catch (e) { setNotice(errText(e)) } finally { setBusy(false) }
  }
  const sendOtp = () => run(async () => {
    const normalized = phone.replace(/[\s-]/g, '')
    if (!/^\+[1-9][0-9]{7,14}$/.test(normalized)) throw new Error('मोबाइल नंबर देश कोड सहित लिखें, जैसे +919876543210')
    const { error } = await supabase.auth.signInWithOtp({ phone: normalized })
    if (error) throw error
    setPhone(normalized); setOtpSent(true); setNotice('OTP भेज दिया गया है।')
  })
  const verifyOtp = () => run(async () => {
    const { data, error } = await supabase.auth.verifyOtp({ phone: phone.replace(/[\s-]/g, ''), token: otp.trim(), type: 'sms' })
    if (error) throw error
    await loadSession(data.session)
  })
  const signOut = () => run(async () => { await supabase.auth.signOut(); setSession(null); setProfile(null) })

  if (!session) return <main className="auth-wrap"><section className="auth-card"><div className="brand-mark">M</div><p className="eyebrow">MALRO • BARMER</p><h1>{meta.title}</h1><p className="muted">{meta.subtitle}</p><label>मोबाइल नंबर</label><input value={phone} onChange={e => setPhone(e.target.value)} placeholder="+919876543210" inputMode="tel" />{otpSent && <><label>SMS OTP</label><input value={otp} onChange={e => setOtp(e.target.value)} placeholder="6 अंकों का OTP" inputMode="numeric" maxLength={6} /></>}{!otpSent ? <button disabled={busy} onClick={sendOtp}>{busy ? 'भेज रहे हैं…' : 'OTP भेजें'}</button> : <button disabled={busy || otp.trim().length < 6} onClick={verifyOtp}>{busy ? 'जाँच रहे हैं…' : 'OTP सत्यापित करें'}</button>}{notice && <p className="notice">{notice}</p>}<p className="tiny">सुरक्षित लॉगिन • Supabase OTP</p></section></main>

  const roleAllowed = role === 'customer' ? profile?.account_role === 'customer'
    : role === 'seller' ? ['seller_pending', 'seller'].includes(profile?.account_role)
    : role === 'delivery' ? profile?.account_role === 'delivery'
    : profile?.account_role === 'admin' && session.user.app_metadata?.role === 'admin'

  if (!roleAllowed) return <main className="auth-wrap"><section className="auth-card"><div className="brand-mark">M</div><h1>इस ऐप की अनुमति नहीं है</h1><p className="muted">इस मोबाइल खाते की भूमिका {meta.title} के लिए अधिकृत नहीं है। भूमिका केवल MALRO के अधिकृत सर्वर द्वारा दी जा सकती है।</p><p className="notice">वर्तमान भूमिका: {profile?.account_role || 'अज्ञात'}</p><button className="secondary" onClick={signOut}>दूसरे खाते से लॉगिन करें</button></section></main>

  const addToCart = (id: string) => setCart(c => ({ ...c, [id]: Math.min((c[id] || 0) + 1, products.find(p => p.id === id)?.stock_quantity || 1) }))
  const cartItems = products.filter(p => cart[p.id])
  const cartTotal = cartItems.reduce((n, p) => n + p.price_paise * cart[p.id], 0)
  const checkout = () => run(async () => {
    if (!cartItems.length) throw new Error('कार्ट खाली है')
    const shopId = cartItems[0].shop_id
    if (cartItems.some(p => p.shop_id !== shopId)) throw new Error('एक ऑर्डर में एक ही दुकान के उत्पाद चुनें')
    const { data, error } = await supabase.rpc('place_customer_order', {
      p_shop_id: shopId,
      p_delivery_address: address,
      p_items: cartItems.map(p => ({ product_id: p.id, quantity: cart[p.id] })),
    })
    if (error) throw error
    setCart({}); setTab(1); setNotice('ऑर्डर दर्ज हुआ: ' + String(data).slice(0, 8)); await refresh(session.user.id)
  })

  const applySeller = () => run(async () => {
    const { error } = await supabase.functions.invoke('request-seller-onboarding', { body: {} })
    if (error) throw error
    await loadSession(session)
    setNotice('विक्रेता आवेदन शुरू हो गया है। अब दुकान का विवरण भरें।')
  })
  const createShop = () => run(async () => {
    const { error } = await supabase.from('shops').insert({ owner_id: session.user.id, ...shopForm })
    if (error) throw error
    setShopForm({ name: '', phone: '', address: '', locality: 'Barmer' }); await refresh(session.user.id)
  })
  const addProduct = () => run(async () => {
    if (!activeShop) throw new Error('पहले स्वीकृत दुकान चुनें')
    const { error } = await supabase.from('products').insert({ shop_id: activeShop, ...productForm, price_paise: Math.round(Number(productForm.price) * 100), stock_quantity: Number(productForm.stock), status: 'draft' })
    if (error) throw error
    setProductForm({ name: '', description: '', category: 'किराना', price: '', stock: '' }); await refresh(session.user.id)
  })
  const reviewShop = (id: string, status: 'approved' | 'rejected') => run(async () => {
    const { error } = await supabase.functions.invoke('admin-review-shop', { body: { shop_id: id, status } })
    if (error) throw error
    await refresh(session.user.id)
  })
  const moderateProduct = (id: string, status: 'active' | 'rejected' | 'hidden') => run(async () => {
    const { error } = await supabase.from('products').update({ status }).eq('id', id)
    if (error) throw error
    await refresh(session.user.id)
  })
  const cancelOrder = (id: string) => run(async () => {
    const { error } = await supabase.rpc('cancel_customer_order', { p_order_id: id })
    if (error) throw error
    await refresh(session.user.id)
  })

  return <div className="app-shell" style={{ '--accent': meta.accent } as any}>
    <header className="topbar"><div><div className="wordmark">MALRO</div><div className="muted small">{meta.subtitle}</div></div><div className="user-pill">{profile?.full_name || session.user.phone || 'लॉगिन'}<button className="text-button" onClick={signOut}>बाहर</button></div></header>
    <main className="content"><div className="page-heading"><div><p className="eyebrow">{role.toUpperCase()} WORKSPACE</p><h1>{meta.tabs[tab]}</h1></div><span className="live-dot">LIVE DATA</span></div>
    {notice && <div className="notice banner">{notice}<button className="text-button" onClick={() => setNotice('')}>×</button></div>}
    {role === 'customer' && tab === 0 && <><div className="hero"><div><p className="eyebrow">LOCAL MARKETPLACE</p><h2>बाड़मेर का बाज़ार,<br/>अब आपकी स्क्रीन पर</h2><p>स्वीकृत दुकानों के उपलब्ध उत्पाद</p></div><div className="hero-mark">M</div></div><div className="product-grid">{products.map(p => <article className="product-card" key={p.id}><div className="product-image">M</div><div className="card-body"><span className="tag">{p.category}</span><h3>{p.name}</h3><p className="muted">{p.description || 'स्थानीय उत्पाद'}</p><div className="card-foot"><strong>{money(p.price_paise)}</strong><button onClick={() => addToCart(p.id)}>कार्ट में जोड़ें</button></div><small>स्टॉक: {p.stock_quantity}</small></div></article>)}</div>{!products.length && <Empty text="अभी कोई उपलब्ध उत्पाद नहीं है। Seller ऐप से स्वीकृत दुकान में उत्पाद प्रकाशित होने पर यहाँ दिखेंगे।" />}</>}
    {role === 'customer' && tab === 1 && <><section className="panel"><h2>आपका कार्ट</h2>{cartItems.map(p => <div className="list-row" key={p.id}><span>{p.name} × {cart[p.id]}</span><strong>{money(p.price_paise * cart[p.id])}</strong></div>)}<div className="list-row total"><strong>कुल</strong><strong>{money(cartTotal)}</strong></div><h3>डिलीवरी पता</h3><div className="form-grid">{(['name','phone','address_line','locality','pincode'] as const).map(k => <label key={k}>{({name:'नाम',phone:'मोबाइल',address_line:'पूरा पता',locality:'इलाका',pincode:'पिनकोड'} as any)[k]}<input value={address[k]} onChange={e => setAddress(a => ({...a,[k]:e.target.value}))}/></label>)}</div><button disabled={busy || !cartItems.length} onClick={checkout}>COD ऑर्डर करें</button></section></>}
    {role === 'customer' && tab === 2 && <section className="panel"><h2>मेरे ऑर्डर</h2>{orders.map(o => <div className="list-row" key={o.id}><div><strong>#{o.id.slice(0,8)}</strong><p className="muted">{o.status} • {new Date(o.created_at).toLocaleString('hi-IN')}</p></div><div><strong>{money(o.total_paise)}</strong>{o.status === 'placed' && <button className="secondary" onClick={() => cancelOrder(o.id)}>रद्द करें</button>}</div></div>)}{!orders.length && <Empty text="अभी कोई ऑर्डर नहीं है।" />}</section>}

    {role === 'seller' && tab === 0 && <><div className="metric-grid"><Metric label="दुकानें" value={shops.length}/><Metric label="उत्पाद" value={products.length}/><Metric label="ऑर्डर" value={orders.length}/></div><section className="panel"><h2>आपकी दुकानें</h2>{shops.map(s => <div className="list-row" key={s.id}><div><strong>{s.name}</strong><p className="muted">{s.locality} • {s.status}</p></div><button className="secondary" onClick={() => setActiveShop(s.id)}>चुनें</button></div>)}{!shops.length && <Empty text="दुकान का आवेदन शुरू करने के लिए दुकान टैब खोलें।" />}</section></>}
    {role === 'seller' && tab === 1 && <section className="panel"><h2>उत्पाद प्रबंधन</h2><label>दुकान<select value={activeShop} onChange={e => setActiveShop(e.target.value)}>{shops.map(s => <option key={s.id} value={s.id}>{s.name} ({s.status})</option>)}</select></label><div className="form-grid">{(['name','description','category','price','stock'] as const).map(k => <label key={k}>{k}<input value={productForm[k]} onChange={e => setProductForm(f => ({...f,[k]:e.target.value}))}/></label>)}</div><button disabled={busy || shops.find(s => s.id === activeShop)?.status !== 'approved'} onClick={addProduct}>उत्पाद ड्राफ्ट जोड़ें</button><p className="muted small">नए उत्पाद ड्राफ्ट के रूप में बनेंगे। उन्हें सक्रिय करने के लिए वर्तमान स्वीकृति/मॉडरेशन नियम लागू होंगे।</p>{products.map(p => <div className="list-row" key={p.id}><span>{p.name} • {p.status}</span><strong>{money(p.price_paise)}</strong></div>)}</section>}
    {role === 'seller' && tab === 2 && <section className="panel"><h2>दुकान के ऑर्डर</h2>{orders.map(o => <div className="list-row" key={o.id}><span>#{o.id.slice(0,8)} • {o.status}</span><strong>{money(o.total_paise)}</strong></div>)}{!orders.length && <Empty text="अभी कोई ऑर्डर नहीं है।" />}<p className="notice">ऑर्डर स्वीकार/पैक करने के लिए सुरक्षित सर्वर एक्शन अभी बनाना बाकी है।</p></section>}
    {role === 'seller' && tab === 3 && <section className="panel"><h2>दुकान आवेदन</h2>{profile?.account_role === 'customer' && <button onClick={applySeller}>विक्रेता आवेदन शुरू करें</button>}{profile?.account_role === 'seller_pending' && <><p className="notice">आपका विक्रेता आवेदन लंबित है। दुकान विवरण भरें।</p><div className="form-grid">{(['name','phone','address','locality'] as const).map(k => <label key={k}>{k}<input value={shopForm[k]} onChange={e => setShopForm(f => ({...f,[k]:e.target.value}))}/></label>)}</div><button onClick={createShop}>दुकान आवेदन भेजें</button></>}{profile?.account_role === 'seller' && <p>विक्रेता खाता सक्रिय है। दुकान की स्वीकृति स्थिति डैशबोर्ड में देखें।</p>}</section>}

    {role === 'delivery' && tab === 0 && <section className="panel"><h2>आपको दिए गए काम</h2>{jobs.filter(j => !j.delivered_at).map(j => <div className="list-row" key={j.id}><div><strong>ऑर्डर #{j.order_id.slice(0,8)}</strong><p className="muted">पिकअप: {j.pickup_at ? new Date(j.pickup_at).toLocaleString('hi-IN') : 'लंबित'}</p></div><span className="tag">असाइन</span></div>)}{!jobs.filter(j => !j.delivered_at).length && <Empty text="अभी कोई डिलीवरी असाइन नहीं है।" />}<p className="notice">डिलीवरी स्टेटस, OTP और GPS अपडेट के सुरक्षित सर्वर फ़ंक्शन अभी उपलब्ध नहीं हैं।</p></section>}
    {role === 'delivery' && tab === 1 && <section className="panel"><h2>डिलीवरी इतिहास</h2>{jobs.filter(j => j.delivered_at).map(j => <div className="list-row" key={j.id}>ऑर्डर #{j.order_id.slice(0,8)}<strong>पूरा</strong></div>)}</section>}
    {role === 'delivery' && tab === 2 && <section className="panel"><h2>खाता</h2><p>{session.user.phone}</p><p>भूमिका: Delivery Partner</p><button className="secondary" onClick={signOut}>लॉगआउट</button></section>}

    {role === 'admin' && tab === 0 && <div className="metric-grid"><Metric label="दुकानें" value={shops.length}/><Metric label="लंबित दुकानें" value={shops.filter(s => s.status === 'pending').length}/><Metric label="कुल ऑर्डर" value={orders.length}/></div>}
    {role === 'admin' && tab === 1 && <section className="panel"><h2>दुकान समीक्षा</h2>{shops.map(s => <div className="list-row" key={s.id}><div><strong>{s.name}</strong><p className="muted">{s.locality} • {s.status}</p></div>{s.status === 'pending' ? <div className="actions"><button onClick={() => reviewShop(s.id,'approved')}>स्वीकृत</button><button className="danger" onClick={() => reviewShop(s.id,'rejected')}>अस्वीकृत</button></div> : <span className="tag">{s.status}</span>}</div>)}{!shops.length && <Empty text="कोई दुकान आवेदन नहीं है।" />}</section>}
    {role === 'admin' && tab === 2 && <section className="panel"><h2>उत्पाद समीक्षा</h2>{products.map(p => <div className="list-row" key={p.id}><div><strong>{p.name}</strong><p className="muted">{p.category} • {money(p.price_paise)} • स्टॉक {p.stock_quantity} • {p.status}</p></div>{p.status !== 'active' ? <button onClick={() => moderateProduct(p.id,'active')}>सक्रिय करें</button> : <button className="secondary" onClick={() => moderateProduct(p.id,'hidden')}>छिपाएँ</button>}</div>)}{!products.length && <Empty text="कोई उत्पाद समीक्षा के लिए नहीं है।" />}</section>}
    {role === 'admin' && tab === 3 && <section className="panel"><h2>सभी ऑर्डर</h2>{orders.map(o => <div className="list-row" key={o.id}><div><strong>#{o.id.slice(0,8)}</strong><p className="muted">{o.status} • {new Date(o.created_at).toLocaleString('hi-IN')}</p></div><strong>{money(o.total_paise)}</strong></div>)}{!orders.length && <Empty text="अभी कोई ऑर्डर नहीं है।" />}</section>}
    </main><nav className="bottom-nav">{meta.tabs.map((label, i) => <button key={label} className={tab===i?'active':''} onClick={() => setTab(i)}>{label}</button>)}</nav>
    <footer>© MALRO • सुरक्षित, भूमिका-आधारित कार्यक्षेत्र</footer>
  </div>
}

function Metric({label,value}:{label:string;value:number}) { return <div className="metric"><span>{label}</span><strong>{value}</strong></div> }
function Empty({text}:{text:string}) { return <div className="empty">{text}</div> }
