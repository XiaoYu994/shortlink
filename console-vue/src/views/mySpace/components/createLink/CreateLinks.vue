<template>
  <div>
    <el-form ref="ruleFormRef" :model="formData" :rules="formRule" label-width="80px">
      <el-form-item label="跳转链接" prop="originUrls">
        <el-input
            :rows="4"
            v-model="formData.originUrls"
            type="textarea"
            placeholder="请输入http://或https://开头的链接或应用跳转链接，一行一个，最多100行"
        />
        <span style="font-size: 12px">{{ originUrlRows + '/' + maxDescribeRows }}</span>
      </el-form-item>
      <el-form-item label="描述信息" prop="describes">
        <el-input
            v-loading="isLoading"
            :rows="4"
            v-model="formData.describes"
            type="textarea"
            placeholder="请输入描述信息，一行一个，描述信息行数请与链接行数相等"
        />
        <span style="font-size: 12px">{{ describeRows + '/' + maxDescribeRows }}</span>
      </el-form-item>
      <el-form-item label="短链分组" prop="gid">
        <el-select v-model="formData.gid" placeholder="请选择">
          <el-option
              v-for="item in groupInfo"
              :key="item.gid"
              :label="item.name"
              :value="item.gid"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="有效期" prop="v">
        <el-radio-group v-model="formData.validDateType">
          <el-radio :label="0">永久</el-radio>
          <el-radio :label="1">自定义</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="formData.validDateType === 1" label="选择时间">
        <el-date-picker
            :disabled-date="disabledDate"
            v-model="formData.validDate"
            value-format="YYYY-MM-DD HH:mm:ss"
            type="datetime"
            placeholder="选择日期"
            :shortcuts="shortcuts"
        />
        <span class="alert">链接失效后将自动跳转到404页面 !</span>
      </el-form-item>
      <el-form-item>
        <div style="width: 100%; display: flex; justify-content: flex-end">
          <el-button
              class="buttons"
              type="primary"
              :disabled="submitDisable"
              :loading="loading"
              @click="onSubmit(ruleFormRef)"
          >确认</el-button
          >
          <el-button class="buttons" @click="cancel">取消</el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import {getCurrentInstance, onBeforeUnmount, reactive, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {useStore} from 'vuex'

const store = useStore()
const defaultDomain = store.state.domain ?? ' '
const props = defineProps({
  groupInfo: Array,
  defaultGid: String
})
const { proxy } = getCurrentInstance()
const API = proxy.$API

const reg = /^(https?:\/\/(([a-zA-Z0-9]+-?)+[a-zA-Z0-9]+\.)+(([a-zA-Z0-9]+-?)+[a-zA-Z0-9]+))(:\d+)?(\/.*)?(\?.*)?(#.*)?$/
const shortcuts = [
  { text: '一天', value: () => { const date = new Date(); date.setTime(date.getTime() + 3600 * 1000 * 24); return date } },
  { text: '七天', value: () => { const date = new Date(); date.setTime(date.getTime() + 3600 * 1000 * 24 * 7); return date } },
  { text: '三十天', value: () => { const date = new Date(); date.setTime(date.getTime() + 3600 * 1000 * 24 * 30); return date } }
]

const groupInfo = ref()
const formData = reactive({
  domain: defaultDomain,
  originUrls: null,
  gid: null,
  createdType: 1,
  validDate: null,
  describes: null,
  validDateType: 0
})

const initFormData = () => {
  formData.domain = defaultDomain
  formData.originUrls = null
  formData.createdType = 1
  formData.validDate = null
  formData.describes = null
  formData.validDateType = 0
}

const maxOriginUrlRows = ref(100)
const originUrlRows = ref(0)
const isLoading = ref(false)
const loading = ref(false) // 提交 loading

watch(
    () => formData.originUrls,
    (nV) => {
      originUrlRows.value = (nV || '').split(/\r|\r\n|\n/)?.length ?? 0
    }
)

const maxDescribeRows = ref(100)
const describeRows = ref(0)
watch(
    () => formData.describes,
    (nV) => {
      describeRows.value = (nV || '').split(/\r|\r\n|\n/)?.length ?? 0
    }
)

watch(
    () => props.groupInfo,
    (nV) => {
      groupInfo.value = nV
      if (nV && nV.length > 0) {
        formData.gid = nV[0].gid
      }
    },
    { immediate: true }
)

watch(
    () => props.defaultGid,
    (nV) => {
      if (props.defaultGid) {
        formData.gid = props.defaultGid
      } else if (groupInfo.value && groupInfo.value.length > 0) {
        formData.gid = groupInfo.value[0].gid
      }
    },
    { immediate: true }
)

const formRule = reactive({
  originUrls: [
    { required: true, message: '请输入链接', trigger: 'blur' },
    {
      validator: function (rule, value, callback) {
        if (value) {
          value.split(/\r|\r\n|\n/).forEach((item) => {
            if (!reg.test(item)) {
              callback(new Error('请输入 http:// 或 https:// 开头的链接或应用跳转链接'))
            }
          })
        }
        if (originUrlRows.value > maxOriginUrlRows.value) {
          callback(new Error('超过输入' + maxOriginUrlRows.value + '行'))
        } else {
          callback()
          submitDisable.value = false
        }
      },
      trigger: 'blur'
    }
  ],
  gid: [{ required: true, message: '请选择分组', trigger: 'blur' }],
  describes: [
    { required: true, message: '请输入描述信息', trigger: 'blur' },
    {
      validator: function (rule, value, callback) {
        if (value) {
          value.split(/\r|\r\n|\n/).forEach((item) => {
            if (item === '' || !item) {
              callback(new Error('请不要输入空行'))
            }
          })
        }
        if (describeRows.value !== originUrlRows.value) {
          callback(new Error('标题数量与链接数量不等'))
        }
        if (describeRows.value > maxDescribeRows.value) {
          callback(new Error('超过输入' + maxDescribeRows.value + '行'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  validDate: [{ required: false, message: '请输日期', trigger: 'blur' }]
})

const disabledDate = (time) => {
  return new Date(time).getTime() < new Date().getTime()
}

const transferStrToArray = (str) => {
  return str.split(/[\n]+/)
}

const emits = defineEmits(['onSubmit', 'cancel'])

function downLoadXls(res) {
  // 如果是 Blob 对象，res 本身就是数据流
  // 如果拦截器返回的是 res (axios response)，则取 res.data
  const data = res.data || res

  let url = window.URL.createObjectURL(
      new Blob([data], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      })
  )
  let link = document.createElement('a')
  link.style.display = 'none'
  link.href = url

  // 尝试从 headers 获取文件名
  let fileName = '短链接批量创建.xlsx'
  if (res.headers && res.headers['content-disposition']) {
    try {
      fileName = decodeURI(res.headers['content-disposition'].split(';')[1].split('filename*=')[1])
    } catch (e) { console.warn('获取文件名失败', e) }
  }

  link.setAttribute('download', fileName)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

const ruleFormRef = ref()
const submitDisable = ref(false)

const onSubmit = async (formEl) => {
  if (!formEl) return
  await formEl.validate(async (valid, fields) => {
    if (valid) {
      try {
        loading.value = true
        submitDisable.value = true

        let { describes, originUrls } = formData
        describes = transferStrToArray(describes)
        originUrls = transferStrToArray(originUrls)

        // 调用接口
        // 假设批量创建接口返回的是 Blob (文件流)
        // 拦截器需要对 responseType: 'blob' 的请求做特殊放行
        const res = await API.smallLinkPage.addLinks({ ...formData, describes, originUrls })

        // 成功处理
        ElMessage.success('创建成功！短链列表已开始下载')
        emits('onSubmit', false)
        downLoadXls(res)

      } catch (error) {
        console.error('批量创建失败', error)
      } finally {
        loading.value = false
        submitDisable.value = false
      }
    } else {
      ElMessage.error('请检查输入项是否正确！')
    }
  })
}

const cancel = () => {
  emits('cancel', false)
  initFormData()
}

onBeforeUnmount(() => {
  initFormData()
})

defineExpose({
  initFormData
})
</script>

<style lang="less" scoped>
.alert {
  color: rgb(231, 166, 67);
  font-size: 12px;
  width: 90%;
}
</style>