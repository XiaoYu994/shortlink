<template>
  <div>
    <el-form ref="ruleFormRef" :model="formData" :rules="formRule" label-width="80px">
      <el-form-item label="跳转链接" prop="originUrl">
        <el-input
            v-if="isSingle"
            v-model="formData.originUrl"
            placeholder="请输入http://或https://开头的链接或应用跳转链接"
        ></el-input>
        <el-input
            v-else
            :rows="4"
            v-model="formData.originUrl"
            type="textarea"
            placeholder="请输入http://或https://开头的链接或应用跳转链接，一行一个，最多100行"
        />
      </el-form-item>
      <el-form-item label="描述信息" prop="describe">
        <el-input
            maxlength="100"
            show-word-limit
            v-loading="isLoading"
            :rows="4"
            v-model="formData.describe"
            type="textarea"
            placeholder="请输入描述信息"
        />
        <span style="font-size: 12px">{{ '将创建' + describeRows + '条短链' }}</span>
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
  isSingle: Boolean, // 单个创建传true， 批量创建传false,
  defaultGid: String
})
const { proxy } = getCurrentInstance()
const API = proxy.$API

// url的校验规则
const reg = /^(https?:\/\/(([a-zA-Z0-9]+-?)+[a-zA-Z0-9]+\.)+(([a-zA-Z0-9]+-?)+[a-zA-Z0-9]+))(:\d+)?(\/.*)?(\?.*)?(#.*)?$/

// 自定义时间中选择几天
const shortcuts = [
  { text: '一天', value: () => { const date = new Date(); date.setTime(date.getTime() + 3600 * 1000 * 24); return date } },
  { text: '七天', value: () => { const date = new Date(); date.setTime(date.getTime() + 3600 * 1000 * 24 * 7); return date } },
  { text: '三十天', value: () => { const date = new Date(); date.setTime(date.getTime() + 3600 * 1000 * 24 * 30); return date } }
]

const groupInfo = ref()
const formData = reactive({
  domain: defaultDomain,
  originUrl: null,
  gid: null,
  createdType: 1,
  validDate: null,
  describe: null,
  validDateType: 0
})

const initFormData = () => {
  formData.domain = defaultDomain
  formData.originUrl = null
  formData.createdType = 1
  formData.validDate = null
  formData.describe = null
  formData.validDateType = 0
}

const maxOriginUrlRows = ref(100)
const originUrlRows = ref(0)
const loading = ref(false) // 提交按钮 loading
const isLoading = ref(false) // 描述框 loading

// 防抖
const fd = (fn, delay) => {
  let timer = null
  return function (url) {
    if (timer) { clearTimeout(timer); timer = null }
    timer = setTimeout(() => { fn(url) }, delay)
  }
}

// 自动获取标题
const queryTitle = (url) => {
  if (reg.test(url)) {
    isLoading.value = true
    // 注意：这里 queryTitle 如果失败了拦截器会报错，这里 catch 一下避免阻塞
    API.smallLinkPage.queryTitle({ url: url })
        .then((res) => {
          // 假设拦截器返回 res.data (JSON body)
          formData.describe = res?.data
        })
        .catch(() => {})
        .finally(() => { isLoading.value = false })
  }
}
const getTitle = fd(queryTitle, 1000)

watch(
    () => formData.originUrl,
    (nV) => {
      originUrlRows.value = (nV || '').split(/\r|\r\n|\n/)?.length ?? 0
      if (!formData.describe) {
        getTitle(nV)
      }
    }
)

const maxDescribeRows = ref(100)
const describeRows = ref(0)
watch(
    () => formData.describe,
    (nV) => {
      describeRows.value = (nV || '').split(/\r|\r\n|\n/)?.length ?? 0
    }
)

// 初始化分组
watch(
    () => props.groupInfo,
    (nV) => {
      groupInfo.value = nV
      if (props.defaultGid) {
        formData.gid = props.defaultGid
      } else if (nV && nV.length > 0) {
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

// 校验规则
const formRule = reactive({
  originUrl: [
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
  describe: [
    { required: true, message: '请输入描述信息', trigger: 'blur' },
    {
      validator: function (rule, value, callback) {
        if (props.isSingle === false && describeRows.value !== originUrlRows.value) {
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

const emits = defineEmits(['onSubmit', 'cancel'])
const ruleFormRef = ref()
const submitDisable = ref(false)

// 提交表单
const onSubmit = async (formEl) => {
  if (!formEl) return
  await formEl.validate(async (valid, fields) => {
    if (valid) {
      try {
        loading.value = true
        submitDisable.value = true

        // 调用接口 (拦截器已处理业务错误 code != 0)
        await API.smallLinkPage.addSmallLink(formData)

        // 成功
        ElMessage.success('创建成功！')
        emits('onSubmit', false)

      } catch (error) {
        // 失败 (拦截器已弹窗，这里只负责恢复按钮状态)
        console.error('创建短链接失败', error)
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