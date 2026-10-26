import { useCallback, useEffect, useState } from 'react'
import './App.css'

const PAGE_SIZE = 8
const EMPTY_CART = { items: [], total: 0 }
const EMPTY_PRODUCT_FORM = {
  id: '',
  name: '',
  price: '',
  categoryId: '',
  stockQuantity: '0',
  imageUrl: '',
}
const EMPTY_CATEGORY_FORM = { id: '', name: '' }
const ORDER_STATUSES = ['PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED']

function getProductImage(product) {
  return product?.imageUrl || 'https://placehold.co/600x450?text=Product'
}

function formatPrice(price) {
  return `₹${Number(price ?? 0).toLocaleString('en-IN', {
    maximumFractionDigits: 2,
  })}`
}

function formatDate(dateValue) {
  if (!dateValue) return '—'
  const date = new Date(dateValue)
  return Number.isNaN(date.getTime()) ? String(dateValue) : date.toLocaleString('en-IN')
}

function normaliseRole(role) {
  return String(role || '').replace(/^ROLE_/i, '').toUpperCase()
}

function App() {
  const [products, setProducts] = useState([])
  const [categories, setCategories] = useState([])
  const [selectedProduct, setSelectedProduct] = useState(null)
  const [cart, setCart] = useState(EMPTY_CART)
  const [token, setToken] = useState(() => localStorage.getItem('nook_token') || '')
  const [role, setRole] = useState(() => localStorage.getItem('nook_role') || '')
  const [accountName, setAccountName] = useState(() => localStorage.getItem('nook_name') || '')

  const [loading, setLoading] = useState(true)
  const [categoriesLoading, setCategoriesLoading] = useState(true)
  const [cartLoading, setCartLoading] = useState(false)
  const [checkingOut, setCheckingOut] = useState(false)
  const [payingOrderId, setPayingOrderId] = useState(null)
  const [error, setError] = useState('')
  const [categoriesError, setCategoriesError] = useState('')
  const [message, setMessage] = useState('')

  const [showCart, setShowCart] = useState(false)
  const [showAuth, setShowAuth] = useState(false)
  const [authMode, setAuthMode] = useState('login')
  const [loginEmail, setLoginEmail] = useState('')
  const [loginPassword, setLoginPassword] = useState('')
  const [registerName, setRegisterName] = useState('')
  const [registerEmail, setRegisterEmail] = useState('')
  const [registerPassword, setRegisterPassword] = useState('')
  const [authSubmitting, setAuthSubmitting] = useState(false)

  const [searchInput, setSearchInput] = useState('')
  const [categoryInput, setCategoryInput] = useState('')
  const [minPriceInput, setMinPriceInput] = useState('')
  const [maxPriceInput, setMaxPriceInput] = useState('')
  const [filters, setFilters] = useState({
    name: '',
    categoryId: '',
    minPrice: '',
    maxPrice: '',
    sort: 'name,asc',
  })
  const [page, setPage] = useState(0)
  const [pageInfo, setPageInfo] = useState({ totalPages: 1, totalElements: 0 })

  const [checkoutOrder, setCheckoutOrder] = useState(null)
  const [paymentResult, setPaymentResult] = useState(null)
  const [showOrders, setShowOrders] = useState(false)
  const [orders, setOrders] = useState([])
  const [ordersLoading, setOrdersLoading] = useState(false)
  const [selectedOrder, setSelectedOrder] = useState(null)
  const [orderDetailLoading, setOrderDetailLoading] = useState(false)
  const [paymentHistoryOrderId, setPaymentHistoryOrderId] = useState(null)
  const [paymentHistory, setPaymentHistory] = useState([])
  const [paymentHistoryLoading, setPaymentHistoryLoading] = useState(false)

  const [showAdmin, setShowAdmin] = useState(false)
  const [adminTab, setAdminTab] = useState('products')
  const [adminProducts, setAdminProducts] = useState([])
  const [adminOrders, setAdminOrders] = useState([])
  const [adminOrdersLoading, setAdminOrdersLoading] = useState(false)
  const [adminOrderPage, setAdminOrderPage] = useState(0)
  const [adminOrderPageInfo, setAdminOrderPageInfo] = useState({ totalPages: 1, totalElements: 0 })
  const [adminLoading, setAdminLoading] = useState(false)
  const [productForm, setProductForm] = useState(EMPTY_PRODUCT_FORM)
  const [categoryForm, setCategoryForm] = useState(EMPTY_CATEGORY_FORM)
  const [adminOrderId, setAdminOrderId] = useState('')
  const [adminOrderStatus, setAdminOrderStatus] = useState('SHIPPED')
  const [adminSubmitting, setAdminSubmitting] = useState(false)

  const isAdmin = normaliseRole(role) === 'ADMIN'
  const cartItems = Array.isArray(cart?.items) ? cart.items : []
  const cartCount = cartItems.reduce((total, item) => total + Number(item.quantity || 0), 0)

  const apiRequest = useCallback(async (path, options = {}, authToken = token) => {
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    }
    if (authToken) headers.Authorization = `Bearer ${authToken}`

    let response
    try {
      response = await fetch(`/api${path}`, { ...options, headers })
    } catch (networkError) {
      throw new Error(
        'Cannot reach the backend. Check that Spring Boot is running.',
        { cause: networkError }
      )
    }

    if (!response.ok) {
      const rawBody = await response.text().catch(() => '')
      let body

      try {
        body = rawBody ? JSON.parse(rawBody) : null
      } catch {
        // The response is not JSON; rawBody remains available as a fallback.
      }
      const requestError = new Error(
        body?.message || body?.detail || body?.error || rawBody || `Request failed (${response.status})`
      )
      requestError.status = response.status

      if (response.status === 401 && authToken) {
        localStorage.removeItem('nook_token')
        localStorage.removeItem('nook_role')
        localStorage.removeItem('nook_name')
        setToken('')
        setRole('')
        setAccountName('')
        setCart(EMPTY_CART)
        requestError.authExpired = true
        requestError.message = 'Your session has expired. Please log in again.'
        setMessage(requestError.message)
      }
      throw requestError
    }

    if (response.status === 204) return null
    const responseText = await response.text()
    return responseText ? JSON.parse(responseText) : null
  }, [token])

  const fetchCategories = useCallback(async () => {
    try {
      const data = await apiRequest('/categories', {}, '')
      setCategories(Array.isArray(data) ? data : [])
      setCategoriesError('')
    } catch (err) {
      setCategoriesError(err.message || 'Could not load categories.')
    } finally {
      setCategoriesLoading(false)
    }
  }, [apiRequest])



  const fetchOrders = useCallback(async () => {
    if (!token) {
      setShowAuth(true)
      setAuthMode('login')
      setMessage('Please log in to view your orders.')
      return
    }
    setOrdersLoading(true)
    try {
      const data = await apiRequest('/orders')
      setOrders(Array.isArray(data) ? data : [])
      setSelectedOrder(null)
      setPaymentHistoryOrderId(null)
      setPaymentHistory([])
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not load orders: ${err.message}`)
    } finally {
      setOrdersLoading(false)
    }
  }, [apiRequest, token])

  const loadAdminOrders = useCallback(async (requestedPage = adminOrderPage) => {
    if (!isAdmin) return
    setAdminOrdersLoading(true)
    try {
      const data = await apiRequest(`/admin/orders?page=${requestedPage}&size=10&sort=createdAt,desc`, {}, token)
      setAdminOrders(Array.isArray(data?.content) ? data.content : [])
      setAdminOrderPage(data?.number ?? requestedPage)
      setAdminOrderPageInfo({
        totalPages: Math.max(1, data?.totalPages ?? 1),
        totalElements: data?.totalElements ?? 0,
      })
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not load admin orders: ${err.message}`)
    } finally {
      setAdminOrdersLoading(false)
    }
  }, [apiRequest, adminOrderPage, isAdmin, token])

  const loadAdminData = useCallback(async () => {
    if (!isAdmin) return
    setAdminLoading(true)
    try {
      const [productData, categoryData] = await Promise.all([
        apiRequest('/products?page=0&size=100&sort=id,desc', {}, ''),
        apiRequest('/categories', {}, ''),
      ])
      setAdminProducts(Array.isArray(productData) ? productData : (productData?.content ?? []))
      setCategories(Array.isArray(categoryData) ? categoryData : [])
      await loadAdminOrders(0)
    } catch (err) {
      setMessage(`Could not load admin data: ${err.message}`)
    } finally {
      setAdminLoading(false)
    }
  }, [apiRequest, isAdmin, loadAdminOrders])

  useEffect(() => {
    let active = true

    async function loadCategories() {
      try {
        const data = await apiRequest('/categories', {}, '')

        if (!active) return

        setCategories(Array.isArray(data) ? data : [])
        setCategoriesError('')
      } catch (err) {
        if (!active) return

        setCategoriesError(err.message || 'Could not load categories.')
      } finally {
        if (active) setCategoriesLoading(false)
      }
    }

    loadCategories()

    return () => {
      active = false
    }
  }, [apiRequest])

  useEffect(() => {
    let active = true
    async function loadProducts() {
      setLoading(true)
      setError('')
      const params = new URLSearchParams()
      params.set('page', String(page))
      params.set('size', String(PAGE_SIZE))
      if (filters.name) params.set('name', filters.name)
      if (filters.categoryId) params.set('categoryId', filters.categoryId)
      if (filters.minPrice !== '') params.set('minPrice', filters.minPrice)
      if (filters.maxPrice !== '') params.set('maxPrice', filters.maxPrice)
      if (filters.sort) params.set('sort', filters.sort)

      try {
        const data = await apiRequest(`/products?${params.toString()}`, {}, '')
        if (!active) return
        const content = Array.isArray(data) ? data : (data?.content ?? [])
        setProducts(content)
        setPageInfo({
          totalPages: Array.isArray(data) ? 1 : Math.max(1, data?.totalPages ?? 1),
          totalElements: Array.isArray(data) ? content.length : (data?.totalElements ?? content.length),
        })
      } catch (err) {
        if (!active) return
        setProducts([])
        setPageInfo({ totalPages: 1, totalElements: 0 })
        setError(err.message || 'Could not load products.')
      } finally {
        if (active) setLoading(false)
      }
    }
    loadProducts()
    return () => { active = false }
  }, [apiRequest, filters, page])
  useEffect(() => {
    let active = true

    async function loadCart() {
      if (!token) return

      try {
        const data = await apiRequest('/cart', {}, token)

        if (active) {
          setCart(data || EMPTY_CART)
        }
      } catch (err) {
        if (active && !err.authExpired) {
          setMessage(`Could not load your cart: ${err.message}`)
        }
      } finally {
        if (active) setCartLoading(false)
      }
    }

    loadCart()

    return () => {
      active = false
    }
  }, [token, apiRequest])

  function applyFilters(event) {
    event.preventDefault()
    if (minPriceInput !== '' && Number(minPriceInput) < 0) {
      setMessage('Minimum price cannot be negative.')
      return
    }
    if (maxPriceInput !== '' && Number(maxPriceInput) < 0) {
      setMessage('Maximum price cannot be negative.')
      return
    }
    if (minPriceInput !== '' && maxPriceInput !== '' && Number(minPriceInput) > Number(maxPriceInput)) {
      setMessage('Minimum price cannot exceed maximum price.')
      return
    }
    setMessage('')
    setSelectedProduct(null)
    setPage(0)
    setFilters((current) => ({
      ...current,
      name: searchInput.trim(),
      categoryId: categoryInput,
      minPrice: minPriceInput,
      maxPrice: maxPriceInput,
    }))
  }

  function clearFilters() {
    setSearchInput('')
    setCategoryInput('')
    setMinPriceInput('')
    setMaxPriceInput('')
    setPage(0)
    setSelectedProduct(null)
    setFilters({ name: '', categoryId: '', minPrice: '', maxPrice: '', sort: 'name,asc' })
    setMessage('')
  }

  function logout() {
    localStorage.removeItem('nook_token')
    localStorage.removeItem('nook_role')
    localStorage.removeItem('nook_name')
    setToken('')
    setRole('')
    setAccountName('')
    setCart(EMPTY_CART)
    setShowCart(false)
    setShowAuth(false)
    setShowOrders(false)
    setShowAdmin(false)
    setCheckoutOrder(null)
    setPaymentResult(null)
    setOrders([])
    setSelectedOrder(null)
    setMessage('You have logged out.')
  }

  async function handleAuthSubmit(event) {
    event.preventDefault()
    setMessage('')
    setAuthSubmitting(true)
    try {
      if (authMode === 'register') {
        const data = await apiRequest('/auth/register', {
          method: 'POST',
          body: JSON.stringify({
            name: registerName.trim(),
            email: registerEmail.trim(),
            password: registerPassword,
          }),
        }, '')
        setLoginEmail(data?.email || registerEmail.trim())
        setLoginPassword('')
        setRegisterPassword('')
        setAuthMode('login')
        setMessage('Account created successfully. Please log in to continue.')
      } else {
        const data = await apiRequest('/auth/login', {
          method: 'POST',
          body: JSON.stringify({ email: loginEmail.trim(), password: loginPassword }),
        }, '')
        if (!data?.token) throw new Error('The login response did not contain a token.')
        const savedRole = normaliseRole(data.role)
        localStorage.setItem('nook_token', data.token)
        localStorage.setItem('nook_role', savedRole)
        localStorage.setItem('nook_name', data.name || data.email || '')
        setToken(data.token)
        setRole(savedRole)
        setAccountName(data.name || data.email || '')
        setLoginPassword('')
        setShowAuth(false)
        setMessage(`Welcome${data.name ? `, ${data.name}` : ''}! You are logged in.`)
      }
    } catch (err) {
      setMessage(`${authMode === 'register' ? 'Registration' : 'Login'} failed: ${err.message}`)
    } finally {
      setAuthSubmitting(false)
    }
  }

  async function addToCart(product) {
    setMessage('')
    if (!token) {
      setShowAuth(true)
      setAuthMode('login')
      setMessage('Please log in before adding items to your cart.')
      return
    }
    if (Number(product.stockQuantity ?? 0) <= 0) {
      setMessage(`${product.name} is currently out of stock.`)
      return
    }
    try {
      const data = await apiRequest('/cart/items', {
        method: 'POST',
        body: JSON.stringify({ productId: product.id, quantity: 1 }),
      })
      setCart(data || EMPTY_CART)
      setMessage(`${product.name} added to your cart.`)
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not add ${product.name}: ${err.message}`)
    }
  }

  async function updateQuantity(productId, quantity) {
    if (quantity < 1) return removeFromCart(productId)
    setMessage('')
    try {
      const data = await apiRequest(`/cart/items/${productId}`, {
        method: 'PUT',
        body: JSON.stringify({ quantity }),
      })
      setCart(data || EMPTY_CART)
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not update quantity: ${err.message}`)
    }
  }

  async function removeFromCart(productId) {
    setMessage('')
    try {
      const data = await apiRequest(`/cart/items/${productId}`, { method: 'DELETE' })
      setCart(data || EMPTY_CART)
      setMessage('Item removed from your cart.')
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not remove item: ${err.message}`)
    }
  }

  async function handleCheckout() {
    if (!token) {
      setShowAuth(true)
      setAuthMode('login')
      setMessage('Please log in before checkout.')
      return
    }
    if (!cartItems.length) {
      setMessage('Your cart is empty.')
      return
    }


    setCheckingOut(true)
    setMessage('')
    try {
      const order = await apiRequest('/orders/checkout', { method: 'POST' })
      setCheckoutOrder(order)
      setPaymentResult(null)
      setPaymentHistoryOrderId(null)
      setPaymentHistory([])
      setCart(EMPTY_CART)
      // Refresh product stock after a successful checkout.
      try {
        const params = new URLSearchParams()
        params.set('page', String(page))
        params.set('size', String(PAGE_SIZE))

        if (filters.name) params.set('name', filters.name)
        if (filters.categoryId) params.set('categoryId', filters.categoryId)
        if (filters.minPrice !== '') params.set('minPrice', filters.minPrice)
        if (filters.maxPrice !== '') params.set('maxPrice', filters.maxPrice)
        if (filters.sort) params.set('sort', filters.sort)

        const catalogue = await apiRequest(
          `/products?${params.toString()}`,
          {},
          ''
        )

        const content = Array.isArray(catalogue)
          ? catalogue
          : (catalogue?.content ?? [])

        setProducts(content)
        setSelectedProduct((current) => {
          if (!current) return null

          const updatedProduct = content.find(
            (product) => product.id === current.id
          )

          return updatedProduct ?? current
        })

        setPageInfo({
          totalPages: Array.isArray(catalogue)
            ? 1
            : Math.max(1, catalogue?.totalPages ?? 1),
          totalElements: Array.isArray(catalogue)
            ? content.length
            : (catalogue?.totalElements ?? content.length),
        })
      } catch {
        // The order was placed successfully, even if refreshing the catalogue fails.
      }
      setShowCart(false)
      setShowOrders(false)
      setMessage(`Order #${order.orderId} has been placed. Complete payment to confirm it.`)
    } catch (err) {
      if (!err.authExpired) setMessage(`Checkout failed: ${err.message}`)
    } finally {
      setCheckingOut(false)
    }
  }

  async function handlePayForOrder(order) {
    if (!token) {
      setShowAuth(true)
      setAuthMode('login')
      setMessage('Please log in again to pay for your order.')
      return
    }
    setPayingOrderId(order.orderId)
    setMessage('')
    try {
      // A new key is intentional for each retry after a failed attempt.
      const randomPart = window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
      const result = await apiRequest(`/payments/orders/${order.orderId}`, {
        method: 'POST',
        headers: { 'Idempotency-Key': `nook-${order.orderId}-${randomPart}` },
      })
      setPaymentResult(result)
      if (String(result?.status).toUpperCase() === 'SUCCESS') {
        setCheckoutOrder((current) => current?.orderId === order.orderId
          ? { ...current, status: 'CONFIRMED' }
          : current)
        setMessage(`Payment successful for order #${order.orderId}.`)
        if (showOrders) fetchOrders()
      } else {
        setMessage(`Payment ${String(result?.status || 'failed').toLowerCase()} for order #${order.orderId}. You can retry payment.`)
      }
    } catch (err) {
      if (!err.authExpired) setMessage(`Payment request failed: ${err.message}`)
    } finally {
      setPayingOrderId(null)
    }
  }

  async function cancelOrder(order) {
    if (!token) {
      setShowAuth(true)
      setAuthMode('login')
      setMessage('Please log in again to cancel an order.')
      return
    }
    if (!['PLACED', 'CONFIRMED'].includes(String(order.status).toUpperCase())) {
      setMessage('Only placed or confirmed orders can be cancelled.')
      return
    }
    if (!window.confirm(`Cancel order #${order.orderId}? A successful payment will receive a simulated refund.`)) return
    setMessage('')
    try {
      const result = await apiRequest(`/orders/${order.orderId}/cancel`, { method: 'POST' })
      const updatedOrder = result?.order
      if (result?.payment) setPaymentResult(result.payment)
      if (updatedOrder) {
        setCheckoutOrder((current) => current?.orderId === updatedOrder.orderId ? updatedOrder : current)
        setOrders((current) => current.map((item) => item.orderId === updatedOrder.orderId ? updatedOrder : item))
        if (selectedOrder?.orderId === updatedOrder.orderId) setSelectedOrder(updatedOrder)
      }
      if (result?.cancelled) {
        setMessage(result.message || `Order #${order.orderId} cancelled.`)
      } else {
        setMessage(result?.message || 'Cancellation did not complete. Please check payment history and retry if appropriate.')
      }
      if (showOrders) await fetchOrders()
      // Refresh stock values on the visible catalogue after a cancellation restores inventory.
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) })
      if (filters.name) params.set('name', filters.name)
      if (filters.categoryId) params.set('categoryId', filters.categoryId)
      if (filters.minPrice !== '') params.set('minPrice', filters.minPrice)
      if (filters.maxPrice !== '') params.set('maxPrice', filters.maxPrice)
      if (filters.sort) params.set('sort', filters.sort)
      const catalogue = await apiRequest(`/products?${params.toString()}`, {}, '')
      const content = Array.isArray(catalogue) ? catalogue : (catalogue?.content ?? [])
      setProducts(content)

      setSelectedProduct((current) => {
        if (!current) return null

        const updatedProduct = content.find(
          (product) => product.id === current.id
        )

        return updatedProduct ?? current
      })

      setPageInfo({
        totalPages: Array.isArray(catalogue) ? 1 : Math.max(1, catalogue?.totalPages ?? 1),
        totalElements: Array.isArray(catalogue) ? content.length : (catalogue?.totalElements ?? content.length),
      })
      if (paymentHistoryOrderId === order.orderId) {
        setPaymentHistoryOrderId(order.orderId)
        const refreshedPayments = await apiRequest(`/payments/orders/${order.orderId}`)
        setPaymentHistory(Array.isArray(refreshedPayments) ? refreshedPayments : [])
      }
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not cancel order: ${err.message}`)
    }
  }

  async function updateAdminOrderStatusFor(orderId, newStatus) {
    if (!isAdmin) return
    setAdminSubmitting(true)
    try {
      const updated = await apiRequest(`/orders/${orderId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: newStatus }),
      })
      setAdminOrders((current) => current.map((item) => item.orderId === updated.orderId ? { ...item, ...updated } : item))
      setMessage(`Order #${updated.orderId} status updated to ${updated.status}.`)
      await loadAdminOrders(adminOrderPage)
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not update order status: ${err.message}`)
    } finally {
      setAdminSubmitting(false)
    }
  }

  async function viewOrderDetails(orderId) {
    setOrderDetailLoading(true)
    setPaymentHistoryOrderId(null)
    setPaymentHistory([])
    try {
      const data = await apiRequest(`/orders/${orderId}`)
      setSelectedOrder(data)
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not load order details: ${err.message}`)
    } finally {
      setOrderDetailLoading(false)
    }
  }

  async function togglePaymentHistory(orderId) {
    if (paymentHistoryOrderId === orderId) {
      setPaymentHistoryOrderId(null)
      setPaymentHistory([])
      return
    }
    setPaymentHistoryOrderId(orderId)
    setPaymentHistoryLoading(true)
    setPaymentHistory([])
    try {
      const data = await apiRequest(`/payments/orders/${orderId}`)
      setPaymentHistory(Array.isArray(data) ? data : [])
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not load payment history: ${err.message}`)
    } finally {
      setPaymentHistoryLoading(false)
    }
  }

  async function openOrders() {
    setShowCart(false)
    setShowAdmin(false)
    setShowAuth(false)
    if (!token) {
      setShowAuth(true)
      setAuthMode('login')
      setMessage('Please log in to view your orders.')
      return
    }
    setShowOrders((current) => !current)
    await fetchOrders()
  }

  async function openAdmin() {
    if (!isAdmin) {
      setMessage('Admin access is required for this section.')
      return
    }
    setShowCart(false)
    setShowOrders(false)
    setShowAuth(false)
    setShowAdmin((current) => !current)
    await loadAdminData()
  }

  function resetProductForm() {
    setProductForm({ ...EMPTY_PRODUCT_FORM })
  }

  function selectProductToEdit(productId) {
    if (!productId) {
      resetProductForm()
      return
    }
    const product = adminProducts.find((item) => String(item.id) === String(productId))
    if (!product) return
    setProductForm({
      id: String(product.id),
      name: product.name || '',
      price: String(product.price ?? ''),
      categoryId: String(product.category?.id ?? ''),
      stockQuantity: String(product.stockQuantity ?? 0),
      imageUrl: product.imageUrl || '',
    })
  }

  async function refreshAdminProducts() {
    const data = await apiRequest('/products?page=0&size=100&sort=id,desc', {}, '')
    setAdminProducts(Array.isArray(data) ? data : (data?.content ?? []))
    // Keep the catalogue refreshed after admin changes.
    const params = new URLSearchParams()
    params.set('page', String(page))
    params.set('size', String(PAGE_SIZE))
    if (filters.name) params.set('name', filters.name)
    if (filters.categoryId) params.set('categoryId', filters.categoryId)
    if (filters.minPrice !== '') params.set('minPrice', filters.minPrice)
    if (filters.maxPrice !== '') params.set('maxPrice', filters.maxPrice)
    if (filters.sort) params.set('sort', filters.sort)
    const catalogue = await apiRequest(`/products?${params.toString()}`, {}, '')
    const content = Array.isArray(catalogue) ? catalogue : (catalogue?.content ?? [])
    setProducts(content)
    setPageInfo({
      totalPages: Array.isArray(catalogue) ? 1 : Math.max(1, catalogue?.totalPages ?? 1),
      totalElements: Array.isArray(catalogue) ? content.length : (catalogue?.totalElements ?? content.length),
    })
  }

  async function submitProductForm(event) {
    event.preventDefault()
    if (!isAdmin) return
    if (!productForm.categoryId) {
      setMessage('Please choose a category for the product.')
      return
    }
    if (!productForm.name.trim()) {
      setMessage('Product name is required.')
      return
    }
    if (!Number.isFinite(Number(productForm.price)) || Number(productForm.price) <= 0) {
      setMessage('Product price must be greater than zero.')
      return
    }
    if (!Number.isInteger(Number(productForm.stockQuantity)) || Number(productForm.stockQuantity) < 0) {
      setMessage('Stock quantity must be a non-negative whole number.')
      return
    }
    setAdminSubmitting(true)
    try {
      if (productForm.id) {
        // PATCH supports partial updates while preserving omitted fields.
        await apiRequest(`/products/${productForm.id}`, {
          method: 'PATCH',
          body: JSON.stringify({
            name: productForm.name.trim(),
            price: Number(productForm.price),
            categoryId: Number(productForm.categoryId),
            stockQuantity: Number(productForm.stockQuantity),
            imageUrl: productForm.imageUrl.trim(),
          }),
        })
        setMessage(`Product #${productForm.id} updated.`)
      } else {
        const created = await apiRequest('/products', {
          method: 'POST',
          body: JSON.stringify({
            name: productForm.name.trim(),
            price: Number(productForm.price),
            categoryId: Number(productForm.categoryId),
            stockQuantity: Number(productForm.stockQuantity),
            imageUrl: productForm.imageUrl.trim(),
          }),
        })
        setMessage(`Product ${created?.name || ''} created successfully.`)
      }
      await refreshAdminProducts()
      resetProductForm()
      setMessage('Product changes saved successfully.')
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not save product: ${err.message}`)
    } finally {
      setAdminSubmitting(false)
    }
  }

  async function deleteProduct(productId) {
    if (!isAdmin || !productId) return
    const product = adminProducts.find((item) => String(item.id) === String(productId))
    if (!window.confirm(`Delete ${product?.name || `product #${productId}`}? This cannot be undone.`)) return
    try {
      await apiRequest(`/products/${productId}`, { method: 'DELETE' })
      resetProductForm()
      await refreshAdminProducts()
      setMessage('Product deleted successfully.')
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not delete product: ${err.message}`)
    }
  }

  async function submitCategoryForm(event) {
    event.preventDefault()
    if (!isAdmin) return
    if (!categoryForm.name.trim()) {
      setMessage('Category name is required.')
      return
    }
    setAdminSubmitting(true)
    try {
      if (categoryForm.id) {
        await apiRequest(`/categories/${categoryForm.id}`, {
          method: 'PUT',
          body: JSON.stringify({ name: categoryForm.name.trim() }),
        })
        setMessage('Category updated successfully.')
      } else {
        await apiRequest('/categories', {
          method: 'POST',
          body: JSON.stringify({ name: categoryForm.name.trim() }),
        })
        setMessage('Category created successfully.')
      }
      setCategoryForm({ ...EMPTY_CATEGORY_FORM })
      await fetchCategories()
      await loadAdminData()
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not save category: ${err.message}`)
    } finally {
      setAdminSubmitting(false)
    }
  }

  function selectCategoryToEdit(categoryId) {
    if (!categoryId) {
      setCategoryForm({ ...EMPTY_CATEGORY_FORM })
      return
    }
    const category = categories.find((item) => String(item.id) === String(categoryId))
    if (category) setCategoryForm({ id: String(category.id), name: category.name || '' })
  }

  async function deleteCategory(categoryId) {
    if (!isAdmin || !categoryId) return
    const category = categories.find((item) => String(item.id) === String(categoryId))
    if (!window.confirm(`Delete category ${category?.name || `#${categoryId}`}?`)) return
    try {
      await apiRequest(`/categories/${categoryId}`, { method: 'DELETE' })
      setCategoryForm({ ...EMPTY_CATEGORY_FORM })
      await fetchCategories()
      await loadAdminData()
      setMessage('Category deleted successfully.')
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not delete category: ${err.message}`)
    }
  }

  async function updateOrderStatus(event) {
    event.preventDefault()
    if (!isAdmin) return
    if (!adminOrderId.trim() || !/^\d+$/.test(adminOrderId.trim())) {
      setMessage('Enter a valid numeric order ID.')
      return
    }
    setAdminSubmitting(true)
    try {
      const updated = await apiRequest(`/orders/${adminOrderId.trim()}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: adminOrderStatus }),
      })
      setMessage(`Order #${updated.orderId} status updated to ${updated.status}.`)
      if (selectedOrder?.orderId === updated.orderId) setSelectedOrder(updated)
      await loadAdminOrders(adminOrderPage)
    } catch (err) {
      if (!err.authExpired) setMessage(`Could not update order status: ${err.message}`)
    } finally {
      setAdminSubmitting(false)
    }
  }

  return (
    <div className="store">
      <header className="navbar">
        <a className="brand" href="#top" onClick={() => {
          setShowAdmin(false); setShowOrders(false); setShowCart(false); setShowAuth(false)
        }}>nook<span>.</span></a>

        <nav className="nav-links">
          <a href="#products" onClick={() => { setShowAdmin(false); setShowOrders(false) }}>Shop</a>
          <a href="#about">About</a>
        </nav>

        <div className="utility-actions">
          {token ? (
            <>
              <span className="account-greeting">Hi, {accountName || 'there'}</span>
              <button className="cart-button" type="button" onClick={logout}>Log out</button>
              <button className="cart-button" type="button" onClick={openOrders}>Orders</button>
              {isAdmin && <button className="cart-button" type="button" onClick={openAdmin}>Admin</button>}
            </>
          ) : (
            <button className="cart-button" type="button" onClick={() => {
              setShowAuth((current) => !current); setShowCart(false); setShowOrders(false); setShowAdmin(false)
            }}>Log in / Register</button>
          )}
          <button className="cart-button" type="button" onClick={() => {
            setShowCart((current) => !current); setShowAuth(false); setShowOrders(false); setShowAdmin(false)
          }}>Cart ({cartCount})</button>
        </div>
      </header>

      {message && (
        <div className="status-message" role="status">
          <span>{message}</span>
          <button type="button" onClick={() => setMessage('')} aria-label="Dismiss message">×</button>
        </div>
      )}

      {showAuth && (
        <section className="panel auth-panel">
          <div className="panel-header">
            <div>
              <p className="eyebrow">YOUR NOOK ACCOUNT</p>
              <h2>{authMode === 'login' ? 'Welcome back.' : 'Create your account.'}</h2>
            </div>
            <button className="secondary-button" type="button" onClick={() => setShowAuth(false)}>Close</button>
          </div>
          <div className="tabs" role="tablist" aria-label="Authentication">
            <button type="button" className={authMode === 'login' ? 'active' : ''} onClick={() => setAuthMode('login')}>Log in</button>
            <button type="button" className={authMode === 'register' ? 'active' : ''} onClick={() => setAuthMode('register')}>Register</button>
          </div>
          <form className="form-grid auth-form" onSubmit={handleAuthSubmit}>
            {authMode === 'register' && (
              <div className="form-field">
                <label htmlFor="register-name">Full name</label>
                <input id="register-name" value={registerName} onChange={(event) => setRegisterName(event.target.value)} autoComplete="name" required minLength={1} />
              </div>
            )}
            <div className="form-field">
              <label htmlFor="auth-email">Email</label>
              <input id="auth-email" type="email" value={authMode === 'login' ? loginEmail : registerEmail} onChange={(event) => authMode === 'login' ? setLoginEmail(event.target.value) : setRegisterEmail(event.target.value)} autoComplete="email" required />
            </div>
            <div className="form-field">
              <label htmlFor="auth-password">Password {authMode === 'register' && <span>(at least 8 characters)</span>}</label>
              <input id="auth-password" type="password" value={authMode === 'login' ? loginPassword : registerPassword} onChange={(event) => authMode === 'login' ? setLoginPassword(event.target.value) : setRegisterPassword(event.target.value)} autoComplete={authMode === 'login' ? 'current-password' : 'new-password'} minLength={authMode === 'register' ? 8 : undefined} required />
            </div>
            <div className="form-actions">
              <button className="primary-button" type="submit" disabled={authSubmitting}>{authSubmitting ? 'Please wait…' : authMode === 'login' ? 'Log in' : 'Create account'}</button>
            </div>
          </form>
          {authMode === 'register' && <p className="muted-note">After registration, log in to receive your authentication token.</p>}
        </section>
      )}

      {showCart && (
        <section className="panel">
          <div className="panel-header">
            <div><p className="eyebrow">YOUR SELECTION</p><h2>Your cart</h2></div>
            <button className="secondary-button" type="button" onClick={() => setShowCart(false)}>Continue shopping</button>
          </div>
          {!token && <div className="empty-state"><p>Please log in to view and save your cart.</p><button className="primary-button" type="button" onClick={() => { setShowCart(false); setShowAuth(true); setAuthMode('login') }}>Log in</button></div>}
          {token && cartLoading && <p>Loading your cart…</p>}
          {token && !cartLoading && cartItems.length === 0 && <div className="empty-state"><p>Your cart is empty.</p><a className="primary-button" href="#products" onClick={() => setShowCart(false)}>Explore products</a></div>}
          {token && !cartLoading && cartItems.length > 0 && <>
            <div className="cart-lines">
              {cartItems.map((item) => (
                <div className="cart-line" key={item.productId}>
                  <div className="cart-line-info"><strong>{item.productName}</strong><span>{formatPrice(item.price)} each</span><span>Subtotal: {formatPrice(item.subtotal)}</span></div>
                  <div className="cart-line-actions">
                    <button type="button" className="quantity-button" aria-label={`Decrease ${item.productName}`} onClick={() => updateQuantity(item.productId, item.quantity - 1)}>−</button>
                    <span>{item.quantity}</span>
                    <button type="button" className="quantity-button" aria-label={`Increase ${item.productName}`} onClick={() => updateQuantity(item.productId, item.quantity + 1)}>+</button>
                    <button type="button" className="text-button danger-text" onClick={() => removeFromCart(item.productId)}>Remove</button>
                  </div>
                </div>
              ))}
            </div>
            <div className="cart-summary">
              <div><span>Subtotal</span><strong>{formatPrice(cart.total)}</strong></div>
              <p className="muted-note">Final stock availability is validated when you place the order.</p>
              <button className="primary-button" type="button" disabled={checkingOut} onClick={handleCheckout}>{checkingOut ? 'Placing order…' : 'Checkout'}</button>
            </div>
          </>}
        </section>
      )}

      {checkoutOrder && (
        <section className="panel checkout-panel">
          <div className="panel-header">
            <div><p className="eyebrow">CHECKOUT</p><h2>Order #{checkoutOrder.orderId}</h2></div>
            <button className="secondary-button" type="button" onClick={() => setCheckoutOrder(null)}>Close</button>
          </div>
          <div className="order-meta"><span>Status: <strong className="status-pill">{checkoutOrder.status}</strong></span><span>Placed: {formatDate(checkoutOrder.createdAt)}</span></div>
          <div className="order-items">
            {(checkoutOrder.items || []).map((item) => <div key={`${item.productId}-${item.productName}`} className="order-item-row"><span>{item.productName} × {item.quantity}</span><span>{formatPrice(item.subtotal)}</span></div>)}
          </div>
          <div className="total-row"><span>Order total</span><strong>{formatPrice(checkoutOrder.totalAmount)}</strong></div>
          {paymentResult && <div className={`payment-result ${String(paymentResult.status).toUpperCase() === 'SUCCESS' ? 'success' : 'failure'}`}>
            <h3>Payment {paymentResult.status}</h3>
            <p>Payment reference: {paymentResult.paymentReference || '—'}</p>
            <p>Provider reference: {paymentResult.providerReference || '—'}</p>
            <p>Amount: {formatPrice(paymentResult.amount)}</p>
            <p>Time: {formatDate(paymentResult.createdAt)}</p>
            {paymentResult.refundStatus && paymentResult.refundStatus !== 'NOT_REQUESTED' && <><p>Refund status: {paymentResult.refundStatus}</p><p>Refund reference: {paymentResult.refundReference || '—'}</p>{paymentResult.refundedAt && <p>Refunded at: {formatDate(paymentResult.refundedAt)}</p>}</>}
          </div>}
          {checkoutOrder.status === 'PLACED' && String(paymentResult?.status || '').toUpperCase() !== 'SUCCESS' && <>
            <p className="muted-note">Your backend currently processes payments through its configured payment gateway. A successful payment confirms the order; a failed attempt can be retried.</p>
            <button className="primary-button" type="button" disabled={payingOrderId === checkoutOrder.orderId} onClick={() => handlePayForOrder(checkoutOrder)}>{payingOrderId === checkoutOrder.orderId ? 'Processing payment…' : paymentResult ? 'Retry payment' : 'Pay now'}</button>
          </>}
          {String(paymentResult?.status || '').toUpperCase() === 'SUCCESS' && checkoutOrder.status === 'CONFIRMED' && <p className="success-note">Payment confirmed. Your order is now confirmed.</p>}
          {checkoutOrder.status === 'CANCELLED' && <p className="muted-note">This order is cancelled. If it was paid, its simulated refund status is shown above and in payment history.</p>}
          {['PLACED', 'CONFIRMED'].includes(String(checkoutOrder.status).toUpperCase()) && <button className="danger-button" type="button" onClick={() => cancelOrder(checkoutOrder)}>Cancel order{checkoutOrder.status === 'CONFIRMED' ? ' and refund' : ''}</button>}
        </section>
      )}

      {showOrders && (
        <section className="panel">
          <div className="panel-header">
            <div><p className="eyebrow">YOUR PURCHASES</p><h2>Order history</h2></div>
            <button className="secondary-button" type="button" onClick={() => setShowOrders(false)}>Close</button>
          </div>
          {ordersLoading && <p>Loading your orders…</p>}
          {!ordersLoading && orders.length === 0 && <div className="empty-state"><p>You have not placed any orders yet.</p></div>}
          {!ordersLoading && orders.length > 0 && <div className="order-list">
            {orders.map((order) => <article className="order-card" key={order.orderId}>
              <div className="order-card-top"><div><h3>Order #{order.orderId}</h3><p>{formatDate(order.createdAt)}</p></div><span className="status-pill">{order.status}</span></div>
              <div className="order-card-bottom"><strong>{formatPrice(order.totalAmount)}</strong><div className="action-row"><button className="secondary-button" type="button" onClick={() => viewOrderDetails(order.orderId)}>Details</button><button className="secondary-button" type="button" onClick={() => togglePaymentHistory(order.orderId)}>{paymentHistoryOrderId === order.orderId ? 'Hide payments' : 'Payment history'}</button>{['PLACED', 'CONFIRMED'].includes(String(order.status).toUpperCase()) && <button className="danger-button" type="button" onClick={() => cancelOrder(order)}>Cancel{order.status === 'CONFIRMED' ? ' & refund' : ''}</button>}</div></div>
              {paymentHistoryOrderId === order.orderId && <div className="payment-history">
                {paymentHistoryLoading ? <p>Loading payment history…</p> : paymentHistory.length === 0 ? <p>No payment attempts recorded for this order.</p> : paymentHistory.map((payment) => <div className="payment-history-row" key={payment.paymentId}><span>Payment #{payment.paymentId} · {payment.status}</span><span>{formatPrice(payment.amount)}</span><small>{payment.paymentReference || 'No reference'} · {formatDate(payment.createdAt)}{payment.refundStatus && payment.refundStatus !== 'NOT_REQUESTED' ? ` · Refund ${payment.refundStatus}${payment.refundReference ? ` (${payment.refundReference})` : ''}` : ''}</small></div>)}
              </div>}
            </article>)}
          </div>}
          {orderDetailLoading && <p>Loading order details…</p>}
          {selectedOrder && <div className="order-detail-box">
            <div className="panel-header"><h3>Details for order #{selectedOrder.orderId}</h3><button className="text-button" type="button" onClick={() => setSelectedOrder(null)}>Hide</button></div>
            <p>Status: <strong>{selectedOrder.status}</strong></p><p>Placed: {formatDate(selectedOrder.createdAt)}</p>
            {(selectedOrder.items || []).map((item) => <div className="order-item-row" key={`${item.productId}-${item.productName}`}><span>{item.productName} × {item.quantity} · {formatPrice(item.unitPrice)} each</span><strong>{formatPrice(item.subtotal)}</strong></div>)}
            <div className="total-row"><span>Total</span><strong>{formatPrice(selectedOrder.totalAmount)}</strong></div>
            {isAdmin && <p className="muted-note">Use the Admin panel to update an order status.</p>}
          </div>}
        </section>
      )}

      {showAdmin && isAdmin && (
        <section className="panel admin-panel">
          <div className="panel-header">
            <div><p className="eyebrow">STORE MANAGEMENT</p><h2>Admin dashboard</h2><p className="muted-note">Management actions are protected by your backend's ADMIN role.</p></div>
            <button className="secondary-button" type="button" onClick={() => setShowAdmin(false)}>Close</button>
          </div>
          <div className="tabs" role="tablist" aria-label="Admin sections">
            <button type="button" className={adminTab === 'products' ? 'active' : ''} onClick={() => setAdminTab('products')}>Products</button>
            <button type="button" className={adminTab === 'categories' ? 'active' : ''} onClick={() => setAdminTab('categories')}>Categories</button>
            <button type="button" className={adminTab === 'orders' ? 'active' : ''} onClick={() => { setAdminTab('orders'); loadAdminOrders(0) }}>Orders</button>
          </div>
          {adminLoading && <p>Loading admin data…</p>}

          {adminTab === 'products' && <div className="admin-grid">
            <form className="admin-form" onSubmit={submitProductForm}>
              <h3>{productForm.id ? `Edit product #${productForm.id}` : 'Create product'}</h3>
              <div className="form-field"><label htmlFor="choose-product">Choose an existing product to edit</label><select id="choose-product" value={productForm.id} onChange={(event) => selectProductToEdit(event.target.value)}><option value="">Create a new product…</option>{adminProducts.map((product) => <option key={product.id} value={product.id}>{product.name} (#{product.id})</option>)}</select></div>
              <div className="form-field"><label htmlFor="admin-product-name">Product name</label><input id="admin-product-name" value={productForm.name} onChange={(event) => setProductForm((current) => ({ ...current, name: event.target.value }))} required /></div>
              <div className="form-grid compact-grid">
                <div className="form-field"><label htmlFor="admin-product-price">Price (₹)</label><input id="admin-product-price" type="number" min="0.01" step="0.01" value={productForm.price} onChange={(event) => setProductForm((current) => ({ ...current, price: event.target.value }))} required /></div>
                <div className="form-field"><label htmlFor="admin-product-stock">Stock quantity</label><input id="admin-product-stock" type="number" min="0" step="1" value={productForm.stockQuantity} onChange={(event) => setProductForm((current) => ({ ...current, stockQuantity: event.target.value }))} required /></div>
              </div>
              <div className="form-field"><label htmlFor="admin-product-category">Category</label><select id="admin-product-category" value={productForm.categoryId} onChange={(event) => setProductForm((current) => ({ ...current, categoryId: event.target.value }))} required><option value="">Choose category</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></div>
              <div className="form-field"><label htmlFor="admin-product-image">Image URL</label><input id="admin-product-image" type="url" placeholder="https://…" value={productForm.imageUrl} onChange={(event) => setProductForm((current) => ({ ...current, imageUrl: event.target.value }))} /></div>
              <div className="action-row"><button className="primary-button" disabled={adminSubmitting || adminLoading} type="submit">{adminSubmitting ? 'Saving…' : productForm.id ? 'Save product changes' : 'Create product'}</button>{productForm.id && <button className="danger-button" type="button" disabled={adminSubmitting} onClick={() => deleteProduct(productForm.id)}>Delete product</button>}<button className="secondary-button" type="button" onClick={resetProductForm}>Clear</button></div>
            </form>
            <div className="admin-list"><h3>Products</h3><p className="muted-note">Select a product to edit its name, price, category, stock and image.</p>{adminProducts.map((product) => <button className="admin-list-item" type="button" key={product.id} onClick={() => selectProductToEdit(String(product.id))}><span>{product.name}<small>#{product.id} · {product.category?.name || 'Uncategorised'}</small></span><strong>{formatPrice(product.price)}</strong></button>)}</div>
          </div>}

          {adminTab === 'categories' && <div className="admin-grid">
            <form className="admin-form" onSubmit={submitCategoryForm}>
              <h3>{categoryForm.id ? `Edit category #${categoryForm.id}` : 'Create category'}</h3>
              <div className="form-field"><label htmlFor="choose-category">Choose an existing category</label><select id="choose-category" value={categoryForm.id} onChange={(event) => selectCategoryToEdit(event.target.value)}><option value="">Create a new category…</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></div>
              <div className="form-field"><label htmlFor="admin-category-name">Category name</label><input id="admin-category-name" value={categoryForm.name} onChange={(event) => setCategoryForm((current) => ({ ...current, name: event.target.value }))} required /></div>
              <div className="action-row"><button className="primary-button" type="submit" disabled={adminSubmitting}>{adminSubmitting ? 'Saving…' : categoryForm.id ? 'Save category' : 'Create category'}</button>{categoryForm.id && <button className="danger-button" type="button" disabled={adminSubmitting} onClick={() => deleteCategory(categoryForm.id)}>Delete</button>}<button className="secondary-button" type="button" onClick={() => setCategoryForm({ ...EMPTY_CATEGORY_FORM })}>Clear</button></div>
            </form>
            <div className="admin-list"><h3>Categories</h3>{categories.map((category) => <button className="admin-list-item" type="button" key={category.id} onClick={() => selectCategoryToEdit(String(category.id))}><span>{category.name}<small>Category #{category.id}</small></span><span>Edit →</span></button>)}</div>
          </div>}

          {adminTab === 'orders' && <div className="admin-order-form">
            <div className="panel-header"><div><h3>All customer orders</h3><p className="muted-note">Page {adminOrderPage + 1} of {adminOrderPageInfo.totalPages} · {adminOrderPageInfo.totalElements} orders</p></div><button className="secondary-button" type="button" onClick={() => loadAdminOrders(adminOrderPage)} disabled={adminOrdersLoading}>{adminOrdersLoading ? 'Refreshing…' : 'Refresh'}</button></div>
            {adminOrdersLoading && <p>Loading orders…</p>}
            {!adminOrdersLoading && adminOrders.length === 0 && <div className="empty-state"><p>No orders found.</p></div>}
            {!adminOrdersLoading && adminOrders.length > 0 && <div className="order-list">{adminOrders.map((order) => {
              const nextStatuses = order.status === 'PLACED' ? ['CANCELLED'] : order.status === 'CONFIRMED' ? ['SHIPPED'] : order.status === 'SHIPPED' ? ['DELIVERED'] : []
              return <article className="order-card" key={order.orderId}>
                <div className="order-card-top"><div><h3>Order #{order.orderId} · {order.customerName || 'Customer'}</h3><p>{order.customerEmail || 'No customer email'} · {formatDate(order.createdAt)}</p></div><span className="status-pill">{order.status}</span></div>
                <div className="order-card-bottom"><strong>{formatPrice(order.totalAmount)}</strong><span>{(order.items || []).length} line item(s)</span></div>
                {nextStatuses.length > 0 && <div className="action-row" style={{ marginTop: 12 }}>{nextStatuses.map((status) => <button className={status === 'CANCELLED' ? 'danger-button' : 'secondary-button'} key={status} type="button" disabled={adminSubmitting} onClick={() => updateAdminOrderStatusFor(order.orderId, status)}>{status === 'CANCELLED' ? 'Cancel unpaid order' : `Mark ${status.toLowerCase()}`}</button>)}</div>}
              </article>
            })}</div>}
            <div className="pagination"><button className="secondary-button" type="button" disabled={adminOrdersLoading || adminOrderPage === 0} onClick={() => loadAdminOrders(adminOrderPage - 1)}>Previous</button><span>Page {adminOrderPage + 1} of {adminOrderPageInfo.totalPages}</span><button className="secondary-button" type="button" disabled={adminOrdersLoading || adminOrderPage + 1 >= adminOrderPageInfo.totalPages} onClick={() => loadAdminOrders(adminOrderPage + 1)}>Next</button></div>
            <form className="admin-form form-grid" onSubmit={updateOrderStatus} style={{ marginTop: 24 }}>
              <h3>Manual status update</h3>
              <div className="form-field"><label htmlFor="admin-order-id">Order ID</label><input id="admin-order-id" inputMode="numeric" pattern="[0-9]+" value={adminOrderId} onChange={(event) => setAdminOrderId(event.target.value)} required /></div>
              <div className="form-field"><label htmlFor="admin-order-status">New status</label><select id="admin-order-status" value={adminOrderStatus} onChange={(event) => setAdminOrderStatus(event.target.value)}>{ORDER_STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}</select></div>
              <div className="form-actions"><button className="primary-button" type="submit" disabled={adminSubmitting}>{adminSubmitting ? 'Updating…' : 'Update order status'}</button></div>
            </form>
            <p className="muted-note">Allowed transitions are checked by the backend. A successful payment is required before confirming an order. Paid-order cancellation must use the customer cancellation/refund workflow.</p>
          </div>}
        </section>
      )}

      <main id="top">
        <section className="hero">
          <div className="hero-content">
            <p className="eyebrow">THE EVERYDAY COLLECTION</p>
            <h1>Good things.<br />Better living.</h1>
            <p className="hero-description">Thoughtfully selected essentials for the way you live, work, and explore.</p>
            <a className="shop-button" href="#products">Explore the collection <span>→</span></a>
          </div>
          <div className="hero-decoration"><span>LESS, BUT BETTER.</span><div className="hero-circle">n.</div></div>
        </section>

        <section className="products-section" id="products">
          {!selectedProduct ? <>
            <div className="section-heading"><div><p className="eyebrow">CURATED FOR YOU</p><h2>Everyday favourites</h2></div><p className="product-count">{loading ? 'Loading…' : `${pageInfo.totalElements} products`}</p></div>
            <form className="filter-panel" onSubmit={applyFilters}>
              <div className="form-field"><label htmlFor="product-search">Search products</label><input id="product-search" type="search" placeholder="Product name…" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} /></div>
              <div className="form-field"><label htmlFor="category-filter">Category</label><select id="category-filter" value={categoryInput} onChange={(event) => setCategoryInput(event.target.value)}><option value="">All categories</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></div>
              <div className="form-field"><label htmlFor="min-price">Minimum price (₹)</label><input id="min-price" type="number" min="0" step="0.01" placeholder="0" value={minPriceInput} onChange={(event) => setMinPriceInput(event.target.value)} /></div>
              <div className="form-field"><label htmlFor="max-price">Maximum price (₹)</label><input id="max-price" type="number" min="0" step="0.01" placeholder="Any" value={maxPriceInput} onChange={(event) => setMaxPriceInput(event.target.value)} /></div>
              <div className="form-field"><label htmlFor="product-sort">Sort by</label><select id="product-sort" value={filters.sort} onChange={(event) => { setFilters((current) => ({ ...current, sort: event.target.value })); setPage(0) }}><option value="name,asc">Name: A to Z</option><option value="name,desc">Name: Z to A</option><option value="price,asc">Price: low to high</option><option value="price,desc">Price: high to low</option><option value="id,desc">Newest first</option><option value="id,asc">Oldest first</option></select></div>
              <div className="form-actions filter-actions"><button className="primary-button" type="submit">Apply filters</button><button className="secondary-button" type="button" onClick={clearFilters}>Clear</button></div>
            </form>
            {categoriesLoading && <p className="muted-note">Loading categories…</p>}
            {categoriesError && <p className="inline-error" role="alert">Category list: {categoriesError}</p>}
            {loading && <p>Loading products from our store…</p>}
            {!loading && error && <div className="empty-state" role="alert"><p>Could not load products: {error}</p><button className="secondary-button" type="button" onClick={() => setFilters((current) => ({ ...current }))}>Retry</button></div>}
            {!loading && !error && products.length === 0 && <div className="empty-state"><p>No matching products. Try changing or clearing your filters.</p></div>}
            {!loading && !error && products.length > 0 && <>
              <div className="product-grid">{products.map((product) => {
                const inStock = Number(product.stockQuantity ?? 0) > 0
                return <article className="product-card" key={product.id}>
                  <button type="button" className="product-image product-image-button" onClick={() => { setSelectedProduct(product); window.scrollTo({ top: 0, behavior: 'smooth' }) }} aria-label={`View details for ${product.name}`}>
                    <img src={getProductImage(product)} alt={product.name} loading="lazy" onError={(event) => { event.currentTarget.onerror = null; event.currentTarget.src = 'https://placehold.co/600x450?text=Image+Unavailable' }} />
                  </button>
                  <div className="product-info"><div><p className="product-category">{product.category?.name ?? 'Essentials'}</p><h3>{product.name}</h3><p className="product-price">{formatPrice(product.price)}</p>{!inStock && <p className="product-category">Out of stock</p>}</div><button className="add-button" type="button" disabled={!inStock} onClick={() => addToCart(product)} aria-label={`Add ${product.name} to cart`} title={inStock ? 'Add to cart' : 'Out of stock'}>+</button></div>
                </article>
              })}</div>
              {pageInfo.totalPages > 1 && <nav className="pagination" aria-label="Product pages"><button className="secondary-button" type="button" disabled={page === 0 || loading} onClick={() => { setPage((current) => Math.max(0, current - 1)); document.getElementById('products')?.scrollIntoView({ behavior: 'smooth' }) }}>Previous</button><span>Page {page + 1} of {pageInfo.totalPages}</span><button className="secondary-button" type="button" disabled={page + 1 >= pageInfo.totalPages || loading} onClick={() => { setPage((current) => Math.min(pageInfo.totalPages - 1, current + 1)); document.getElementById('products')?.scrollIntoView({ behavior: 'smooth' }) }}>Next</button></nav>}
            </>}
          </> : <div className="product-detail"><button type="button" className="back-button" onClick={() => setSelectedProduct(null)}>← Back to products</button><div className="product-detail-content"><img className="product-detail-image" src={getProductImage(selectedProduct)} alt={selectedProduct.name} onError={(event) => { event.currentTarget.onerror = null; event.currentTarget.src = 'https://placehold.co/600x450?text=Image+Unavailable' }} /><div className="product-detail-info"><p className="eyebrow">{selectedProduct.category?.name ?? 'Essentials'}</p><h2>{selectedProduct.name}</h2><p className="product-detail-price">{formatPrice(selectedProduct.price)}</p><p>{selectedProduct.description || 'A thoughtfully selected everyday essential.'}</p><p className="product-stock">{Number(selectedProduct.stockQuantity ?? 0) > 0 ? `${selectedProduct.stockQuantity} in stock` : 'Out of stock'}</p><button className="primary-button" type="button" disabled={Number(selectedProduct.stockQuantity ?? 0) <= 0} onClick={() => addToCart(selectedProduct)}>{Number(selectedProduct.stockQuantity ?? 0) > 0 ? 'Add to cart +' : 'Out of stock'}</button></div></div></div>}
        </section>

        <section className="about-section" id="about"><p className="eyebrow">A LITTLE MORE THOUGHTFUL</p><h2>Everyday essentials, chosen with care.</h2><p>Quality finds. Simple choices. Things worth keeping.</p></section>
      </main>

      <footer className="footer"><a className="brand" href="#top">nook<span>.</span></a><p>Made for everyday living.</p></footer>
    </div>
  )
}

export default App
