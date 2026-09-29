<script setup>
/*
 * 可直接放入 frontend/src/views/ 的单文件组件。
 * 当前使用 localStorage 作为练习项目的演示数据层：暂存不入账，确认后才更新累计数量/库存。
 * 接入后端时，保留模板与 useOrderPage，替换 DemoService 的 read/list/sources/form/save/cancel 即可。
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { useUserStore } from '../stores/user'

/**
 * WMS 练习项目：浏览器演示数据服务，不是后端。
 * 所有数量以 DECIMAL(18,3) 字符串交换，计算使用 BigInt 千分单位。
 * 草稿/暂存不计入累计数量、不生成库存；确认一个批次只入账一次。
 * localStorage 仅用于单人演示，不能替代数据库事务、权限和并发锁。
 */
const STORAGE_KEY = 'wms-inbound-practice-v1';
const STATUS = {
  receipt: {0: '未完成', 1: '已完成', 2: '已取消'},
  putaway: {0: '待上架', 1: '上架中', 2: '已完成', 3: '已取消'}
};
function units(value) {
  const s = String(value ?? '').trim();
  if (!/^\d{1,15}(?:\.\d{1,3})?$/.test(s)) {
    throw new Error('数量必须是非负数，最多 15 位整数、3 位小数；不接受空值或科学计数法。');
  }
  const [a,b=''] = s.split('.');
  return BigInt(a)*1000n + BigInt(b.padEnd(3,'0'));
}
function decimal(n) {
  if (n < 0n) throw new Error('数量不能为负数。');
  return `${n/1000n}.${String(n%1000n).padStart(3,'0')}`;
}
function quantity(value) {
  const n = units(value);
  return `${String(n/1000n).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${String(n%1000n).padStart(3,'0')}`;
}
function total(items, field) {
  return decimal(items.reduce((n,i)=>n+units(i[field] || '0'),0n));
}
const copy = x => JSON.parse(JSON.stringify(x));
const now = () => {
  const d=new Date(),p=n=>String(n).padStart(2,'0');
  return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
};
const find = (rows,id,label='记录') => {
  const result=rows.find(x=>String(x.id)===String(id));
  if(!result) throw new Error(`${label}不存在，请刷新页面。`);
  return result;
};
const isOpen = (kind,status) => kind==='receipt' ? status===0 : [0,1].includes(status);
const completed = kind => kind==='receipt' ? 1 : 2;
function inboundItem(db,id) {
  for(const order of db.inbounds) {
    const item=order.items.find(i=>i.id===id);
    if(item) return {order,item};
  }
  throw new Error('来源入库明细不存在。');
}
function receiptItem(db,id) {
  for(const order of db.receipts) {
    const item=order.items.find(i=>i.id===id);
    if(item) return {order,item};
  }
  throw new Error('来源收货明细不存在。');
}
function postedForReceiptItem(db,id) {
  return db.putaways.filter(p=>p.status===2).reduce((n,p)=>n+
      p.items.filter(i=>i.receiptOrderItemId===id).reduce((s,i)=>s+units(i.putawayQty),0n),0n);
}
function availableForReceipt(db,receipt) {
  return decimal(receipt.items.reduce((s,i)=>s+units(i.receivedQty)-postedForReceiptItem(db,i.id),0n));
}
function recomputeInbounds(db) {
  for(const order of db.inbounds) {
    for(const item of order.items) {
      item.receivedQty=decimal(db.receipts.filter(r=>r.status===1).reduce((s,r)=>s+
          r.items.filter(i=>i.inboundOrderItemId===item.id).reduce((a,i)=>a+units(i.receivedQty),0n),0n));
      item.putawayQty=decimal(db.putaways.filter(p=>p.status===2).reduce((s,p)=>s+
          p.items.filter(i=>i.inboundOrderItemId===item.id).reduce((a,i)=>a+units(i.putawayQty),0n),0n));
      if(units(item.putawayQty)>units(item.receivedQty) || units(item.receivedQty)>units(item.planQty)) {
        throw new Error('数量不一致：必须满足已上架 ≤ 已收货 ≤ 计划数量。');
      }
    }
    if([0,6].includes(order.status)) continue;
    const planned=units(total(order.items,'planQty'));
    const received=units(total(order.items,'receivedQty'));
    const putaway=units(total(order.items,'putawayQty'));
    order.status=received===0n?1:received<planned?2:putaway===0n?3:putaway<planned?4:5;
  }
}
function postInventory(db,order) {
  for(const item of order.items) {
    let inv=db.inventory.find(x=>x.warehouseId===order.warehouseId &&
        x.locationId===item.locationId && x.skuId===item.skuId);
    if(!inv) {
      inv={id:++db.nextId,warehouseId:order.warehouseId,locationId:item.locationId,
        skuId:item.skuId,quantity:'0.000'};
      db.inventory.push(inv);
    }
    const before=inv.quantity;
    inv.quantity=decimal(units(before)+units(item.putawayQty));
    // 一条上架明细对应一条流水；同单重复确认在 save 中被拦截。
    db.transactions.push({id:++db.nextId,inventoryId:inv.id,putawayOrderId:order.id,
      changeQty:item.putawayQty,beforeQty:before,afterQty:inv.quantity,createdTime:order.putawayTime});
  }
}
function seedDatabase() {
  const db={schema:1,revision:0,nextId:10000,
    warehouses:[{id:1,warehouseName:'华东一号仓'},{id:2,warehouseName:'华南中心仓'}],
    locations:[
      {id:1001,warehouseId:1,locationCode:'A-01-01',status:1},
      {id:1002,warehouseId:1,locationCode:'A-01-02',status:1},
      {id:1003,warehouseId:1,locationCode:'DEFAULT',status:1},
      {id:2001,warehouseId:2,locationCode:'A-01-01',status:1},
      {id:2002,warehouseId:2,locationCode:'A-01-02',status:1},
      {id:2003,warehouseId:2,locationCode:'DEFAULT',status:1}],
    inbounds:[
      {id:1,inboundOrderNo:'IN202609240001',warehouseId:1,status:2,items:[
          {id:1,skuId:101,sku:'SKU-APPLE-001',skuName:'苹果礼盒 12 枚',planQty:'600.000'},
          {id:2,skuId:102,sku:'SKU-PEAR-002',skuName:'秋月梨 6 枚',planQty:'600.000'}]},
      {id:2,inboundOrderNo:'IN202609240002',warehouseId:1,status:1,items:[
          {id:3,skuId:103,sku:'SKU-MILK-008',skuName:'常温纯牛奶 250ml',planQty:'420.000'},
          {id:6,skuId:106,sku:'SKU-JUICE-011',skuName:'100% 橙汁 1L',planQty:'440.000'}]},
      {id:3,inboundOrderNo:'IN202609230018',warehouseId:2,status:3,items:[
          {id:4,skuId:104,sku:'SKU-CUP-015',skuName:'陶瓷马克杯',planQty:'32.000'}]},
      {id:4,inboundOrderNo:'IN202609230011',warehouseId:1,status:4,items:[
          {id:5,skuId:105,sku:'SKU-BOX-021',skuName:'标准周转箱',planQty:'2400.000'}]},
      {id:5,inboundOrderNo:'IN202609220009',warehouseId:2,status:5,items:[
          {id:7,skuId:107,sku:'SKU-TEA-003',skuName:'茶叶礼盒',planQty:'510.000'}]},
      {id:6,inboundOrderNo:'IN202609220004',warehouseId:1,status:0,items:[
          {id:8,skuId:108,sku:'SKU-BAG-002',skuName:'环保购物袋',planQty:'120.000'}]}],
    receipts:[],putaways:[],inventory:[],transactions:[]};
  const receipt=(id,no,inboundId,status,quantities,remark,createdTime)=>{
    const parent=find(db.inbounds,inboundId);
    return {id,receiptOrderNo:no,inboundOrderId:parent.id,warehouseId:parent.warehouseId,
      receiverId:8,receiverName:'王海',status,version:1,remark,createdTime,updatedTime:createdTime,
      receivedTime:status===1?createdTime:null,
      items:quantities.map(([line,qty],index)=>({
        id:id*100+index+1,receiptOrderId:id,inboundOrderItemId:line,
        skuId:inboundItem(db,line).item.skuId,receivedQty:qty}))};
  };
  db.receipts=[
    receipt(1,'RC202609240008',1,1,[[1,'600.000'],[2,'120.000']],'供应商第一批到货。','2026-09-24 11:36:00'),
    receipt(2,'RC202609240011',2,0,[[3,'420.000']],'已录入数量，尚未确认。','2026-09-24 13:20:00'),
    receipt(3,'RC202609230019',3,1,[[4,'32.000']],'退货收货完成。','2026-09-23 17:15:00'),
    receipt(4,'RC202609230012',4,1,[[5,'2400.000']],'调拨商品到货。','2026-09-23 14:08:00'),
    receipt(5,'RC202609220006',6,2,[],'入库计划调整，已取消。','2026-09-22 10:12:00'),
    receipt(6,'RC202609220010',5,1,[[7,'510.000']],'全部到货。','2026-09-22 16:00:00')];
  const put=(id,no,receiptId,status,entries,remark,time)=>{
    const parent=find(db.receipts,receiptId);
    return {id,putawayOrderNo:no,inboundOrderId:parent.inboundOrderId,receiptOrderId:parent.id,
      warehouseId:parent.warehouseId,operatorId:12,operatorName:'赵一',status,version:1,remark,
      createdTime:time,updatedTime:time,putawayTime:status===2?time:null,
      items:entries.map(([sourceId,qty,locationId],index)=>({
        id:id*100+index+1,putawayOrderId:id,receiptOrderItemId:sourceId,
        inboundOrderItemId:receiptItem(db,sourceId).item.inboundOrderItemId,
        skuId:receiptItem(db,sourceId).item.skuId,putawayQty:qty,locationId}))};
  };
  db.putaways=[
    put(1,'PA202609240003',1,2,[[101,'480.000',1001]],'首批上架，剩余数量另建一单。','2026-09-24 13:05:00'),
    put(2,'PA202609240006',1,1,[[101,'120.000',1001],[102,'60.000',1002]],'已暂存，尚未计入库存。','2026-09-24 14:12:00'),
    put(3,'PA202609230009',3,0,[],'待选择数量和目标库位。','2026-09-23 17:28:00'),
    put(4,'PA202609230004',4,2,[[401,'1800.000',1002]],'本批上架 1800，来源尚余 600。','2026-09-23 15:46:00'),
    put(5,'PA202609220002',1,3,[],'重复创建，已取消。','2026-09-22 11:06:00'),
    put(6,'PA202609220005',6,2,[[601,'510.000',2001]],'全部上架完成。','2026-09-22 17:20:00')];
  db.putaways.filter(p=>p.status===2).forEach(p=>postInventory(db,p));
  recomputeInbounds(db);
  return db;
}
class DemoService {
  constructor(storage) {
    this.storage=storage;
    if(!this.storage.getItem(STORAGE_KEY)) this.storage.setItem(STORAGE_KEY,JSON.stringify(seedDatabase()));
    this.read();
  }
  read() {
    const raw=this.storage.getItem(STORAGE_KEY);
    if(!raw) throw new Error('演示数据不存在。');
    let db;
    try {db=JSON.parse(raw);} catch {throw new Error('本地演示数据损坏；请先备份，再重置演示。');}
    if(db.schema!==1 || !Array.isArray(db.inbounds)) throw new Error('本地演示数据版本不兼容。');
    return db;
  }
  transaction(action) {
    // 单浏览器演示：所有数据先在副本上校验，校验失败不写入。
    const original=this.read(),next=copy(original),result=action(next);
    recomputeInbounds(next);
    if(this.read().revision!==original.revision) throw new Error('数据已变化，请刷新后重试。');
    next.revision++;
    this.storage.setItem(STORAGE_KEY,JSON.stringify(next));
    return copy(result);
  }
  reset() { this.storage.setItem(STORAGE_KEY,JSON.stringify(seedDatabase())); }
  list(kind) {
    const db=this.read(),rows=kind==='receipt'?db.receipts:db.putaways;
    return rows.map(row=>{
      const parent=find(db.inbounds,row.inboundOrderId);
      const warehouse=find(db.warehouses,row.warehouseId);
      const result={...row,inboundOrderNo:parent.inboundOrderNo,warehouseName:warehouse.warehouseName,
        items:row.items.map(i=>{
          const source=inboundItem(db,i.inboundOrderItemId).item;
          const loc=kind==='putaway'?find(db.locations,i.locationId,'库位'):null;
          return {...i,sku:source.sku,skuName:source.skuName,locationCode:loc?.locationCode||''};
        })};
      result.skuCount=new Set(row.items.map(i=>i.skuId)).size;
      result.totalQty=total(row.items,kind==='receipt'?'receivedQty':'putawayQty');
      if(kind==='receipt') result.remainingQty=row.status===1?availableForReceipt(db,row):'0.000';
      else {
        const receipt=find(db.receipts,row.receiptOrderId);
        result.receiptOrderNo=receipt.receiptOrderNo;
        result.locations=[...new Set(result.items.map(i=>i.locationCode))];
      }
      return result;
    }).sort((a,b)=>b.createdTime.localeCompare(a.createdTime)||b.id-a.id);
  }
  sources(kind) {
    const db=this.read();
    if(kind==='receipt') return db.inbounds.filter(x=>![0,5,6].includes(x.status) &&
        x.items.some(i=>units(i.planQty)>units(i.receivedQty))).map(x=>({
      id:x.id,label:x.inboundOrderNo,warehouseId:x.warehouseId,
      remainingQty:decimal(units(total(x.items,'planQty'))-units(total(x.items,'receivedQty')))}));
    return db.receipts.filter(x=>x.status===1&&units(availableForReceipt(db,x))>0n).map(x=>({
      id:x.id,label:`${x.receiptOrderNo} · ${find(db.inbounds,x.inboundOrderId).inboundOrderNo}`,
      warehouseId:x.warehouseId,remainingQty:availableForReceipt(db,x)}));
  }
  form(kind,sourceId,orderId=null) {
    const db=this.read(),rows=kind==='receipt'?db.receipts:db.putaways;
    let existing=orderId?find(rows,Number(orderId)):null;
    const sourceField=kind==='receipt'?'inboundOrderId':'receiptOrderId';
    if(existing && !isOpen(kind,existing.status)) throw new Error('已完成或已取消的单据不能继续办理。');
    if(existing) sourceId=existing[sourceField];
    // 简化规则：一个来源只保留一张未完成单；再次新建自动继续该单。
    if(!existing) existing=rows.find(x=>x[sourceField]===Number(sourceId)&&isOpen(kind,x.status));
    const source=find(kind==='receipt'?db.inbounds:db.receipts,Number(sourceId),'来源单据');
    if(kind==='receipt' && [0,5,6].includes(source.status)) throw new Error('只能从已提交、仍有待收货数量的入库单创建收货单。');
    if(kind==='putaway' && source.status!==1) throw new Error('只能从已完成收货单创建上架单。');
    const warehouse=find(db.warehouses,source.warehouseId);
    const lines=source.items.map(i=>{
      const old=existing?.items.find(x=>kind==='receipt'?x.inboundOrderItemId===i.id:x.receiptOrderItemId===i.id);
      const sku=kind==='receipt'?i:inboundItem(db,i.inboundOrderItemId).item;
      const prior=kind==='receipt'?units(i.receivedQty):postedForReceiptItem(db,i.id);
      const sourceQty=kind==='receipt'?units(i.planQty):units(i.receivedQty);
      return {sourceItemId:i.id,inboundOrderItemId:kind==='receipt'?i.id:i.inboundOrderItemId,
        skuId:i.skuId,sku:sku.sku,skuName:sku.skuName,sourceQty:decimal(sourceQty),
        priorQty:decimal(prior),maxQty:decimal(sourceQty-prior),
        qty:old?(kind==='receipt'?old.receivedQty:old.putawayQty):'',
        locationId:old?.locationId?String(old.locationId):''};
    }).filter(i=>units(i.maxQty)>0n||i.qty!=='');
    return {id:existing?.id||null,version:existing?.version||0,sourceId:source.id,
      documentNo:existing?(kind==='receipt'?existing.receiptOrderNo:existing.putawayOrderNo):'确认或暂存时生成',
      sourceLabel:kind==='receipt'?source.inboundOrderNo:source.receiptOrderNo,
      warehouseId:source.warehouseId,warehouseName:warehouse.warehouseName,
      status:existing?.status??0,remark:existing?.remark||'',items:lines};
  }
  save(kind,form,confirm,user={id:12,name:'演示操作员'}) {
    return this.transaction(db=>{
      const rows=kind==='receipt'?db.receipts:db.putaways;
      let row=form.id?find(rows,Number(form.id)):null;
      // 重复确认同一单据只返回原单，不重新增加数量/库存。
      if(row&&row.status===completed(kind)&&confirm) return row;
      if(row&&!isOpen(kind,row.status)) throw new Error('单据已完成或已取消，不能修改。');
      if(row&&row.version!==form.version) throw new Error('该草稿已被修改，请关闭弹窗后重新打开。');
      const sourceField=kind==='receipt'?'inboundOrderId':'receiptOrderId';
      const source=find(kind==='receipt'?db.inbounds:db.receipts,Number(form.sourceId),'来源单据');
      if(row&&row[sourceField]!==source.id) throw new Error('已保存单据不能变更来源。');
      if(rows.some(x=>x.id!==row?.id&&x[sourceField]===source.id&&isOpen(kind,x.status))) {
        throw new Error('该来源已有未完成单据，请使用“继续办理”。');
      }
      if(kind==='receipt'&&[0,5,6].includes(source.status)) throw new Error('来源入库单当前不允许收货。');
      if(kind==='putaway'&&source.status!==1) throw new Error('来源收货单尚未完成。');
      const parent=kind==='receipt'?source:find(db.inbounds,source.inboundOrderId);
      if(parent.status===6) throw new Error('来源入库单已取消。');
      const seen=new Set(),lines=[];
      for(const input of form.items) {
        if(seen.has(input.sourceItemId)) throw new Error('同一单据不允许重复 SKU 行。');
        seen.add(input.sourceItemId);
        const src=find(source.items,input.sourceItemId,'来源明细');
        const q=String(input.qty??'').trim()===''?0n:units(input.qty);
        if(q===0n) continue; // 空白/0 表示这次不处理，不存空明细。
        const available=kind==='receipt'?units(src.planQty)-units(src.receivedQty):
            units(src.receivedQty)-postedForReceiptItem(db,src.id);
        if(q>available) throw new Error(`${input.sku||'该 SKU'} 超过当前可办理数量 ${quantity(decimal(available))}。`);
        const inboundId=kind==='receipt'?src.id:src.inboundOrderItemId;
        const old=row?.items.find(i=>i.inboundOrderItemId===inboundId);
        const line={id:old?.id||++db.nextId,inboundOrderItemId:inboundId,skuId:src.skuId};
        if(kind==='receipt') line.receivedQty=decimal(q);
        else {
          const loc=find(db.locations,Number(input.locationId),'目标库位');
          if(loc.warehouseId!==source.warehouseId||loc.status!==1) throw new Error('目标库位必须是当前仓库的启用库位。');
          line.receiptOrderItemId=src.id;
          line.locationId=loc.id;
          line.putawayQty=decimal(q);
        }
        lines.push(line);
      }
      if(confirm&&lines.length===0) throw new Error('请至少填写一行大于 0 的数量。');
      if(String(form.remark||'').length>500) throw new Error('备注最多 500 字。');
      const time=now(),id=row?.id||++db.nextId;
      if(!row) {
        row={id,inboundOrderId:parent.id,warehouseId:source.warehouseId,createdTime:time,
          version:0,items:[]};
        const no=`${kind==='receipt'?'RC':'PA'}${time.slice(0,10).replaceAll('-','')}${String(id).padStart(6,'0')}`;
        if(kind==='receipt') row.receiptOrderNo=no;
        else {row.putawayOrderNo=no;row.receiptOrderId=source.id;}
        rows.push(row);
      }
      row.items=lines.map(i=>({...i,[kind==='receipt'?'receiptOrderId':'putawayOrderId']:id}));
      row.remark=String(form.remark||'').trim();row.updatedTime=time;row.version++;
      row.status=confirm?completed(kind):(kind==='receipt'?0:lines.length?1:0);
      if(kind==='receipt') {
        row.receiverId=user.id;row.receiverName=user.name;row.receivedTime=confirm?time:null;
      } else {
        row.operatorId=user.id;row.operatorName=user.name;row.putawayTime=confirm?time:null;
        if(confirm) postInventory(db,row);
      }
      return row;
    });
  }
  cancel(kind,id) {
    return this.transaction(db=>{
      const row=find(kind==='receipt'?db.receipts:db.putaways,Number(id));
      if(!isOpen(kind,row.status)) throw new Error('只能取消尚未完成的单据；已入账单据不能直接取消。');
      row.status=kind==='receipt'?2:3;row.updatedTime=now();row.version++;
      return row;
    });
  }
}


/** 页面交互层。接后端时替换 DemoService，而不是在模板中拼装累计数量。 */
function useOrderPage(kind) {
  const receiving=kind==='receipt',word=receiving?'收货':'上架';
  const route=useRoute(),router=useRouter(),userStore=useUserStore();
  const rows=ref([]),db=ref({warehouses:[],locations:[]}),keyword=ref(''),warehouse=ref(''),
      range=ref('all'),status=ref(''),expanded=ref(null),form=ref(null),editor=ref(null),
      sourceOptions=ref([]),message=ref(''),messageError=ref(false),formError=ref(''),busy=ref(false);
  let service,original='',timer;
  const stateMap=STATUS[kind];
  const done=receiving?1:2;
  const canEdit=r=>receiving?r.status===0:[0,1].includes(r.status);
  const badgeClass=s=>s===done?'done':s===(receiving?2:3)?'cancelled':!receiving&&s===1?'working':'pending';
  const docNo=r=>receiving?r.receiptOrderNo:r.putawayOrderNo;
  const qty=i=>receiving?i.receivedQty:i.putawayQty;
  function notify(text,error=false){
    message.value=text;messageError.value=error;clearTimeout(timer);
    timer=setTimeout(()=>message.value='',5500);
  }
  function safely(fn){try{return fn();}catch(e){notify(e.message||'操作失败',true);return null;}}
  function refresh(){db.value=service.read();rows.value=service.list(kind);sourceOptions.value=service.sources(kind);}
  const filtered=computed(()=>{
    const kw=keyword.value.trim().toLowerCase(),end=new Date(),start=new Date();
    if(range.value==='today')start.setHours(0,0,0,0);
    else start.setDate(start.getDate()-Number(range.value));
    return rows.value.filter(r=>{
      const date=new Date(r.createdTime.replace(' ','T'));
      return (!kw||[docNo(r),r.inboundOrderNo,r.receiptOrderNo].some(x=>String(x||'').toLowerCase().includes(kw)))&&
          (!warehouse.value||r.warehouseId===Number(warehouse.value))&&
          (status.value===''||r.status===Number(status.value))&&
          (range.value==='all'||date>=start&&date<=end);
    });
  });
  const stats=computed(()=>receiving?[
    {title:'未完成草稿',value:rows.value.filter(r=>r.status===0).length,meta:'尚未增加累计收货'},
    {title:'可创建上架单',value:rows.value.filter(r=>r.status===1&&units(r.remainingQty)>0n).length,meta:'已收货且仍有剩余'},
    {title:'已完成收货单',value:rows.value.filter(r=>r.status===1).length,meta:'按本次收货批次计数'}
  ]:[
    {title:'待上架',value:rows.value.filter(r=>r.status===0).length,meta:'尚未填写上架明细'},
    {title:'上架中',value:rows.value.filter(r=>r.status===1).length,meta:'已暂存，尚未计入库存'},
    {title:'已完成上架单',value:rows.value.filter(r=>r.status===2).length,meta:'本批已确认入账'}
  ]);
  const totalInput=computed(()=>{
    try{return quantity(decimal((form.value?.items||[]).reduce((n,i)=>n+
        (String(i.qty).trim()?units(i.qty):0n),0n)));}
    catch{return '请检查数量';}
  });
  const locations=computed(()=>db.value.locations.filter(l=>l.status===1&&l.warehouseId===form.value?.warehouseId));
  function resetFilters(){keyword.value='';warehouse.value='';range.value='all';status.value='';}
  async function openEditor(id=null,sourceId=null){
    try{
      refresh();
      if(!id&&!sourceId&&!sourceOptions.value.length)throw new Error(`没有可${word}的来源单据。`);
      form.value=service.form(kind,sourceId||sourceOptions.value[0]?.id,id);
      form.value.lockSource=Boolean(id);
      if(!sourceOptions.value.some(s=>s.id===form.value.sourceId)){
        sourceOptions.value.unshift({id:form.value.sourceId,label:form.value.sourceLabel});
      }
      original=JSON.stringify(form.value);formError.value='';
      await nextTick();if(editor.value&&!editor.value.open)editor.value.showModal();
    }catch(e){notify(e.message,true);}
  }
  const isDirty=()=>form.value&&JSON.stringify(form.value)!==original;
  function closeEditor(){
    if(isDirty()&&!window.confirm('尚有未保存的修改，确定放弃吗？'))return;
    editor.value?.close();form.value=null;
  }
  async function changeSource(event){
    const next=event.target.value;
    if(isDirty()&&!window.confirm('切换来源会放弃未保存的输入，继续吗？')){
      event.target.value=String(form.value.sourceId);return;
    }
    await openEditor(null,Number(next));
  }
  function fillRemaining(){form.value.items.forEach(i=>i.qty=i.maxQty);}
  function saveEditor(confirm){
    if(busy.value)return;
    busy.value=true;formError.value='';
    try{
      const row=service.save(kind,form.value,confirm,{
        id:Number(userStore.userId)||12,name:userStore.realName||'演示操作员'
      });
      editor.value?.close();form.value=null;expanded.value=row.id;refresh();
      notify(confirm?`${word}单已确认；${receiving?'累计收货已更新':'本批库存已增加'}。`:
          `${receiving?'草稿已保存':'进度已暂存'}，未入账。`);
    }catch(e){formError.value=e.message;}
    finally{busy.value=false;}
  }
  function cancelOrder(row){
    if(!window.confirm('取消这张尚未入账的单据？明细将保留为只读记录。'))return;
    safely(()=>{service.cancel(kind,row.id);refresh();notify('单据已取消，累计数量和库存不变。');});
  }
  function resetDemo(){
    if(window.confirm('仅重置本演示的浏览器数据，不连接数据库。是否继续？')){
      safely(()=>{service.reset();refresh();notify('演示数据已重置。');});
    }
  }
  function goPutaway(row){
    return router.push({path:'/inbound/putaway',query:{receiptOrderId:String(row.id),action:'create'}});
  }
  function openFromQuery(){
    const q=route.query;
    if(q.action!=='create'&&q.action!=='continue')return;
    // 入库单跳转：收货直接使用 inboundOrderId；上架从该入库单的可用收货批次中选第一批。
    let sourceId=Number(receiving?q.inboundOrderId:q.receiptOrderId);
    if(!receiving&&!sourceId&&q.inboundOrderId){
      const r=service.read().receipts.find(x=>x.inboundOrderId===Number(q.inboundOrderId)&&
          service.sources('putaway').some(s=>s.id===x.id));
      sourceId=r?.id;
    }
    if(sourceId)openEditor(null,sourceId);
    else if(q.inboundOrderId)notify('该入库单当前没有可办理的来源批次。',true);
  }
  const onStorage=event=>{if(event.key===STORAGE_KEY){safely(refresh);if(form.value)notify('其他标签页的数据已变化，提交时会重新校验。');}};
  onMounted(()=>{
    safely(()=>{service=new DemoService(window.localStorage);refresh();openFromQuery();});
    window.addEventListener('storage',onStorage);
  });
  watch(()=>route.fullPath,()=>{if(service)safely(openFromQuery);});
  onBeforeRouteLeave(()=>!isDirty()||window.confirm('存在未保存的修改，确定离开吗？'));
  onBeforeUnmount(()=>{clearTimeout(timer);window.removeEventListener('storage',onStorage);});
  return {receiving,word,rows,db,keyword,warehouse,range,status,expanded,form,editor,sourceOptions,
    message,messageError,formError,busy,stateMap,done,canEdit,badgeClass,docNo,qty,quantity,units,
    filtered,stats,totalInput,locations,resetFilters,openEditor,closeEditor,changeSource,
    fillRemaining,saveEditor,cancelOrder,resetDemo,goPutaway};
}

const {
  receiving, word, db, keyword, warehouse, range, status, expanded, form, editor, sourceOptions,
  message, messageError, formError, busy, stateMap, done, canEdit, badgeClass, docNo, qty, quantity: displayQuantity, units: qtyUnits,
  filtered, stats, totalInput, locations, resetFilters, openEditor, closeEditor, changeSource,
  fillRemaining, saveEditor, cancelOrder, resetDemo, goPutaway
} = useOrderPage('receipt')
</script>

<template>
  <main class="wms-page">
    <section class="page-head">
      <div>
        <div class="kicker">{{ receiving ? 'RECEIVING' : 'PUTAWAY' }}</div>
        <h2>{{ word }}单管理</h2>
        <p>{{ receiving ? '按到货批次记录实际收货；暂存与确认分开，明细直接在列表中展开。' : '将已收货商品放入目标库位；按本次批次确认上架并增加库存。' }}</p>
      </div>
      <button class="btn primary" type="button" @click="openEditor()">＋ 新建{{ word }}单</button>
    </section>


    <section class="panel">
      <div class="filters">
        <div class="field">
          <label for="order-keyword">单据搜索</label>
          <input id="order-keyword" v-model.trim="keyword"
                 :placeholder="receiving ? '收货单号 / 入库单号' : '上架单号 / 收货单号 / 入库单号'">
        </div>
        <div class="field">
          <label for="order-warehouse">仓库</label>
          <select id="order-warehouse" v-model="warehouse">
            <option value="">全部仓库</option>
            <option v-for="w in db.warehouses" :key="w.id" :value="String(w.id)">{{ w.warehouseName }}</option>
          </select>
        </div>
        <div class="field">
          <label for="order-range">创建时间</label>
          <select id="order-range" v-model="range">
            <option value="all">全部时间</option><option value="today">今天</option>
            <option value="7">近 7 天</option><option value="30">近 30 天</option>
          </select>
        </div>
        <button class="btn" type="button" @click="resetFilters">重置</button>
      </div>

      <div class="toolbar">
        <div class="tabs">
          <button class="tab" :class="{active:status===''}" @click="status=''">全部</button>
          <button v-for="(label,value) in stateMap" :key="value" class="tab"
                  :class="{active:status===String(value)}" @click="status=String(value)">{{ label }}</button>
        </div>
        <div class="count">共 {{ filtered.length }} 条</div>
      </div>

      <div class="table-wrap">
        <table class="main-table" :class="{putaway:!receiving}">
          <thead><tr>
            <th>{{ word }}单 / 创建时间</th><th>{{ receiving ? '来源入库单' : '来源收货单 / 入库单' }}</th>
            <th>仓库</th><th>状态</th><th>SKU 种类</th><th>本单{{ word }}量</th>
            <th v-if="!receiving">目标库位</th><th>{{ word }}人 / 完成时间</th><th>操作</th>
          </tr></thead>
          <tbody>
          <template v-for="row in filtered" :key="row.id">
            <tr class="main-row" :class="{'is-expanded':expanded===row.id}">
              <td>
                <button class="doc-btn" type="button" :aria-expanded="expanded===row.id"
                        @click="expanded=expanded===row.id?null:row.id">
                  <span class="arrow">{{ expanded===row.id?'▾':'▸' }}</span>{{ docNo(row) }}
                </button>
                <div class="sub">{{ row.createdTime }}</div>
              </td>
              <td><b>{{ receiving?row.inboundOrderNo:row.receiptOrderNo }}</b>
                <div v-if="!receiving" class="sub">{{ row.inboundOrderNo }}</div></td>
              <td class="nowrap">{{ row.warehouseName }}</td>
              <td><span class="badge" :class="badgeClass(row.status)">{{ stateMap[row.status] }}</span></td>
              <td>{{ row.skuCount }}</td>
              <td class="mono"><b>{{ displayQuantity(row.totalQty) }}</b>
                <div v-if="row.status!==done" class="sub">录入量 · 未入账</div></td>
              <td v-if="!receiving">
                <b>{{ row.locations.slice(0,2).join(' / ') || '待分配' }}</b>
                <div v-if="row.locations.length>2" class="sub">共 {{ row.locations.length }} 个库位</div>
              </td>
              <td>{{ receiving?row.receiverName:row.operatorName }}
                <div class="sub nowrap">{{ (receiving?row.receivedTime:row.putawayTime)||'尚未完成' }}</div></td>
              <td><div class="actions">
                <template v-if="canEdit(row)">
                  <button class="link" @click="openEditor(row.id)">{{ receiving?'继续收货':row.status===0?'开始上架':'继续上架' }}</button>
                  <button class="link danger" @click="cancelOrder(row)">取消</button>
                </template>
                <button v-else-if="receiving&&row.status===1&&qtyUnits(row.remainingQty)>0" class="link"
                        @click="goPutaway(row)">办理上架</button>
                <span v-else class="sub">只读</span>
              </div></td>
            </tr>
            <tr v-if="expanded===row.id">
              <td :colspan="receiving?8:9" class="detail-cell">
                <div class="detail-head"><b>本次{{ word }}明细</b>
                  <span>{{ receiving&&row.status===1 ? '当前尚可上架 '+displayQuantity(row.remainingQty)
                      :row.status===done?'已确认入账；不可再次编辑':row.status===(receiving?2:3)?'已取消；仅可查看原记录':'未入账，数量可以继续修改' }}</span></div>
                <div class="table-wrap"><table class="detail-table">
                  <thead><tr><th>SKU 编码</th><th>商品名称</th><th v-if="!receiving">目标库位</th><th>本单{{ word }}量</th></tr></thead>
                  <tbody>
                  <tr v-for="item in row.items" :key="item.id">
                    <td><b>{{ item.sku }}</b></td><td>{{ item.skuName }}</td>
                    <td v-if="!receiving">{{ item.locationCode }}</td><td class="mono">{{ displayQuantity(qty(item)) }}</td>
                  </tr>
                  <tr v-if="!row.items.length"><td class="empty" :colspan="receiving?3:4">尚未保存明细。</td></tr>
                  </tbody>
                </table></div>
                <div class="remark">备注：{{ row.remark||'—' }}</div>
              </td>
            </tr>
          </template>
          <tr v-if="!filtered.length"><td class="empty" :colspan="receiving?8:9">没有符合条件的{{ word }}单。</td></tr>
          </tbody>
        </table>
      </div>
      <div class="note">点击单号展开本次明细。统计卡片按全部单据计数；列表按筛选条件显示。
        {{ receiving?'草稿数量不计入入库单累计已收货。':'“上架中”仅表示正在办理且已暂存，不表示部分库存已入账。' }}</div>
    </section>


    <!-- 这是办理弹窗，不是重复的只读详情页。 -->
    <dialog ref="editor" aria-labelledby="operation-title" @cancel.prevent="closeEditor">
      <template v-if="form">
        <div class="dialog-head">
          <div><div class="kicker">BATCH OPERATION</div><h3 id="operation-title">{{ form.id?'继续':'新建' }}{{ word }}单</h3>
            <p>{{ form.documentNo }} · 一张单只记录本次批次</p></div>
          <button type="button" class="close" aria-label="关闭办理窗口" @click="closeEditor">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-grid">
            <div class="field"><label for="operation-source">{{ receiving?'来源入库单':'来源收货单（仅已完成）' }}</label>
              <select id="operation-source" :value="form.sourceId" :disabled="form.lockSource" @change="changeSource">
                <option v-for="s in sourceOptions" :key="s.id" :value="s.id">{{ s.label }}</option>
              </select></div>
            <div class="field"><label>仓库（由来源决定）</label><div class="readonly">{{ form.warehouseName }}</div></div>
          </div>
          <div class="callout">
            {{ receiving?'填写本次实际收货数量。保存草稿不增加已收货数量；确认后本单完成，剩余到货另建收货单。'
              :'每个 SKU 本单选择一个目标库位。暂存不增加库存；确认后本批整体入账，剩余数量另建上架单。' }}
            空白或 0 表示本次不处理该行。
          </div>
          <div class="table-wrap"><table class="edit-table">
            <thead><tr><th>SKU / 商品名称</th><th>{{ receiving?'计划量':'本批收货量' }}</th>
              <th>{{ receiving?'累计已收':'本批累计已上架' }}</th><th>当前可{{ word }}</th><th>本次{{ word }}</th>
              <th v-if="!receiving">目标库位</th></tr></thead>
            <tbody>
            <tr v-for="item in form.items" :key="item.sourceItemId">
              <td><b>{{ item.sku }}</b><div class="sub">{{ item.skuName }}</div></td>
              <td class="mono">{{ displayQuantity(item.sourceQty) }}</td><td class="mono">{{ displayQuantity(item.priorQty) }}</td>
              <td class="mono">{{ displayQuantity(item.maxQty) }}</td>
              <td><input v-model="item.qty" :aria-label="item.sku+' 本次数量'" type="text" inputmode="decimal"
                         autocomplete="off" placeholder="0.000"></td>
              <td v-if="!receiving"><select v-model="item.locationId" :aria-label="item.sku+' 目标库位'">
                <option value="">请选择库位</option>
                <option v-for="l in locations" :key="l.id" :value="String(l.id)">{{ l.locationCode }}</option>
              </select></td>
            </tr>
            <tr v-if="!form.items.length"><td class="empty" :colspan="receiving?5:6">来源已无剩余数量，请重新选择。</td></tr>
            </tbody>
          </table></div>
          <div class="fill-row"><span>数量精度为 0.001；提交时重新校验可用数量。</span>
            <button class="link" type="button" @click="fillRemaining">填入全部剩余</button></div>
          <div class="field"><label for="operation-remark">备注（选填，最多 500 字）</label>
            <textarea id="operation-remark" v-model="form.remark" rows="2" maxlength="500" placeholder="补充本次作业说明"></textarea></div>
          <div v-if="formError" class="error-line" role="alert">{{ formError }}</div>
        </div>
        <div class="dialog-footer"><div class="total">本次合计 <strong>{{ totalInput }}</strong></div>
          <div class="buttons">
            <button class="btn" type="button" :disabled="busy" @click="closeEditor">返回列表</button>
            <button class="btn" type="button" :disabled="busy" @click="saveEditor(false)">{{ receiving?'保存草稿':'暂存进度' }}</button>
            <button class="btn primary" type="button" :disabled="busy" @click="saveEditor(true)">确认{{ word }}</button>
          </div>
        </div>
      </template>
    </dialog>
  </main>
</template>

<style scoped>

.wms-page{--canvas:#fffaf0;--surface-soft:#faf5e8;--surface-card:#f5f0e0;--ink:#0a0a0a;--muted:#6a6a6a;--hairline:#e5e1d8;--pink:#ff4d8b;--lavender:#b8a4ed;--peach:#ffb084;--ochre:#e8b94a;color:var(--ink);font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif;width:100%;box-sizing:border-box}
.wms-page *{box-sizing:border-box}.wms-page button,.wms-page input,.wms-page select,.wms-page textarea{font:inherit}
.wms-page button{cursor:pointer}.wms-page button:disabled{cursor:not-allowed;opacity:.5}.wms-page button:focus-visible,.wms-page a:focus-visible{outline:3px solid #b8a4ed;outline-offset:3px}
.wms-page .page-head{display:flex;justify-content:space-between;align-items:flex-end;gap:24px;margin-bottom:24px}
.wms-page .kicker{font-size:11px;font-weight:750;letter-spacing:1.8px;color:var(--muted);margin-bottom:8px}
.wms-page h2{font-size:40px;line-height:1.15;letter-spacing:-1.2px;font-weight:550;margin:0}
.wms-page .page-head p{font-size:14px;line-height:1.8;color:var(--muted);margin:10px 0 0}
.wms-page .btn{min-height:42px;border:1px solid var(--hairline);border-radius:12px;background:var(--canvas);padding:0 17px;color:var(--ink);font-size:13px;font-weight:650;white-space:nowrap}
.wms-page .btn.primary{background:var(--ink);border-color:var(--ink);color:#fff}.wms-page .btn:hover{filter:brightness(.96)}
.wms-page .summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin:0 0 22px}
.wms-page .summary-card{position:relative;overflow:hidden;padding:19px 22px;border-radius:20px;min-height:115px}
.wms-page .summary-card:nth-child(1){background:var(--lavender)}.wms-page .summary-card:nth-child(2){background:var(--peach)}.wms-page .summary-card:nth-child(3){background:var(--ochre)}
.wms-page .summary-card small{font-size:12px}.wms-page .summary-card strong{font-size:32px;display:block;font-weight:600;margin:10px 0 2px}
.wms-page .summary-card::after{content:"";width:66px;height:66px;border-radius:22px;position:absolute;right:-9px;bottom:-15px;background:rgba(255,255,255,.25);transform:rotate(20deg)}
.wms-page .summary-note{font-size:11px;opacity:.74}.wms-page .panel{border:1px solid var(--hairline);border-radius:16px;overflow:hidden;background:var(--canvas);padding:0}
.wms-page .filters{display:grid;grid-template-columns:1.8fr 1fr 1fr auto;gap:12px;padding:18px;align-items:end}
.wms-page .field{min-width:0}.wms-page .field label{font-size:12px;color:var(--muted);display:block;margin-bottom:7px;font-weight:600}
.wms-page input,.wms-page select,.wms-page textarea{width:100%;padding:11px 12px;border:1px solid var(--hairline);border-radius:10px;background:var(--canvas);color:var(--ink);outline:0;font-size:13px}
.wms-page input:focus,.wms-page select:focus,.wms-page textarea:focus{border-color:var(--ink);box-shadow:0 0 0 2px #0a0a0a08}
.wms-page .toolbar{display:flex;justify-content:space-between;align-items:center;gap:15px;padding:13px 18px;border-block:1px solid var(--hairline)}
.wms-page .tabs{display:flex;flex-wrap:wrap;gap:5px}.wms-page .tab{border:0;border-radius:999px;background:transparent;padding:8px 13px;color:var(--muted);font-size:13px}
.wms-page .tab.active{background:var(--surface-card);color:var(--ink);font-weight:650}.wms-page .count{font-size:12px;color:var(--muted);white-space:nowrap}
.wms-page .table-wrap{overflow:auto}.wms-page table{border-collapse:collapse;width:100%;font-size:12px}
.wms-page .main-table{min-width:1040px}.wms-page .main-table.putaway{min-width:1120px}
.wms-page th,.wms-page td{text-align:left;vertical-align:middle;padding:17px 13px;border-bottom:1px solid #ebe6dc}
.wms-page th{font-size:11px;color:var(--muted);font-weight:650;white-space:nowrap}
.wms-page td b{font-weight:650}.wms-page .sub{font-size:11px;color:var(--muted);margin-top:5px;line-height:1.6}
.wms-page .mono{font-variant-numeric:tabular-nums}.wms-page .nowrap{white-space:nowrap}
.wms-page .main-row:hover{background:#fffdf7}.wms-page .main-row.is-expanded{background:var(--surface-soft)}
.wms-page .doc-btn{border:0;background:transparent;padding:0;font-size:12px;font-weight:750;color:var(--ink);white-space:nowrap}
.wms-page .doc-btn .arrow{display:inline-block;width:15px;color:#8c8170}.wms-page .badge{display:inline-flex;border-radius:999px;padding:5px 10px;font-size:11px;font-weight:650;white-space:nowrap}
.wms-page .badge.pending{background:#efe9ff;color:#58416f}.wms-page .badge.working{background:#ffe2bc;color:#865012}
.wms-page .badge.done{background:#dff5e4;color:#166534}.wms-page .badge.cancelled{background:#eeece6;color:#777}
.wms-page .actions{display:flex;gap:9px;align-items:center;white-space:nowrap}.wms-page .link{border:0;background:transparent;padding:5px 0;color:#a32356;font-weight:650;font-size:12px}
.wms-page .link.danger{color:#906a5a}.wms-page .empty{text-align:center;padding:38px;color:var(--muted)}
.wms-page .note{padding:15px 18px;color:var(--muted);font-size:12px;line-height:1.8}
.wms-page .detail-cell{padding:18px 28px;background:var(--surface-soft)}
.wms-page .detail-head{display:flex;justify-content:space-between;gap:16px;margin-bottom:10px;font-size:12px}
.wms-page .detail-table{background:var(--canvas);border:1px solid var(--hairline);min-width:580px}
.wms-page .detail-table th,.wms-page .detail-table td{padding:11px 14px}.wms-page .remark{font-size:12px;line-height:1.8;margin-top:12px;white-space:pre-wrap;overflow-wrap:anywhere;color:var(--muted)}
.wms-page .demo-bar{display:flex;justify-content:space-between;gap:14px;align-items:center;margin:18px 0 0;padding:13px 16px;border:1px dashed #d2c8b6;border-radius:12px;font-size:12px;color:var(--muted);line-height:1.7}
.wms-page .demo-bar .link{flex-shrink:0;color:var(--muted)}.wms-page .toast{position:fixed;bottom:24px;left:50%;transform:translateX(-50%);max-width:calc(100vw - 32px);z-index:100;background:#163b31;color:#fff;border-radius:12px;padding:13px 20px;font-size:13px;box-shadow:0 8px 28px #0002}
.wms-page .toast.error{background:#8b3544}
.wms-page dialog{width:min(1020px,calc(100vw - 32px));max-height:calc(100dvh - 40px);padding:0;border:1px solid var(--hairline);border-radius:20px;background:var(--canvas);color:var(--ink);box-shadow:0 20px 100px #0003}
.wms-page dialog::backdrop{background:rgba(20,24,20,.32);backdrop-filter:blur(3px)}
.wms-page .dialog-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:24px 26px;border-bottom:1px solid var(--hairline)}
.wms-page h3{margin:0;font-size:24px;font-weight:600}.wms-page .dialog-head p{font-size:12px;color:var(--muted);margin:8px 0 0;line-height:1.7}
.wms-page .close{width:34px;height:34px;border:1px solid var(--hairline);background:var(--canvas);border-radius:10px}
.wms-page .dialog-body{padding:20px 26px}.wms-page .form-grid{display:grid;grid-template-columns:2fr 1fr;gap:16px;margin-bottom:16px}
.wms-page .readonly{font-size:13px;line-height:20px;padding:11px 12px;background:var(--surface-card);border-radius:10px;min-height:43px}
.wms-page .callout{background:#f1eadb;border-radius:10px;color:#68553a;padding:12px 14px;margin-bottom:16px;font-size:12px;line-height:1.8}
.wms-page .edit-table{min-width:670px}.wms-page .edit-table th,.wms-page .edit-table td{padding:11px 9px}
.wms-page .edit-table input{min-width:112px;width:124px}.wms-page .edit-table select{min-width:140px}
.wms-page .fill-row{display:flex;justify-content:space-between;align-items:center;margin:12px 0 16px;font-size:12px;color:var(--muted)}
.wms-page .dialog-footer{position:sticky;bottom:0;display:flex;justify-content:space-between;align-items:center;gap:14px;padding:17px 26px;border-top:1px solid var(--hairline);background:var(--canvas)}
.wms-page .dialog-footer .buttons{display:flex;gap:8px}.wms-page .total{font-size:13px}.wms-page .total strong{font-size:20px;margin:0 4px;font-variant-numeric:tabular-nums}
.wms-page .error-line{color:#a32243;font-size:12px;line-height:1.8;margin-top:12px}
@media(max-width:850px){.wms-page h2{font-size:32px}.wms-page .filters{grid-template-columns:1fr 1fr}.wms-page .page-head{align-items:flex-start}.wms-page .form-grid{grid-template-columns:1fr}.wms-page .dialog-body,.wms-page .dialog-head{padding:18px}.wms-page .dialog-footer{padding:15px 18px;align-items:flex-start;flex-direction:column}}
@media(max-width:520px){.wms-page .page-head{flex-direction:column;gap:16px}.wms-page .summary{gap:8px}.wms-page .summary-card{padding:15px 12px}.wms-page .summary-card strong{font-size:27px}.wms-page .summary-note{display:none}.wms-page .filters{grid-template-columns:1fr}.wms-page .toolbar{align-items:flex-start}.wms-page .demo-bar{align-items:flex-start;flex-direction:column}.wms-page .dialog-footer .buttons{flex-wrap:wrap}.wms-page .detail-cell{padding:14px}}

</style>