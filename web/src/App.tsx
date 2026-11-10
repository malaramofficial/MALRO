import { useCallback, useEffect, useMemo, useState } from 'react'
import { supabase } from './lib/supabase'

type Row = Record<string, any>
const role = import.meta.env.VITE_APP_ROLE as string
const config: Record<string, { title: string; subtitle: string; accent: string; tabs: string[] }> = {
  customer: { title: 'MALRO Customer', subtitle: 'बाड़मेर का अपना बाज़ार', accent: '#173b62', tabs: ['बाज़ार', 'मेरे ऑर्डर', 'खाता'] },
  seller: { title: 'MALRO Seller', subtitle: 'दुकान और ऑर्डर प्रबंधन', accent: '#17665b', tabs: ['डैशबोर्ड', 'उत्पाद', 'ऑर्डर', 'दुकान'] },
  delivery: { title: 'MALRO Delivery', subtitle: 'डिलीवरी संचालन', accent: '#72502d', tabs: ['मेरे काम', 'इतिहास', 'खाता'] },
  admin: { title: 'MALRO Admin', subtitle: 'प्लेटफ़ॉर्म संचालन', accent: '#56356c', tabs: ['अवलोकन', 'दुकानें', 'ऑर्डर'] },
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
  const [productForm, setProductForm] = useState({ name: '', description: '', category: 'किराना', unit: 'piece', price: '', stock: '' })
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
        if (!activeShop || !ids.includes(activeShop)) {
          setActiveShop(ids[0])
          const firstShop = (s.data || [])[0]
          if (firstShop) setShopForm({ name: firstShop.name || '', phone: firstShop.phone || '', address: firstShop.address || '', locality: firstShop.locality || '' })
        }
      } else { setProducts([]); setOrders([]) }
    } else if (role === 'delivery') {
      const j = await supabase.from('delivery_jobs').select('id,order_id,pickup_at,delivered_at,last_latitude,last_longitude').eq('delivery_user_id', userId).order('created_at', { ascending: false })
      if (j.error) throw j.error
      setJobs(j.data || [])
    } else if (role === 'admin') {
      const [s, o] = await Promise.all([
        supabase.from('shops').select('id,name,owner_id,address,locality,status,created_at').order('created_at', { ascending: false }),
        supabase.from('orders').select('id,status,total_paise,created_at,shop_id,customer_id').order('created_at', { ascending: false }).limit(100),
      ])
      if (s.error) throw s.error
      if (o.error) throw o.error
      setShops(s.data || [])
      setOrders(o.data || [])
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
    const tables = role === 'customer' ? ['products', 'orders'] : role === 'seller' ? ['shops', 'products', 'orders'] : role === 'delivery' ? ['delivery_jobs', 'orders'] : ['shops', 'orders']
    const channel = supabase.channel('malro-' + role + '-' + session.user.id)
    tables.forEach(table => channel.on('postgres_changes', { event: '*', schema: 'public', table }, () => refresh(session.user.id).catch(e => setNotice(errText(e)))))
    channel.subscribe(status => {
      if (status === 'CHANNEL_ERROR' || status === 'TIMED_OUT') setNotice('Live connection कमजोर है; डेटा अपने-आप फिर से जाँचा जाएगा।')
    })
    const syncTimer = window.setInterval(() => refresh(session.user.id).catch(e => setNotice(errText(e))), 20000)
    return () => { window.clearInterval(syncTimer); supabase.removeChannel(channel) }
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

  if (!session && role === 'seller') return <SellerGate />
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
    const { error } = await supabase.from('products').insert({ shop_id: activeShop, name: productForm.name.trim(), description: productForm.description.trim() || null, category: productForm.category.trim(), price_paise: Math.round(Number(productForm.price) * 100), stock_quantity: Number(productForm.stock), status: 'draft' })
    if (error) throw error
    setProductForm({ name: '', description: '', category: 'किराना', unit: 'piece', price: '', stock: '' }); await refresh(session.user.id)
  })
  const publishProduct = (id: string) => run(async () => {
    const shop = shops.find(x => x.id === activeShop)
    if (!shop || shop.status !== 'approved') throw new Error('पहले Admin से स्वीकृत दुकान चुनें।')
    const product = products.find(x => x.id === id && x.shop_id === activeShop)
    if (!product) throw new Error('उत्पाद इस दुकान में नहीं मिला।')
    if (Number(product.stock_quantity) < 1) throw new Error('स्टॉक 1 या उससे अधिक होने पर ही प्रकाशित कर सकते हैं।')
    const { error } = await supabase.from('products').update({ status: 'active' }).eq('id', id).eq('shop_id', activeShop)
    if (error) throw error
    await refresh(session.user.id)
    setNotice('उत्पाद Customer ऐप में दिखाने के लिए प्रकाशित हो गया।')
  })
  const updateShop = () => run(async () => {
    if (!activeShop) throw new Error('पहले दुकान चुनें।')
    if (!shopForm.name.trim() || !shopForm.address.trim() || !shopForm.locality.trim()) throw new Error('दुकान का नाम, पता और इलाका भरें।')
    const { error } = await supabase.from('shops').update({
      name: shopForm.name.trim(),
      phone: shopForm.phone.trim() || null,
      address: shopForm.address.trim(),
      locality: shopForm.locality.trim(),
    }).eq('id', activeShop).eq('owner_id', session.user.id)
    if (error) throw error
    await refresh(session.user.id)
    setNotice('दुकान की जानकारी अपडेट हो गई। Customer ऐप में भी अपडेट दिखाई देगी।')
  })
  const reviewShop = (id: string, status: 'approved' | 'rejected') => run(async () => {
    const { error } = await supabase.functions.invoke('admin-review-shop', { body: { shop_id: id, status } })
    if (error) throw error
    await refresh(session.user.id)
  })
  const updateSellerOrder = (id: string, status: 'accepted' | 'packed') => run(async () => {
    const { error } = await supabase.rpc('seller_update_order_status', { p_order_id: id, p_new_status: status })
    if (error) throw error
    await refresh(session.user.id)
    setNotice(status === 'accepted' ? 'ऑर्डर स्वीकार कर लिया गया।' : 'ऑर्डर पैक हो गया।')
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
    {role === 'seller' && tab === 1 && <section className="panel"><h2>उत्पाद प्रबंधन</h2><label>दुकान<select value={activeShop} onChange={e => setActiveShop(e.target.value)}>{shops.map(s => <option key={s.id} value={s.id}>{s.name} ({s.status})</option>)}</select></label><div className="form-grid"><label>सामान का नाम<input value={productForm.name} onChange={e => setProductForm(f => ({...f,name:e.target.value}))}/></label><label>विवरण<input value={productForm.description} onChange={e => setProductForm(f => ({...f,description:e.target.value}))}/></label><label>श्रेणी<select value={productForm.category} onChange={e => setProductForm(f => ({...f,category:e.target.value}))}>{['किराना','अनाज और दालें','सब्ज़ियाँ','फल','दूध और डेयरी','तेल और घी','मसाले','पेय पदार्थ','स्नैक्स','घरेलू सामान','अन्य'].map(x => <option key={x}>{x}</option>)}</select></label><label>नाप-तौल<select value={productForm.unit} onChange={e => setProductForm(f => ({...f,unit:e.target.value}))}><option value="kg">किलो (kg)</option><option value="litre">लीटर (L)</option><option value="piece">पीस</option></select></label><label>कीमत (चुनी इकाई के अनुसार ₹)<input type="number" min="0.01" step="0.01" value={productForm.price} onChange={e => setProductForm(f => ({...f,price:e.target.value}))}/></label><label>स्टॉक ({productForm.unit === 'kg' ? 'किलो' : productForm.unit === 'litre' ? 'लीटर' : 'पीस'})<input type="number" min="0" step="0.001" value={productForm.stock} onChange={e => setProductForm(f => ({...f,stock:e.target.value}))}/></label></div><button disabled={busy || shops.find(s => s.id === activeShop)?.status !== 'approved'} onClick={addProduct}>उत्पाद ड्राफ्ट जोड़ें</button><p className="muted small">किलो और लीटर वाले सामान की कीमत प्रति किलो/लीटर रखें। Customer 250 ग्राम, 500 ग्राम, 1 किलो या मिलीलीटर के विकल्प चुन सकेगा।</p>{products.filter(p => p.shop_id === activeShop).map(p => <div className="list-row" key={p.id}><div><strong>{p.name}</strong><p className="muted">{p.status === 'active' ? 'Customer ऐप में प्रकाशित' : p.status} • स्टॉक: {p.stock_quantity}</p></div><div className="actions"><strong>{money(p.price_paise)}</strong>{p.status === 'draft' && <button disabled={busy || Number(p.stock_quantity) < 1 || shops.find(s => s.id === activeShop)?.status !== 'approved'} onClick={() => publishProduct(p.id)}>Customer में दिखाएँ</button>}</div></div>)}</section>}
    {role === 'seller' && tab === 2 && <section className="panel"><h2>दुकान के ऑर्डर</h2>{orders.map(o => <div className="list-row" key={o.id}><div><strong>#{o.id.slice(0,8)} • {o.status}</strong><p className="muted">{new Date(o.created_at).toLocaleString('hi-IN')}</p></div><div className="actions"><strong>{money(o.total_paise)}</strong>{o.status === 'placed' && <button disabled={busy} onClick={() => updateSellerOrder(o.id,'accepted')}>स्वीकार करें</button>}{o.status === 'accepted' && <button disabled={busy} onClick={() => updateSellerOrder(o.id,'packed')}>पैक हो गया</button>}</div></div>)}{!orders.length && <Empty text="अभी कोई ऑर्डर नहीं है।" />}<p className="notice">ऑर्डर स्वीकार/पैक करने के लिए सुरक्षित सर्वर एक्शन अभी बनाना बाकी है।</p></section>}
    {role === 'seller' && tab === 3 && <section className="panel"><h2>दुकान प्रबंधन</h2>{profile?.account_role === 'customer' && <button onClick={applySeller}>विक्रेता आवेदन शुरू करें</button>}{profile?.account_role === 'seller_pending' && <><p className="notice">आपका विक्रेता आवेदन लंबित है। दुकान विवरण भरें।</p><div className="form-grid">{(['name','phone','address','locality'] as const).map(k => <label key={k}>{k}<input value={shopForm[k]} onChange={e => setShopForm(f => ({...f,[k]:e.target.value}))}/></label>)}</div><button disabled={busy} onClick={createShop}>दुकान आवेदन भेजें</button></>}{profile?.account_role === 'seller' && <><label>दुकान चुनें<select value={activeShop} onChange={e => { const selected = shops.find(x => x.id === e.target.value); setActiveShop(e.target.value); if (selected) setShopForm({name:selected.name,phone:selected.phone||'',address:selected.address,locality:selected.locality}); }}>{shops.map(x => <option key={x.id} value={x.id}>{x.name} • {x.status}</option>)}</select></label>{activeShop && <><div className="form-grid">{(['name','phone','address','locality'] as const).map(k => <label key={k}>{({name:'दुकान का नाम',phone:'मोबाइल',address:'पूरा पता',locality:'इलाका'} as any)[k]}<input value={shopForm[k]} onChange={e => setShopForm(f => ({...f,[k]:e.target.value}))}/></label>)}</div><button disabled={busy} onClick={updateShop}>दुकान अपडेट करें</button><p className="muted small">केवल अपनी स्वीकृत दुकान की जानकारी बदल सकते हैं। दुकान की स्वीकृति स्थिति नहीं बदलेगी।</p></>}</>}</section>}

    {role === 'delivery' && tab === 0 && <section className="panel"><h2>आपको दिए गए काम</h2>{jobs.filter(j => !j.delivered_at).map(j => <div className="list-row" key={j.id}><div><strong>ऑर्डर #{j.order_id.slice(0,8)}</strong><p className="muted">पिकअप: {j.pickup_at ? new Date(j.pickup_at).toLocaleString('hi-IN') : 'लंबित'}</p></div><span className="tag">असाइन</span></div>)}{!jobs.filter(j => !j.delivered_at).length && <Empty text="अभी कोई डिलीवरी असाइन नहीं है।" />}<p className="notice">डिलीवरी स्टेटस, OTP और GPS अपडेट के सुरक्षित सर्वर फ़ंक्शन अभी उपलब्ध नहीं हैं।</p></section>}
    {role === 'delivery' && tab === 1 && <section className="panel"><h2>डिलीवरी इतिहास</h2>{jobs.filter(j => j.delivered_at).map(j => <div className="list-row" key={j.id}>ऑर्डर #{j.order_id.slice(0,8)}<strong>पूरा</strong></div>)}</section>}
    {role === 'delivery' && tab === 2 && <section className="panel"><h2>खाता</h2><p>{session.user.phone}</p><p>भूमिका: Delivery Partner</p><button className="secondary" onClick={signOut}>लॉगआउट</button></section>}

    {role === 'admin' && tab === 0 && <div className="metric-grid"><Metric label="दुकानें" value={shops.length}/><Metric label="लंबित दुकानें" value={shops.filter(s => s.status === 'pending').length}/><Metric label="कुल ऑर्डर" value={orders.length}/></div>}
    {role === 'admin' && tab === 1 && <section className="panel"><h2>दुकान समीक्षा</h2>{shops.map(s => <div className="list-row" key={s.id}><div><strong>{s.name}</strong><p className="muted">{s.locality} • {s.status}</p></div>{s.status === 'pending' ? <div className="actions"><button onClick={() => reviewShop(s.id,'approved')}>स्वीकृत</button><button className="danger" onClick={() => reviewShop(s.id,'rejected')}>अस्वीकृत</button></div> : <span className="tag">{s.status}</span>}</div>)}{!shops.length && <Empty text="कोई दुकान आवेदन नहीं है।" />}</section>}
    {role === 'admin' && tab === 2 && <section className="panel"><h2>सभी ऑर्डर</h2>{orders.map(o => <div className="list-row" key={o.id}><div><strong>#{o.id.slice(0,8)}</strong><p className="muted">{o.status} • {new Date(o.created_at).toLocaleString('hi-IN')}</p></div><strong>{money(o.total_paise)}</strong></div>)}{!orders.length && <Empty text="अभी कोई ऑर्डर नहीं है।" />}</section>}
    </main><nav className="bottom-nav">{meta.tabs.map((label, i) => <button key={label} className={tab===i?'active':''} onClick={() => setTab(i)}>{label}</button>)}</nav>
    <footer>© MALRO • सुरक्षित, भूमिका-आधारित कार्यक्षेत्र</footer>
  </div>
}


const SELLER_CLAIM_KEY = 'malro_seller_application_claim'

function SellerGate() {
  const [mode, setMode] = useState<'register' | 'status' | 'login' | 'recover'>(() => {
    try { return localStorage.getItem(SELLER_CLAIM_KEY) ? 'status' : 'register' } catch { return 'register' }
  })
  const [form, setForm] = useState({ full_name: '', phone: '', shop_name: '', description: '', address: '', locality: 'Barmer' })
  const [saved, setSaved] = useState<{ application_code: string; claim_secret: string } | null>(() => {
    try { return JSON.parse(localStorage.getItem(SELLER_CLAIM_KEY) || 'null') } catch { return null }
  })
  const [applicationStatus, setApplicationStatus] = useState<any>(null)
  const [sellerPassword, setSellerPassword] = useState('')
  const [loginId, setLoginId] = useState('')
  const [loginPassword, setLoginPassword] = useState('')
  const [recoverCode, setRecoverCode] = useState('')
  const [recoverSecret, setRecoverSecret] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')

  const checkStatus = async (claim = saved) => {
    if (!claim) return
    const { data, error } = await supabase.functions.invoke('request-seller-onboarding', {
      body: { action: 'status', ...claim },
    })
    if (error) throw error
    setApplicationStatus(data)
    if (data.setup_completed) setMode('login')
  }

  useEffect(() => {
    if (!saved) return
    let active = true
    const refreshStatus = () => checkStatus(saved).catch((e: any) => {
      if (active) setNotice(errText(e))
    })
    refreshStatus()
    const timer = window.setInterval(refreshStatus, 15000)
    return () => { active = false; window.clearInterval(timer) }
  }, [saved?.application_code, saved?.claim_secret])

  const submitApplication = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true); setNotice('')
    try {
      const { data, error } = await supabase.functions.invoke('request-seller-onboarding', {
        body: { action: 'submit', ...form },
      })
      if (error) throw error
      const claim = { application_code: data.application_code, claim_secret: data.claim_secret }
      localStorage.setItem(SELLER_CLAIM_KEY, JSON.stringify(claim))
      setSaved(claim)
      setApplicationStatus({ status: 'pending', setup_completed: false })
      setMode('status')
      setNotice('आपका आवेदन सफलतापूर्वक जमा हो गया है।')
    } catch (e: any) { setNotice(errText(e)) }
    finally { setBusy(false) }
  }

  const finishAccount = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!saved || !applicationStatus?.seller_code) return
    setBusy(true); setNotice('')
    try {
      const sellerCode = applicationStatus.seller_code
      const { error } = await supabase.functions.invoke('complete-seller-account', {
        body: {
          application_code: saved.application_code,
          claim_secret: saved.claim_secret,
          seller_code: sellerCode,
          password: sellerPassword,
        },
      })
      if (error) throw error
      const { error: loginError } = await supabase.auth.signInWithPassword({
        email: 'seller-' + sellerCode.toLowerCase() + '@malro.app',
        password: sellerPassword,
      })
      if (loginError) throw loginError
      localStorage.removeItem(SELLER_CLAIM_KEY)
      window.location.reload()
    } catch (e: any) { setNotice(errText(e)) }
    finally { setBusy(false) }
  }

  const recoverApplication = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true); setNotice('')
    try {
      const claim = {
        application_code: recoverCode.trim().toUpperCase(),
        claim_secret: recoverSecret.trim().toUpperCase(),
      }
      const { data, error } = await supabase.functions.invoke('request-seller-onboarding', {
        body: { action: 'status', ...claim },
      })
      if (error) throw error
      localStorage.setItem(SELLER_CLAIM_KEY, JSON.stringify(claim))
      setSaved(claim); setApplicationStatus(data); setMode('status')
    } catch (e: any) { setNotice(errText(e)) }
    finally { setBusy(false) }
  }

  const login = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true); setNotice('')
    try {
      const id = loginId.trim().toLowerCase()
      if (!id) throw new Error('Seller ID दर्ज करें।')
      const { error } = await supabase.auth.signInWithPassword({
        email: 'seller-' + id + '@malro.app',
        password: loginPassword,
      })
      if (error) throw error
      window.location.reload()
    } catch (e: any) { setNotice(errText(e)) }
    finally { setBusy(false) }
  }

  const clearApplication = () => {
    localStorage.removeItem(SELLER_CLAIM_KEY)
    setSaved(null); setApplicationStatus(null); setMode('register'); setNotice('')
  }

  return <main className="auth-wrap">
    <section className="auth-card" style={{ width: 'min(100%, 560px)' }}>
      <div className="brand-mark">M</div>
      <p className="eyebrow">MALRO • SELLER PARTNER</p>
      <h1>विक्रेता पोर्टल</h1>
      <p className="muted">बिना OTP पंजीकरण • Admin की स्वीकृति के बाद अपना पासवर्ड बनाएँ</p>

      <div style={{ display: 'flex', gap: 8, margin: '18px 0' }}>
        <button type="button" className={mode === 'register' || mode === 'status' ? '' : 'secondary'} onClick={() => setMode(saved ? 'status' : 'register')}>नया आवेदन / स्थिति</button>
        <button type="button" className={mode === 'login' ? '' : 'secondary'} onClick={() => setMode('login')}>Seller Login</button>
      </div>

      {mode === 'register' && <form onSubmit={submitApplication}>
        <div className="form-grid">
          <label>विक्रेता का पूरा नाम<input required minLength={2} maxLength={100} value={form.full_name} onChange={e => setForm(f => ({ ...f, full_name: e.target.value }))} /></label>
          <label>मोबाइल नंबर (वैकल्पिक)<input inputMode="tel" maxLength={20} placeholder="OTP नहीं आएगा" value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))} /></label>
          <label>दुकान का नाम<input required minLength={2} maxLength={120} value={form.shop_name} onChange={e => setForm(f => ({ ...f, shop_name: e.target.value }))} /></label>
          <label>गाँव / शहर<input required maxLength={100} value={form.locality} onChange={e => setForm(f => ({ ...f, locality: e.target.value }))} /></label>
          <label style={{ gridColumn: '1 / -1' }}>दुकान का विवरण<textarea maxLength={1000} rows={3} value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} /></label>
          <label style={{ gridColumn: '1 / -1' }}>दुकान का पूरा पता<textarea required minLength={5} maxLength={500} rows={3} value={form.address} onChange={e => setForm(f => ({ ...f, address: e.target.value }))} /></label>
        </div>
        <button type="submit" disabled={busy}>{busy ? 'आवेदन भेज रहे हैं…' : 'आवेदन Submit करें'}</button>
        <button type="button" className="secondary" onClick={() => setMode('recover')}>पहले आवेदन किया है? स्थिति देखें</button>
      </form>}

      {mode === 'status' && <section>
        {saved ? <>
          <div className="notice"><strong>आवेदन ID:</strong> {saved.application_code}<br/><strong>सुरक्षा कोड:</strong> {saved.claim_secret}<br/>इन दोनों कोड को सुरक्षित रखें। दूसरे डिवाइस पर स्थिति देखने या अकाउंट बनाने के लिए इनकी जरूरत होगी।</div>
          {!applicationStatus && <p className="muted">आवेदन की स्थिति लोड हो रही है।</p>}
          {applicationStatus?.status === 'pending' && <><h2>आवेदन लंबित है</h2><p className="notice">आपका आवेदन Admin Panel पर पहुँच गया है। स्वीकृति मिलते ही यहीं Seller ID और पासवर्ड बनाने का विकल्प दिखेगा।</p><button type="button" className="secondary" disabled={busy} onClick={() => { setBusy(true); checkStatus().then(() => setNotice('स्थिति अपडेट हो गई।')).catch((e: any) => setNotice(errText(e))).finally(() => setBusy(false)) }}>स्थिति जाँचें</button></>}
          {applicationStatus?.status === 'approved' && !applicationStatus.setup_completed && <><h2>आवेदन स्वीकृत ✓</h2><p className="notice">आपकी Seller ID जारी हो गई है। अब अपना नया पासवर्ड बनाएँ।</p><div className="credential" style={{ margin: '14px 0', padding: 12, background: '#f5f7fa', borderRadius: 12 }}><small>आपकी Seller ID</small><strong style={{ display: 'block', fontSize: 20 }}>{applicationStatus.seller_code}</strong></div><form onSubmit={finishAccount}><label>नया पासवर्ड<input type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={sellerPassword} onChange={e => setSellerPassword(e.target.value)} /></label><label>पासवर्ड दोबारा लिखें<input type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={(form as any).confirmPassword || ''} onChange={e => setForm(f => ({ ...f, confirmPassword: e.target.value } as any))} /></label><button type="submit" disabled={busy || sellerPassword.length < 8 || sellerPassword !== (form as any).confirmPassword}>{busy ? 'अकाउंट बना रहे हैं…' : 'Seller Account बनाएँ'}</button></form></>}
          {applicationStatus?.status === 'approved' && applicationStatus.setup_completed && <><h2>अकाउंट बन चुका है ✓</h2><p className="notice">अब अपनी Seller ID और बनाए गए पासवर्ड से Login करें।</p><button type="button" onClick={() => { setLoginId(applicationStatus.seller_code || ''); setMode('login') }}>Login करें</button></>}
          {applicationStatus?.status === 'rejected' && <><h2>आवेदन स्वीकृत नहीं हुआ</h2><p className="notice">{applicationStatus.admin_note || 'कृपया Admin से संपर्क करें।'}</p><button type="button" onClick={clearApplication}>नया आवेदन भरें</button></>}
        </> : <p className="muted">इस डिवाइस पर आवेदन की जानकारी नहीं मिली।</p>}
      </section>}

      {mode === 'recover' && <form onSubmit={recoverApplication}>
        <h2>पुराना आवेदन खोजें</h2>
        <label>आवेदन ID<input required value={recoverCode} onChange={e => setRecoverCode(e.target.value.toUpperCase())} placeholder="MLR-XXXXXXXXXX" /></label>
        <label>सुरक्षा कोड<input required value={recoverSecret} onChange={e => setRecoverSecret(e.target.value.toUpperCase())} placeholder="48 अक्षरों का सुरक्षा कोड" /></label>
        <button type="submit" disabled={busy}>{busy ? 'आवेदन खोज रहे हैं…' : 'स्थिति देखें'}</button>
      </form>}

      {mode === 'login' && <form onSubmit={login}>
        <label>Seller ID<input required autoCapitalize="characters" value={loginId} onChange={e => setLoginId(e.target.value)} placeholder="MALRO-S-XXXXXXXXXX" /></label>
        <label>पासवर्ड<input required type="password" autoComplete="current-password" value={loginPassword} onChange={e => setLoginPassword(e.target.value)} /></label>
        <button type="submit" disabled={busy}>{busy ? 'Login हो रहा है…' : 'Login करें'}</button>
      </form>}

      {notice && <p className="notice" role="status">{notice}</p>}
      <p className="tiny">MALRO Seller • मोबाइल OTP या मोबाइल Login आवश्यक नहीं है</p>
    </section>
  </main>
}

function Metric({label,value}:{label:string;value:number}) { return <div className="metric"><span>{label}</span><strong>{value}</strong></div> }
function Empty({text}:{text:string}) { return <div className="empty">{text}</div> }
