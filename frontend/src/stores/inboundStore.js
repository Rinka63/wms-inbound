const STORAGE_KEY = 'wms-inbound-practice-v3'

export const RECEIPT_STATUS = { 0: '未完成', 1: '已完成'}
export const PUTAWAY_STATUS = { 1: '上架中', 2: '已完成' }

export function units(value) {
  const text = String(value ?? '').trim()
  if (!/^\d{1,15}(?:\.\d{1,3})?$/.test(text)) {
    throw new Error('数量必须是非负数，最多 15 位整数、3 位小数；不接受空值或科学计数法。')
  }
  const [integer, fraction = ''] = text.split('.')
  return BigInt(integer) * 1000n + BigInt(fraction.padEnd(3, '0'))
}

function decimal(value) {
  if (value < 0n) throw new Error('数量不能为负数。')
  return `${value / 1000n}.${String(value % 1000n).padStart(3, '0')}`
}

export function formatQuantity(value) {
  const number = units(value)
  return `${String(number / 1000n).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${String(number % 1000n).padStart(3, '0')}`
}

function total(items, field) {
  return decimal(items.reduce((sum, item) => sum + units(item[field] || '0'), 0n))
}

const copy = value => JSON.parse(JSON.stringify(value))

function now() {
  const date = new Date()
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function find(rows, id, label = '记录') {
  const result = rows.find(item => String(item.id) === String(id))
  if (!result) throw new Error(`${label}不存在，请刷新页面。`)
  return result
}

function inboundItem(db, id) {
  for (const order of db.inbounds) {
    const item = order.items.find(line => line.id === id)
    if (item) return { order, item }
  }
  throw new Error('来源入库明细不存在。')
}

function receiptItem(db, id) {
  for (const order of db.receipts) {
    const item = order.items.find(line => line.id === id)
    if (item) return { order, item }
  }
  throw new Error('来源收货明细不存在。')
}

function postedForReceiptItem(db, id) {
  return db.putaways
    .filter(order => order.status === 2)
    .reduce((sum, order) => sum + order.items
      .filter(item => item.receiptOrderItemId === id)
      .reduce((lineSum, item) => lineSum + units(item.putawayQty), 0n), 0n)
}

function availableForReceipt(db, receipt) {
  return decimal(receipt.items.reduce((sum, item) => (
    sum + units(item.receivedQty) - postedForReceiptItem(db, item.id)
  ), 0n))
}

function recomputeInbounds(db) {
  for (const order of db.inbounds) {
    for (const item of order.items) {
      item.receivedQty = decimal(db.receipts.reduce((sum, receipt) => (
          sum + receipt.items
              .filter(line => line.inboundOrderItemId === item.id)
              .reduce((lineSum, line) => lineSum + units(line.receivedQty), 0n)
      ), 0n))

      item.putawayQty = decimal(db.putaways.reduce((sum, putaway) => (
          sum + putaway.items
              .filter(line => line.inboundOrderItemId === item.id)
              .reduce((lineSum, line) => lineSum + units(line.putawayQty), 0n)
      ), 0n))
    }

    if ([0, 6].includes(order.status)) continue

    const planned = units(total(order.items, 'planQty'))
    const received = units(total(order.items, 'receivedQty'))
    const putaway = units(total(order.items, 'putawayQty'))
    order.status = received === 0n ? 1 : received < planned ? 2 : putaway === 0n ? 3 : putaway < planned ? 4 : 5
  }
}

function postInventory(db, order) {
  for (const item of order.items) {
    let inventory = db.inventory.find(row => (
      row.warehouseId === order.warehouseId && row.locationId === item.locationId && row.skuId === item.skuId
    ))

    if (!inventory) {
      inventory = {
        id: ++db.nextId,
        warehouseId: order.warehouseId,
        locationId: item.locationId,
        skuId: item.skuId,
        quantity: '0.000',
      }
      db.inventory.push(inventory)
    }

    const before = inventory.quantity
    inventory.quantity = decimal(units(before) + units(item.putawayQty))
    db.transactions.push({
      id: ++db.nextId,
      inventoryId: inventory.id,
      putawayOrderId: order.id,
      changeQty: item.putawayQty,
      beforeQty: before,
      afterQty: inventory.quantity,
      createdTime: order.putawayTime,
    })
  }
}

function seedDatabase() {
  const db = {
    schema: 1,
    revision: 0,
    nextId: 10000,
    warehouses: [
      { id: 1, warehouseName: '华东一号仓' },
      { id: 2, warehouseName: '华南中心仓' },
    ],
    locations: [
      { id: 1001, warehouseId: 1, locationCode: 'A-01-01', status: 1 },
      { id: 1002, warehouseId: 1, locationCode: 'A-01-02', status: 1 },
      { id: 1003, warehouseId: 1, locationCode: 'DEFAULT', status: 1 },
      { id: 2001, warehouseId: 2, locationCode: 'A-01-01', status: 1 },
      { id: 2002, warehouseId: 2, locationCode: 'A-01-02', status: 1 },
      { id: 2003, warehouseId: 2, locationCode: 'DEFAULT', status: 1 },
    ],
    inbounds: [
      { id: 1, inboundOrderNo: 'IN202609240001', warehouseId: 1, status: 2, items: [
        { id: 1, skuId: 101, sku: 'SKU-APPLE-001', skuName: '苹果礼盒 12 枚', planQty: '600.000' },
        { id: 2, skuId: 102, sku: 'SKU-PEAR-002', skuName: '秋月梨 6 枚', planQty: '600.000' },
      ] },
      { id: 2, inboundOrderNo: 'IN202609240002', warehouseId: 1, status: 1, items: [
        { id: 3, skuId: 103, sku: 'SKU-MILK-008', skuName: '常温纯牛奶 250ml', planQty: '420.000' },
        { id: 6, skuId: 106, sku: 'SKU-JUICE-011', skuName: '100% 橙汁 1L', planQty: '440.000' },
      ] },
      { id: 3, inboundOrderNo: 'IN202609230018', warehouseId: 2, status: 3, items: [
        { id: 4, skuId: 104, sku: 'SKU-CUP-015', skuName: '陶瓷马克杯', planQty: '32.000' },
      ] },
      { id: 4, inboundOrderNo: 'IN202609230011', warehouseId: 1, status: 4, items: [
        { id: 5, skuId: 105, sku: 'SKU-BOX-021', skuName: '标准周转箱', planQty: '2400.000' },
      ] },
      { id: 5, inboundOrderNo: 'IN202609220009', warehouseId: 2, status: 5, items: [
        { id: 7, skuId: 107, sku: 'SKU-TEA-003', skuName: '茶叶礼盒', planQty: '510.000' },
      ] },
      // 待收货、尚无收货单，可用于新建收货单
      { id: 7, inboundOrderNo: 'IN202609250001', warehouseId: 1, status: 1, items: [
        { id: 9, skuId: 109, sku: 'SKU-OIL-006', skuName: '花生油 5L', planQty: '300.000' },
        { id: 10, skuId: 110, sku: 'SKU-SALT-007', skuName: '食用盐 400g', planQty: '800.000' },
      ] },
      { id: 8, inboundOrderNo: 'IN202609250002', warehouseId: 2, status: 1, items: [
        { id: 11, skuId: 111, sku: 'SKU-WATER-009', skuName: '矿泉水 550ml×24', planQty: '150.000' },
      ] },
    ],
    receipts: [],
    putaways: [],
    inventory: [],
    transactions: [],
  }

  const receipt = (id, no, inboundId, status, quantities, remark, createdTime) => {
    const parent = find(db.inbounds, inboundId)
    return {
      id,
      receiptOrderNo: no,
      inboundOrderId: parent.id,
      warehouseId: parent.warehouseId,
      receiverId: 8,
      receiverName: '王海',
      status,
      version: 1,
      remark,
      createdTime,
      updatedTime: createdTime,
      receivedTime: status === 1 ? createdTime : null,
      items: quantities.map(([line, qty], index) => ({
        id: id * 100 + index + 1,
        receiptOrderId: id,
        inboundOrderItemId: line,
        skuId: inboundItem(db, line).item.skuId,
        receivedQty: qty,
      })),
    }
  }

  db.receipts = [
    receipt(1, 'RC202609240008', 1, 1, [[1, '600.000'], [2, '120.000']], '供应商第一批到货。', '2026-09-24 11:36:00'),
    receipt(2, 'RC202609240011', 2, 0, [[3, '420.000']], '已录入数量，尚未确认。', '2026-09-24 13:20:00'),
    receipt(3, 'RC202609230019', 3, 1, [[4, '32.000']], '退货收货完成。', '2026-09-23 17:15:00'),
    receipt(4, 'RC202609230012', 4, 1, [[5, '2400.000']], '调拨商品到货。', '2026-09-23 14:08:00'),
    receipt(6, 'RC202609220010', 5, 1, [[7, '510.000']], '全部到货。', '2026-09-22 16:00:00'),
  ]

  const putaway = (id, no, receiptId, status, entries, remark, time) => {
    const parent = find(db.receipts, receiptId)
    return {
      id,
      putawayOrderNo: no,
      inboundOrderId: parent.inboundOrderId,
      receiptOrderId: parent.id,
      warehouseId: parent.warehouseId,
      operatorId: 12,
      operatorName: '赵一',
      status,
      version: 1,
      remark,
      createdTime: time,
      updatedTime: time,
      putawayTime: status === 2 ? time : null,
      items: entries.map(([sourceId, qty, locationId], index) => {
        const source = receiptItem(db, sourceId).item
        return {
          id: id * 100 + index + 1,
          putawayOrderId: id,
          receiptOrderItemId: sourceId,
          inboundOrderItemId: source.inboundOrderItemId,
          skuId: source.skuId,
          putawayQty: qty,
          locationId,
        }
      }),
    }
  }

  db.putaways = [
    putaway(1, 'PA202609240003', 1, 2, [[101, '480.000', 1001]], '首批上架，剩余数量另建一单。', '2026-09-24 13:05:00'),
    putaway(2, 'PA202609240006', 1, 1, [[101, '120.000', 1001], [102, '60.000', 1002]], '已暂存，尚未计入库存。', '2026-09-24 14:12:00'),
    putaway(4, 'PA202609230004', 4, 2, [[401, '1800.000', 1002]], '本批上架 1800，来源尚余 600。', '2026-09-23 15:46:00'),
    putaway(6, 'PA202609220005', 6, 2, [[601, '510.000', 2001]], '全部上架完成。', '2026-09-22 17:20:00'),
  ]

  db.putaways.filter(order => order.status === 2).forEach(order => postInventory(db, order))
  recomputeInbounds(db)
  return db
}

class DemoStore {
  constructor(storage) {
    this.storage = storage
    if (!this.storage.getItem(STORAGE_KEY)) this.reset()
    this.read()
  }

  read() {
    const raw = this.storage.getItem(STORAGE_KEY)
    if (!raw) throw new Error('演示数据不存在。')

    let db
    try {
      db = JSON.parse(raw)
    } catch {
      throw new Error('本地演示数据损坏；请先备份，再重置演示。')
    }

    if (
      db.schema !== 1 ||
      !Array.isArray(db.inbounds) ||
      !Array.isArray(db.receipts) ||
      !Array.isArray(db.putaways)
    ) {
      throw new Error('本地演示数据版本不兼容。')
    }
    return db
  }

  reset() {
    this.storage.setItem(STORAGE_KEY, JSON.stringify(seedDatabase()))
  }

  transaction(action) {
    const original = this.read()
    const next = copy(original)
    const result = action(next)
    recomputeInbounds(next)

    if (this.read().revision !== original.revision) {
      throw new Error('数据已变化，请刷新后重试。')
    }

    next.revision++
    this.storage.setItem(STORAGE_KEY, JSON.stringify(next))
    return copy(result)
  }
}

class ReceiptService extends DemoStore {
  list() {
    const db = this.read()
    return db.receipts.map(row => {
      const inbound = find(db.inbounds, row.inboundOrderId)
      const warehouse = find(db.warehouses, row.warehouseId)
      const items = row.items.map(item => {
        const source = inboundItem(db, item.inboundOrderItemId).item
        return { ...item, sku: source.sku, skuName: source.skuName }
      })

      return {
        ...row,
        inboundOrderNo: inbound.inboundOrderNo,
        warehouseName: warehouse.warehouseName,
        items,
        skuCount: new Set(row.items.map(item => item.skuId)).size,
        totalQty: total(row.items, 'receivedQty'),
      }
    }).sort((a, b) => b.createdTime.localeCompare(a.createdTime) || b.id - a.id)
  }

  sources() {
    const db = this.read()
    return db.inbounds
        .filter(order =>
            [1, 2].includes(order.status) &&
            !db.receipts.some(r => r.inboundOrderId === order.id)
        )
        .map(order => ({
          id: order.id,
          label: order.inboundOrderNo,
          warehouseId: order.warehouseId,
        }))
  }

  form(sourceId) {
    const db = this.read()
    const source = find(db.inbounds, Number(sourceId), '来源入库单')
    if (![1, 2].includes(source.status)) {
      throw new Error('只能从待收货或部分收货的入库单创建收货单。')
    }

    const warehouse = find(db.warehouses, source.warehouseId)
    const items = source.items.map(item => {
      const planned = units(item.planQty)
      const received = units(item.receivedQty)
      return {
        sourceItemId: item.id,
        inboundOrderItemId: item.id,
        skuId: item.skuId,
        sourceQty: decimal(planned),
        priorQty: decimal(received),
        maxQty: decimal(planned - received),
        qty: '',
      }
    }).filter(item => units(item.maxQty) > 0n)

    return {
      id: null,
      sourceId: source.id,
      sourceLabel: source.inboundOrderNo,
      documentNo: '确认时生成',
      warehouseId: source.warehouseId,
      warehouseName: warehouse.warehouseName,
      remark: '',
      items,
    }
  }

  save(form, user = { id: 12, name: '演示操作员' }) {
    return this.transaction(db => {
      const source = find(db.inbounds, Number(form.sourceId), '来源入库单')

      if (db.receipts.some(r => r.inboundOrderId === source.id)) {
        throw new Error('该入库单已有收货单，请在列表中继续收货。')
      }

      const seen = new Set()
      const lines = []
      for (const input of form.items) {
        if (seen.has(input.sourceItemId)) throw new Error('同一收货单不允许重复来源明细。')
        seen.add(input.sourceItemId)

        const src = find(source.items, input.sourceItemId, '来源明细')
        const qty = String(input.qty ?? '').trim() === '' ? 0n : units(input.qty)
        if (qty === 0n) continue

        const available = units(src.planQty) - units(src.receivedQty)
        if (qty > available) {
          throw new Error(`SKU ${src.skuId} 超过当前可收货数量 ${formatQuantity(decimal(available))}。`)
        }

        lines.push({
          id: ++db.nextId,
          inboundOrderItemId: src.id,
          skuId: src.skuId,
          receivedQty: decimal(qty),
        })
      }

      if (lines.length === 0) throw new Error('请至少填写一行大于 0 的数量。')
      if (String(form.remark || '').length > 500) throw new Error('备注最多 500 字。')

      const time = now()
      const id = ++db.nextId
      const row = {
        id,
        receiptOrderNo: `RC${time.slice(0, 10).replaceAll('-', '')}${String(id).padStart(6, '0')}`,
        inboundOrderId: source.id,
        warehouseId: source.warehouseId,
        receiverId: user.id,
        receiverName: user.name,
        remark: String(form.remark || '').trim(),
        createdTime: time,
        updatedTime: time,
        receivedTime: time,
        items: lines.map(item => ({ ...item, receiptOrderId: id })),
      }

      // 推算收货单状态：所有入库明细都收齐则已完成
      const allDone = source.items.every(item => {
        const thisQty = lines.find(l => l.inboundOrderItemId === item.id)
        const added = thisQty ? units(thisQty.receivedQty) : 0n
        return units(item.receivedQty) + added >= units(item.planQty)
      })
      row.status = allDone ? 1 : 0

      db.receipts.push(row)
      return row
    })
  }

}

class PutawayService extends DemoStore {
  list() {
    const db = this.read()
    return db.putaways.map(row => {
      const inbound = find(db.inbounds, row.inboundOrderId)
      const receipt = find(db.receipts, row.receiptOrderId)
      const warehouse = find(db.warehouses, row.warehouseId)
      const items = row.items.map(item => {
        const source = inboundItem(db, item.inboundOrderItemId).item
        const location = find(db.locations, item.locationId, '库位')
        return { ...item, sku: source.sku, skuName: source.skuName, locationCode: location.locationCode }
      })

      return {
        ...row,
        inboundOrderNo: inbound.inboundOrderNo,
        receiptOrderNo: receipt.receiptOrderNo,
        warehouseName: warehouse.warehouseName,
        items,
        skuCount: new Set(row.items.map(item => item.skuId)).size,
        totalQty: total(row.items, 'putawayQty'),
        locations: [...new Set(items.map(item => item.locationCode))],
      }
    }).sort((a, b) => b.createdTime.localeCompare(a.createdTime) || b.id - a.id)
  }

  sources() {
    const db = this.read()
    return db.receipts
        .filter(receipt => [0, 1].includes(receipt.status) && receipt.items.length > 0)
        .map(receipt => ({
          id: receipt.id,
          label: `${receipt.receiptOrderNo} · ${find(db.inbounds, receipt.inboundOrderId).inboundOrderNo}`,
          warehouseId: receipt.warehouseId,
        }))
  }

  form(sourceId, orderId = null) {
    const db = this.read()
    let existing = orderId ? find(db.putaways, Number(orderId)) : null

    if (existing && existing.status !== 1) {
      throw new Error('只有上架中的上架单可以继续办理。')
    }
    if (existing) sourceId = existing.receiptOrderId

    const source = find(db.receipts, Number(sourceId), '来源收货单')
    const warehouse = find(db.warehouses, source.warehouseId)
    const inbound = find(db.inbounds, source.inboundOrderId)

    const items = source.items.map(item => {
      const old = existing?.items.find(line => line.receiptOrderItemId === item.id)
      const received = units(item.receivedQty)
      const posted = postedForReceiptItem(db, item.id)
      return {
        sourceItemId: item.id,
        inboundOrderItemId: item.inboundOrderItemId,
        skuId: item.skuId,
        sourceQty: decimal(received),
        priorQty: decimal(posted),
        maxQty: decimal(received - posted),
        qty: old?.putawayQty || '',
        locationCode: old?.locationCode || '',
      }
    }).filter(item => units(item.maxQty) > 0n || item.qty !== '')

    return {
      id: existing?.id || null,
      sourceId: source.id,
      sourceLabel: `${source.receiptOrderNo} · ${inbound.inboundOrderNo}`,
      documentNo: existing?.putawayOrderNo || '确认时生成',
      warehouseId: source.warehouseId,
      warehouseName: warehouse.warehouseName,
      status: existing?.status ?? 1,
      remark: existing?.remark || '',
      items,
    }
  }

  save(form, user = { id: 12, name: '演示操作员' }) {
    return this.transaction(db => {
      let row = form.id ? find(db.putaways, Number(form.id)) : null
      if (row && row.status !== 1) throw new Error('上架单已完成，不能修改。')

      const source = find(db.receipts, Number(form.sourceId), '来源收货单')
      if (row && row.receiptOrderId !== source.id) throw new Error('已保存上架单不能变更来源。')
      if (!row && db.putaways.some(item => item.receiptOrderId === source.id && item.status === 1)) {
        throw new Error('该收货单已有上架中的上架单，请使用"继续上架单"。')
      }

      const parent = find(db.inbounds, source.inboundOrderId)
      const seen = new Set()
      const lines = []

      for (const input of form.items) {
        if (seen.has(input.sourceItemId)) throw new Error('同一上架单不允许重复来源明细。')
        seen.add(input.sourceItemId)

        const src = find(source.items, input.sourceItemId, '来源明细')
        const qty = String(input.qty ?? '').trim() === '' ? 0n : units(input.qty)
        if (qty === 0n) continue

        const available = units(src.receivedQty) - postedForReceiptItem(db, src.id)
        if (qty > available) {
          throw new Error(`SKU ${src.skuId} 超过当前可上架数量 ${formatQuantity(decimal(available))}。`)
        }

        const locationCode = String(input.locationCode || '').trim()
        if (!locationCode) throw new Error(`SKU ${src.skuId} 请填写目标库位。`)
        const location = db.locations.find(l =>
            l.warehouseId === source.warehouseId && l.locationCode === locationCode && l.status === 1
        )
        if (!location) throw new Error(`库位 ${locationCode} 不存在或未启用。`)

        const old = row?.items.find(item => item.receiptOrderItemId === src.id)
        lines.push({
          id: old?.id || ++db.nextId,
          receiptOrderItemId: src.id,
          inboundOrderItemId: src.inboundOrderItemId,
          skuId: src.skuId,
          putawayQty: decimal(qty),
          locationId: location.id,
          locationCode: location.locationCode,
        })
      }

      if (lines.length === 0) throw new Error('请至少填写一行大于 0 的数量。')
      if (String(form.remark || '').length > 500) throw new Error('备注最多 500 字。')

      const time = now()
      const id = row?.id || ++db.nextId
      if (!row) {
        row = {
          id,
          putawayOrderNo: `PA${time.slice(0, 10).replaceAll('-', '')}${String(id).padStart(6, '0')}`,
          inboundOrderId: parent.id,
          receiptOrderId: source.id,
          warehouseId: source.warehouseId,
          createdTime: time,
          items: [],
        }
        db.putaways.push(row)
      }

      row.items = lines.map(item => ({ ...item, putawayOrderId: id }))
      row.remark = String(form.remark || '').trim()
      row.updatedTime = time
      row.operatorId = user.id
      row.operatorName = user.name

      // 累加上架库存
      postInventory(db, row)

      // 推算状态：入库单所有明细的 putawayQty >= planQty 则已完成
      const allDone = parent.items.every(item => {
        const totalPutaway = db.putaways
            .filter(p => p.status === 2 || p.id === id)
            .reduce((sum, p) =>
                    sum + p.items
                        .filter(l => l.inboundOrderItemId === item.id)
                        .reduce((s, l) => s + units(l.putawayQty), 0n)
                , 0n)
        return totalPutaway >= units(item.planQty)
      })

      row.status = allDone ? 2 : 1
      row.putawayTime = allDone ? time : null

      return row
    })
  }
}

export function createReceiptService(storage) {
  return new ReceiptService(storage)
}

export function createPutawayService(storage) {
  return new PutawayService(storage)
}

export { STORAGE_KEY }
