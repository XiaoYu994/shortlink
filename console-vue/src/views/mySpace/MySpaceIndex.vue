<template>
  <div style="display: flex; height: 100%">
    <div class="options-box">
      <div class="option-title flex-box">
        <div>
          短链分组<span> 共{{ editableTabs?.length }}组</span>
        </div>
        <div class="hover-box" style="width: 24px" @click="showAddGroup">
          <img src="@/assets/svg/添加.svg" alt="" />
        </div>
      </div>
      <ul class="sortOptions">
        <li v-for="(item, index) in editableTabs" :key="item.gid">
          <div class="item-box flex-box hover-box" :class="{ selectedItem: selectedIndex === index }"
               @click="changeSelectIndex(index)">
            <div style="display: flex">
              <img src="@/assets/svg/移动竖.svg" width="13" style="margin-right: 3px" alt="" />
              <span class="over-text">{{ item.name }}</span>
            </div>
            <div class="flex-box">
              <el-tooltip show-after="500" class="box-item" effect="dark" :content="'查看图表'" placement="bottom-end">
                <el-icon v-if="!(item.shortLinkCount === 0 || item.shortLinkCount === null)" class="edit"
                         :class="{ zero: item.shortLinkCount === 0 }"
                         @click.stop="chartsVisible({ description: item.name, gid: item.gid, group: true })">
                  <Histogram />
                </el-icon>
              </el-tooltip>
              <el-dropdown>
                <div class="block" @click.stop>
                  <el-icon class="edit" v-if="item.title !== '默认分组'">
                    <Tools />
                  </el-icon>
                </div>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="showEditGroup(item.gid, item.name)">编辑</el-dropdown-item>
                    <el-dropdown-item @click="deleteGroup(item.gid)">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <span class="item-length">{{ item.shortLinkCount ?? 0 }}</span>
            </div>
          </div>
        </li>
      </ul>
      <div class="recycle-bin">
        <div class="recycle-box hover-box" :class="{ selectedItem: selectedIndex === -1 }" @click="recycleBin">
          回收站
          <el-icon style="margin-left: 5px; font-size: 20px">
            <Delete />
          </el-icon>
        </div>
      </div>
    </div>
    <div class="content-box">
      <div class="table-box">
        <div v-if="!isRecycleBin" class="buttons-box">
          <div style="width: 100%; display: flex">
            <el-button class="addButton" type="primary" style="width: 130px; margin-right: 10px"
                       @click="isAddSmallLink = true">创建短链</el-button>
            <el-button style="width: 130px; margin-right: 10px" @click="isAddSmallLinks = true">批量创建</el-button>
          </div>
        </div>
        <div v-else class="recycle-bin-box">
          <span>回收站</span>
          <span>共{{ recycleBinNums }}条短链接</span>
        </div>
        <el-table :data="tableData" height="calc(100vh - 240px)" style="width: calc(100vw - 230px)"
                  :header-cell-style="{ background: '#f7f8fa', color: '#606266' }">
          <template #empty>
            <div style="height: 60vh; display: flex; align-items: center; justify-content: center">
              暂无链接
            </div>
          </template>
          <el-table-column type="selection" width="35" />
          <el-table-column label="短链接信息" prop="info" min-width="300">
            <template #header>
              <el-dropdown>
                <div :class="{ orderIndex: orderIndex === 0 }" class="block" style="margin-top: 3px">
                  <span>短链接信息</span>
                  <el-icon>
                    <CaretBottom />
                  </el-icon>
                </div>
                <template #dropdown>
                  <el-dropdown-item @click="pageParams.orderTag = null, orderIndex = 0">创建时间</el-dropdown-item>
                </template>
              </el-dropdown>
            </template>
            <template #default="scope">
              <div class="table-link-box" :class="{
                isExpire: scope?.row?.validDateType === 1 && !isExpire(scope?.row?.validDate)
              }">
                <img :src="getImgUrl(scope.row.favicon)" :key="scope?.row?.id" width="20" height="20" alt="" />
                <div class="name-date">
                  <el-tooltip show-after="500" :content="scope.row.description">
                    <span>{{ scope.row.description }}</span>
                  </el-tooltip>
                  <div class="time" style="display: flex">
                    <span>{{ scope.row.createTime }}</span>
                    <el-tooltip show-after="500" v-if="scope?.row?.validDate" :content="'到期时间：' + scope?.row?.validDate">
                      <img v-if="isExpire(scope?.row?.validDate)" width="18" height="18" src="@/assets/png/沙漏倒计时.png"
                           alt="" />
                      <div v-else><span>已失效</span></div>
                    </el-tooltip>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="短链接网址" prop="url" min-width="300">
            <template #default="scope">
              <div class="table-url-box">
                <el-link type="primary" :underline="false" target="_blank"
                         :disabled="scope?.row?.validDateType === 1 && !isExpire(scope?.row?.validDate) || scope?.row?.enableStatus === 2"
                         :href="'http://' + scope.row.fullShortUrl">{{ scope.row.domain + '/' + scope.row.shortUri }}</el-link>
                <el-tooltip show-after="500" :content="scope.row.originUrl">
                  <span>{{ scope.row.originUrl }}</span>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="copy" width="170">
            <template #default="scope">
              <div style="display: flex; align-items: center">
                <QRCode :url="'http://' + scope.row.fullShortUrl"></QRCode>
                <el-tooltip show-after="500" class="box-item" effect="dark" content="复制链接" placement="bottom-end">
                  <el-icon @click="copyUrl('http://' + scope.row.fullShortUrl)" class="table-edit copy-url">
                    <Share />
                  </el-icon>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="访问次数" prop="times" width="120">
            <template #header>
              <el-dropdown>
                <div :class="{ orderIndex: orderIndex === 1 }" class="block" style="margin-top: 3px">
                  <span>访问次数</span>
                  <el-icon>
                    <CaretBottom />
                  </el-icon>
                </div>
                <template #dropdown>
                  <el-dropdown-item v-if="!isRecycleBin" @click="pageParams.orderTag = 'todayPv', orderIndex = 1">今日访问次数</el-dropdown-item>
                  <el-dropdown-item @click="pageParams.orderTag = 'totalPv', orderIndex = 1">累计访问次数</el-dropdown-item>
                </template>
              </el-dropdown>
            </template>
            <template #default="scope">
              <div class="times-box">
                <div class="today-box">
                  <span>今日</span>
                  <span>{{ scope.row.todayPv ?? 0 }}</span>
                </div>
                <div class="total-box">
                  <span>累计</span>
                  <span>{{ scope.row.totalPv ?? 0 }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="访问人数" prop="people" width="120">
            <template #header>
              <el-dropdown>
                <div :class="{ orderIndex: orderIndex === 2 }" class="block" style="margin-top: 3px">
                  <span>访问人数</span>
                  <el-icon>
                    <CaretBottom />
                  </el-icon>
                </div>
                <template #dropdown>
                  <el-dropdown-item v-if="!isRecycleBin" @click="pageParams.orderTag = 'todayUv', orderIndex = 2">今日访问人数</el-dropdown-item>
                  <el-dropdown-item @click="pageParams.orderTag = 'totalUv', orderIndex = 2">累计访问人数</el-dropdown-item>
                </template>
              </el-dropdown>
            </template>
            <template #default="scope">
              <div class="times-box">
                <div class="today-box">
                  <span>今日</span>
                  <span>{{ scope.row.todayUv ?? 0 }}</span>
                </div>
                <div class="total-box">
                  <span>累计</span>
                  <span>{{ scope.row.totalUv ?? 0 }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="IP数" prop="IP" width="120">
            <template #header>
              <el-dropdown>
                <div :class="{ orderIndex: orderIndex === 3 }" class="block" style="margin-top: 3px">
                  <span>IP数</span>
                  <el-icon>
                    <CaretBottom />
                  </el-icon>
                </div>
                <template #dropdown>
                  <el-dropdown-item v-if="!isRecycleBin" @click="pageParams.orderTag = 'todayUip', orderIndex = 3">今日IP数</el-dropdown-item>
                  <el-dropdown-item @click="pageParams.orderTag = 'totalUip', orderIndex = 3">累计IP数</el-dropdown-item>
                </template>
              </el-dropdown>
            </template>
            <template #default="scope">
              <div class="times-box">
                <div class="today-box">
                  <span>今日</span>
                  <span>{{ scope.row.todayUip ?? 0 }}</span>
                </div>
                <div class="total-box">
                  <span>累计</span>
                  <span>{{ scope.row.totalUip ?? 0 }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column fixed="right" label="操作" width="180">
            <template #default="scope">
              <div style="display: flex; align-items: center">
                <el-tooltip show-after="500" class="box-item" effect="dark" content="查看图表" placement="bottom-end">
                  <el-icon class="table-edit" @click="chartsVisible(scope.row)">
                    <Histogram />
                  </el-icon>
                </el-tooltip>
                <template v-if="selectedIndex !== -1">
                  <el-tooltip show-after="500" class="box-item" effect="dark" content="编辑" placement="bottom-end">
                    <el-icon @click="editLink(scope.row)" class="table-edit">
                      <Tools />
                    </el-icon>
                  </el-tooltip>
                  <el-tooltip show-after="500" class="box-item" effect="dark" content="删除" placement="bottom-end">
                    <el-popconfirm width="100" title="是否移入回收站" @confirm="toRecycleBin(scope.row)">
                      <template #reference>
                        <el-icon class="table-edit">
                          <Delete />
                        </el-icon>
                      </template>
                    </el-popconfirm>
                  </el-tooltip>
                </template>
                <template v-else>
                  <el-tooltip show-after="500" class="box-item" effect="dark" content="恢复" placement="bottom-end">
                    <el-icon @click="recoverLink(scope.row)" class="table-edit">
                      <HelpFilled />
                    </el-icon>
                  </el-tooltip>
                  <el-tooltip show-after="500" class="box-item" effect="dark" content="删除" placement="bottom-end">
                    <el-popconfirm width="300" title="删除后短链跳转会失效，同时停止数据统计，这是一个不可逆的操作，是否删除?"
                                   @confirm="removeLink(scope.row)">
                      <template #reference>
                        <el-icon class="table-edit">
                          <Delete />
                        </el-icon>
                      </template>
                    </el-popconfirm>
                  </el-tooltip>
                </template>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-block">
          <el-pagination v-model:current-page="pageParams.current" v-model:page-size="pageParams.size"
                         :page-sizes="[10, 15, 20, 30]" layout="total, sizes, prev, pager, next, jumper" :total="totalNums"
                         @size-change="handleSizeChange" @current-change="handleCurrentChange" />
        </div>
      </div>
    </div>
    <ChartsInfo style="width: 880px" ref="chartsInfoRef" :title="chartsInfoTitle" :info="chartsInfo"
                :tableInfo="tableInfo" :isGroup="isGroup" :nums="nums" :favicon="favicon1" :originUrl="originUrl1"
                @changeTime="changeTime" @changePage="changePage" top="60px"></ChartsInfo>
    <el-dialog v-model="isAddGroup" title="新建短链接分组" style="width: 40%">
      <el-form :model="form">
        <el-form-item label="分组名称：" :label-width="formLabelWidth">
          <el-input autocomplete="off" v-model="newGroupName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="isAddGroup = false">取消</el-button>
          <el-button type="primary" @click="addGroup" :loading="addGroupLoading"> 确认 </el-button>
        </span>
      </template>
    </el-dialog>
    <el-dialog v-model="isEditGroup" title="编辑短链接分组" style="width: 40%">
      <el-form :model="form">
        <el-form-item label="分组名称：" :label-width="formLabelWidth">
          <el-input autocomplete="off" v-model="editGroupName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="isEditGroup = false">取消</el-button>
          <el-button type="primary" @click="editGroup" :loading="editGroupLoading">
            确认
          </el-button>
        </span>
      </template>
    </el-dialog>
    <el-dialog @close="afterAddLink" v-model="isAddSmallLink" title="创建链接">
      <el-tabs class="demo-tabs">
        <el-tab-pane>
          <template #label>
            <span class="custom-tabs-label">
              <el-icon>
                <Link />
              </el-icon>
              <span>普通跳转</span>
            </span>
          </template>
          <CreateLink ref="createLink1Ref" :groupInfo="editableTabs" @onSubmit="addLink" @cancel="cancelAddLink"
                      :defaultGid="pageParams.gid" :is-single="true"></CreateLink>
        </el-tab-pane>
        <el-tab-pane>
          <template #label>
            <span class="custom-tabs-label">
              <el-icon>
                <Connection />
              </el-icon>
              <span>随机跳转</span>
            </span> </template>暂未开发</el-tab-pane>
      </el-tabs>
    </el-dialog>
    <el-dialog @close="afterAddLink" v-model="isEditLink" title="编辑链接">
      <EditLink ref="editLinkRef" :editData="editData" :groupInfo="editableTabs" @onSubmit="coverEditLink"
                @updatePage="updatePage" @cancel="coverEditLink"></EditLink>
    </el-dialog>
    <el-dialog @close="afterAddLink" v-model="isAddSmallLinks" title="批量链接">
      <CreateLinks ref="createLink2Ref" :groupInfo="editableTabs" @onSubmit="addLink" @cancel="cancelAddLink"
                   :defaultGid="pageParams.gid"></CreateLinks>
    </el-dialog>
  </div>
</template>

<script setup>
import {getCurrentInstance, onMounted, reactive, ref, watch} from 'vue'
import Sortable from 'sortablejs'
import ChartsInfo from './components/chartsInfo/ChartsInfo.vue'
import CreateLink from './components/createLink/CreateLink.vue'
import CreateLinks from './components/createLink/CreateLinks.vue'
import {getLastWeekFormatDate, getTodayFormatDate} from '@/utils/plugins.js'
import EditLink from './components/editLink/EditLink.vue'
import {ElMessage} from 'element-plus'
import defaultImg from '@/assets/png/短链默认图标.png'
import QRCode from './components/qrCode/QRCode.vue'

// 查看图表的时候传过去展示的，没什么用
const nums = ref(0)
const favicon1 = ref()
const originUrl1 = ref()
const orderIndex = ref(0)

const { proxy } = getCurrentInstance()
const API = proxy.$API
const chartsInfoRef = ref()
const chartsInfoTitle = ref()
const chartsInfo = ref()
const tableInfo = ref()
const createLink1Ref = ref()
const createLink2Ref = ref()
const editLinkRef = ref()
let selectedIndex = ref(0)
const editableTabs = ref([])

// 添加弹窗关闭后重新请求一下页面数据
const afterAddLink = () => {
  setTimeout(() => {
    getGroupInfo(queryPage)
    document.querySelector('.addButton') && document.querySelector('.addButton').blur()
  }, 0)
  if (createLink1Ref.value) createLink1Ref.value.initFormData()
  if (createLink2Ref.value) createLink2Ref.value.initFormData()
  if (editLinkRef.value) editLinkRef.value.initFormData()
}

const statsFormData = reactive({
  endDate: getTodayFormatDate(),
  startDate: getLastWeekFormatDate(),
  size: 10,
  current: 1
})

const initStatsFormData = () => {
  statsFormData.endDate = getTodayFormatDate()
  statsFormData.startDate = getLastWeekFormatDate()
}

const visitLink = {
  fullShortUrl: '',
  gid: '',
  enableStatus: null
}

// 打开的图表是分组（true为分组）的还是单链的
const isGroup = ref(false)
const tableFullShortUrl = ref()
const tableGid = ref()

// 点击查看数据图表
const chartsVisible = async (rowInfo, dateList) => {
  chartsInfoTitle.value = rowInfo?.description
  const { fullShortUrl, gid, group, originUrl, favicon, enableStatus } = rowInfo

  originUrl1.value = originUrl
  favicon1.value = favicon
  isGroup.value = group
  tableFullShortUrl.value = fullShortUrl
  tableGid.value = gid

  // 保存上下文
  visitLink.fullShortUrl = fullShortUrl
  visitLink.gid = gid
  visitLink.enableStatus = enableStatus

  chartsInfoRef?.value.isVisible()

  if (!dateList) {
    initStatsFormData()
  } else {
    statsFormData.startDate = dateList?.[0] + ' 00:00:00'
    statsFormData.endDate = dateList?.[1] + ' 23:59:59'
  }

  let res = null
  let tableRes = null

  if (group) {
    res = await API.group.queryGroupStats({ ...statsFormData, fullShortUrl, gid })
    tableRes = await API.group.queryGroupTable({ gid, ...statsFormData })
  } else {
    res = await API.smallLinkPage.queryLinkStats({ ...statsFormData, fullShortUrl, gid, enableStatus })
    tableRes = await API.smallLinkPage.queryLinkTable({ gid, fullShortUrl, ...statsFormData, enableStatus })
  }

  // 🔥 修复：取出 data 层
  tableInfo.value = tableRes?.data
  chartsInfo.value = res?.data
}

// 图表修改时间后重新请求数
const changeTimeData = async (rowInfo, dateList) => {
  const { fullShortUrl, gid, enableStatus } = rowInfo
  if (!dateList) {
    initStatsFormData()
  } else {
    statsFormData.startDate = dateList?.[0] + ' 00:00:00'
    statsFormData.endDate = dateList?.[1] + ' 23:59:59'
  }

  let res = null
  let tableRes = null

  if (isGroup.value) {
    res = await API.group.queryGroupStats({ ...statsFormData, fullShortUrl, gid })
    tableRes = await API.group.queryGroupTable({ gid, ...statsFormData })
  } else {
    res = await API.smallLinkPage.queryLinkStats({ ...statsFormData, fullShortUrl, gid, enableStatus })
    tableRes = await API.smallLinkPage.queryLinkTable({ gid, fullShortUrl, ...statsFormData, enableStatus })
  }

  // 🔥 修复：取出 data 层
  tableInfo.value = tableRes?.data
  chartsInfo.value = res?.data
}

// 修改时间
const changeTime = (dateList) => {
  changeTimeData(visitLink, dateList)
}

// 修改页码信息
const changePage = async (page) => {
  const { current, size } = page
  statsFormData.current = current ?? 1
  statsFormData.size = size ?? 10

  let tableRes = null
  if (isGroup.value) {
    tableRes = await API.group.queryGroupTable({ gid: tableGid.value, ...statsFormData })
  } else {
    tableRes = await API.smallLinkPage.queryLinkTable({
      gid: tableGid.value,
      fullShortUrl: tableFullShortUrl.value,
      ...statsFormData
    })
  }

  // 🔥 修复：取出 data 层
  tableInfo.value = tableRes?.data
}

// --- 以下代码保持不变 ---

const transformGroupData = (oldIndex, newIndex) => {
  const formData = editableTabs.value
  const tempData = formData.splice(oldIndex, 1)
  formData.splice(newIndex, 0, tempData[0])
  formData.forEach((item, index) => {
    item.sortOrder = index
  })
  return formData
}

const initSortable = (className) => {
  const table = document.querySelector('.' + className)
  Sortable.create(table, {
    animation: 150,
    onEnd: async ({ to, from, oldIndex, newIndex }) => {
      if (newIndex !== oldIndex) {
        if (selectedIndex.value === oldIndex) {
          selectedIndex.value = newIndex
        } else if (oldIndex < newIndex && selectedIndex.value > oldIndex && selectedIndex.value <= newIndex) {
          selectedIndex.value = selectedIndex.value - 1
        } else if (oldIndex > newIndex && selectedIndex.value < oldIndex && selectedIndex.value >= newIndex) {
          selectedIndex.value = selectedIndex.value + 1
        }
        await API.group.sortGroup(transformGroupData(oldIndex, newIndex))
      }
    }
  })
}

watch(() => selectedIndex.value, (newValue) => {
  if (newValue !== -1 && newValue !== -2) {
    queryPage()
  }
})

onMounted(() => {
  initSortable('sortOptions')
})

const tableData = ref([])
const pageParams = reactive({
  gid: null,
  current: 1,
  size: 15,
  orderTag: null
})

watch(() => pageParams.orderTag, () => {
  // 根据当前是否在回收站，调用不同的查询方法
  if (isRecycleBin.value) {
    queryRecycleBinPage()
  } else {
    queryPage()
  }
})

const totalNums = ref(0)

const queryPage = async () => {
  pageParams.gid = editableTabs.value?.[selectedIndex.value]?.gid
  nums.value = editableTabs.value?.[selectedIndex.value]?.shortLinkCount || 0
  const res = await API.smallLinkPage.queryPage(pageParams)
  tableData.value = res.data?.records
  totalNums.value = +res.data?.total
}

const handleSizeChange = () => {
  !isRecycleBin.value ? queryPage() : queryRecycleBinPage()
}

const handleCurrentChange = () => {
  !isRecycleBin.value ? queryPage() : queryRecycleBinPage()
}

const getGroupInfo = async (fn) => {
  const res = await API.group.queryGroup()
  editableTabs.value = res.data?.reverse()
  fn && fn()
}
getGroupInfo(queryPage)

const updatePage = () => {
  getGroupInfo(queryPage)
}

const isRecycleBin = ref(false)
const recycleBinNums = ref(0)

const queryRecycleBinPage = () => {
  const gidList = editableTabs.value.map(item => item.gid)
  API.smallLinkPage
      .queryRecycleBin({
        current: pageParams.current,
        size: pageParams.size,
        orderTag: pageParams.orderTag,
        // 补上 GID 列表 (解决 Sharding value null 问题)
        gidList: gidList.join(',')
      })
      .then((res) => {
        tableData.value = res.data?.records
        totalNums.value = +res.data?.total
        recycleBinNums.value = totalNums.value
      })
}

const recycleBin = () => {
  isRecycleBin.value = true
  selectedIndex.value = -1
  pageParams.current = 1
  pageParams.size = 15
  queryRecycleBinPage()
}

const changeSelectIndex = (index) => {
  selectedIndex.value = index
  isRecycleBin.value = false
}

const isAddGroup = ref(false)
const newGroupName = ref()
const addGroupLoading = ref(false)

const showAddGroup = () => {
  newGroupName.value = ''
  isAddGroup.value = true
}

const addGroup = async () => {
  try {
    addGroupLoading.value = true
    await API.group.addGroup({ name: newGroupName.value })
    ElMessage.success('添加成功')
    getGroupInfo(queryPage)
    isAddGroup.value = false
  } catch (e) {
  } finally {
    addGroupLoading.value = false
  }
}

const deleteGroup = async (gid) => {
  await API.group.deleteGroup({ gid })
  selectedIndex.value = 0
  ElMessage.success('删除成功')
  getGroupInfo(queryPage)
}

const isEditGroup = ref(false)
const editGroupName = ref()
const editGid = ref('')
const editGroupLoading = ref(false)

const showEditGroup = (gid, name) => {
  editGid.value = gid
  editGroupName.value = name
  isEditGroup.value = true
}

const editGroup = async () => {
  try {
    editGroupLoading.value = true
    await API.group.editGroup({ gid: editGid.value, name: editGroupName.value })
    ElMessage.success('编辑成功')
    getGroupInfo(queryPage)
    isEditGroup.value = false
  } catch (e) {
  } finally {
    editGroupLoading.value = false
  }
}

const isAddSmallLink = ref(false)
const isAddSmallLinks = ref(false)

const addLink = () => {
  isAddSmallLink.value = false
  isAddSmallLinks.value = false
}

const cancelAddLink = () => {
  isAddSmallLink.value = false
  isAddSmallLinks.value = false
}

const getImgUrl = (url) => url ?? defaultImg

const isExpire = (validDate) => {
  if (validDate) {
    return new Date().getTime() < new Date(validDate).getTime()
  }
}

const copyUrl = (url) => {
  let eInput = document.createElement('input')
  eInput.value = url
  document.body.appendChild(eInput)
  eInput.select()
  let copyText = document.execCommand('Copy')
  eInput.style.display = 'none'
  if (copyText) {
    ElMessage.success('链接复制成功!')
  }
}

const isEditLink = ref(false)
const editData = ref()

const editLink = (data) => {
  editData.value = data
  isEditLink.value = true
}

const coverEditLink = () => {
  isEditLink.value = false
}

const toRecycleBin = (data) => {
  const { gid, fullShortUrl } = data
  API.smallLinkPage.toRecycleBin({ gid, fullShortUrl }).then(() => {
    ElMessage.success('删除成功')
    getGroupInfo(queryPage)
  })
}

const recoverLink = (data) => {
  const { gid, fullShortUrl, originUrl, enableStatus } = data
  API.smallLinkPage.recoverLink({ gid, originUrl, fullShortUrl, enableStatus }).then(() => {
    ElMessage.success('恢复成功')
    queryRecycleBinPage()
    getGroupInfo()
  })
}

const removeLink = (data) => {
  const { gid, fullShortUrl } = data
  API.smallLinkPage.removeLink({ gid, fullShortUrl }).then(() => {
    ElMessage.success('删除成功')
    queryRecycleBinPage()
  })
}
</script>
<style lang="scss" scoped>
.flex-box {
  display: flex;
  align-items: center;
  padding: 0 10px;
  justify-content: space-between;
}

.hover-box:hover {
  cursor: pointer;
  color: rgba(40, 145, 206, 0.6);
  background-color: #f7f7f7;
  box-shadow: 0px 2px 8px 0px rgba(28, 41, 90, 0.1);
}

.option-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 56px;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);

  span {
    font-size: 12px;
    font-weight: 400;
  }
}

.options-box {
  display: flex;
  flex-direction: column;
  position: relative;
  height: 100%;
  width: 190px;
  border-right: 1px solid rgba(0, 0, 0, 0.1);

  .item-box {
    height: 43px;
    width: 190px;
    font-family:
        PingFangSC-Semibold,
        PingFang SC;
    font-weight: 520;
  }

  .item-box:hover {
    .flex-box {
      .edit {
        display: block;
      }

      .item-length {
        display: none !important;
      }
    }
  }
}

.recycle-bin {
  position: absolute;
  display: flex;
  bottom: 0;
  height: 50px;
  width: 100%;
}

.recycle-box {
  flex: 1;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
}

.edit {
  display: none;
  margin-left: 5px;
  color: rgb(83, 97, 97);
  font-size: 20px;
}

.edit:hover {
  color: #2991ce;
  cursor: pointer;
}

.zero {
  color: rgb(83, 97, 97) !important;
}

// 提示框样式
.tooltip-base-box {
  width: 600px;
}

.tooltip-base-box .row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tooltip-base-box .center {
  justify-content: center;
}

.tooltip-base-box .box-item {
  width: 110px;
}

.selectedItem {
  color: #3464e0 !important;
  background-color: #ebeffa !important;
  font-weight: 600 !important;
}

.block:hover {
  color: rgb(121, 187, 255);

  .el-icon {
    color: rgb(121, 187, 255) !important;
  }
}

.table-edit {
  font-size: 20px;
  margin-right: 20px;
  color: #3677c2;
  cursor: pointer;
}

.table-edit:hover {
  color: #98cafe;
}

.qr-code {
  margin-right: 20px;
  cursor: pointer;
}

.qr-code:hover {
  opacity: 0.5;
}

.content-box {
  flex: 1;
  padding: 16px;
  background-color: #eef0f5;
  position: relative;

  .table-box {
    background-color: #ffffff;
    height: 100%;

    .buttons-box {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px;
    }

    .pagination-block {
      position: absolute;
      bottom: 4%;
      left: 50%;
      transform: translate(-50%, 0);
    }

    .recycle-bin-box {
      height: 64px;
      display: flex;
      align-items: center;
      padding-left: 16px;

      span:nth-child(1) {
        font-size: 20px;
        margin-right: 5px;
      }
    }
  }
}

.over-text {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box; //作为弹性伸缩盒子模型显示。
  -webkit-box-orient: vertical; //设置伸缩盒子的子元素排列方式--从上到下垂直排列
  -webkit-line-clamp: 1; //显示的行
}

.table-link-box {
  display: flex;
  align-items: center;

  .name-date {
    display: flex;
    flex-direction: column;
    margin-left: 10px;

    span:nth-child(1) {
      font-size: 15px;
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box; //作为弹性伸缩盒子模型显示。
      -webkit-box-orient: vertical; //设置伸缩盒子的子元素排列方式--从上到下垂直排列
      -webkit-line-clamp: 1; //显示的行
    }

    .time {
      display: flex;
      align-items: center;

      span {
        font-size: 12px;
      }

      img {
        margin-left: 10px;
      }

      div {
        border: 1.5px solid rgb(253, 81, 85);
        border-radius: 8px;
        line-height: 20px;
        font-size: 12px;
        transform: scale(0.7);
        color: rgb(253, 81, 85);
        padding: 0 4px;
        background-color: rgba(250, 210, 211);

        span {
          font-weight: bolder;
        }
      }
    }
  }
}

.isExpire {
  .name-date {
    span:nth-child(1) {
      color: rgba(0, 0, 0, 0.3);
    }

    .time {
      div {
        span {
          font-weight: bolder;
          color: red;
        }
      }
    }
  }
}

.table-url-box {
  display: flex;
  flex-direction: column;
  align-items: flex-start;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box; //作为弹性伸缩盒子模型显示。
    -webkit-box-orient: vertical; //设置伸缩盒子的子元素排列方式--从上到下垂直排列
    -webkit-line-clamp: 1; //显示的行
    color: rgba(0, 0, 0, 0.4);
  }
}

.times-box {
  display: flex;
  flex-direction: column;

  .today-box {
    span {
      font-size: 13px;
      font-weight: 600;
      margin-right: 10px;
    }

    span:nth-child(1) {
      font-weight: 400;
      color: rgba(0, 0, 0, 0.4);
    }
  }

  .total-box {
    span {
      font-size: 13px;
      font-weight: 400;
      margin-right: 10px;
    }

    span:nth-child(1) {
      font-weight: 400;
      color: rgba(0, 0, 0, 0.4);
    }
  }
}

.copy-url {
  margin-left: 10px;
}

.demo-tabs>.el-tabs__content {
  font-size: 32px;
  font-weight: 600;
}

.demo-tabs .custom-tabs-label .el-icon {
  vertical-align: middle;
}

.demo-tabs .custom-tabs-label span {
  vertical-align: middle;
  margin-left: 4px;
}

.orderIndex {
  color: #3677c2;
}

.sortOptions {
  height: calc(100% - 50px);
  margin-bottom: 50px;
  // height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>
