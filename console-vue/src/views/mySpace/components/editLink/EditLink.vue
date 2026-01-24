<template>
  <div>
    <el-form ref="ruleFormRef" :model="formData" :rules="formRule" label-width="80px">
      <el-form-item label="跳转链接" prop="originUrl">
        <el-input v-model="formData.originUrl" placeholder="请输入http://或https://开头的链接或应用跳转链接"></el-input>
      </el-form-item>
      <el-form-item label="描述信息" prop="describe">
        <el-input
            v-loading="isLoading"
            :rows="4"
            v-model="formData.describe"
            type="textarea"
            placeholder="可通过换行创建多个短链，一行一个，单次最多创建50条"
        />
        <span>{{ describeRows + '/' + maxDescribeRows }}</span>
      </el-form-item>

      <el-form-item label="短链分组" prop="gid">
        <el-select v-model="formData.gid" placeholder="请选择">
          <el-option v-for="item in groupInfo" :key="item.gid" :label="item.name" :value="item.gid" />
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
        <div style="width: 100%; display: flex; justify-content: flex-end;">
          <el-button
              class="buttons"
              type="primary"
              :loading="loading"
              @click="onSubmit(ruleFormRef)"
          >确认</el-button>
          <el-button class="buttons" @click="cancel">取消</el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import {getCurrentInstance, onBeforeUnmount, reactive, ref, watch} from 'vue'
import {useStore} from 'vuex'
import {ElMessage} from "element-plus";

const store = useStore()
const defaultDomain = store.state.domain ?? ' '
const props = defineProps({
  groupInfo: Array,
  editData: Object
})
const { proxy } = getCurrentInstance()
const API = proxy.$API
const editData = props.editData

const reg = /^(https?:\/\/(([a-zA-Z0-9]+-?)+[a-zA-Z0-9]+\.)+(([a-zA-Z0-9]+-?)+[a-zA-Z0-9]+))(:\d+)?(\/.*)?(\?.*)?(#.*)?$/;

const shortcuts = [
  { text: '一天', value: () => { const date = new Date(); date.setTime(date.getTime() + 3600 * 1000 * 24); return date }, },
  { text: '七天', value: () => { const date = new Date(); date.setTime(date.getTime() + 3600 * 1000 * 24 * 7); return date }, },
  { text: '三十天', value: () => { const date = new Date(); date.setTime(date.getTime() + 3600 * 1000 * 24 * 30); return date }, },
]

const groupInfo = ref()
const formData = reactive({
  domain: defaultDomain,
  originUrl: editData.originUrl,
  gid: editData.gid,
  originGid: editData.gid,
  createdType: editData.createdType,
  validDate: editData.validDate,
  describe: editData.describe,
  validDateType: editData.validDateType,
  fullShortUrl: editData.fullShortUrl
})

const initFormData = () => {
  formData.domain = defaultDomain
  formData.originUrl = null
  formData.createdType = 1
  formData.validDate = null
  formData.describe = null
  formData.validDateType = 0
  formData.fullShortUrl = null
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
    // catch 防止查询标题失败阻塞
    API.smallLinkPage.queryTitle({ url: url })
        .then(res => {
          // 假设拦截器返回 JSON body, 标题在 res.data
          formData.describe = res?.data
        })
        .catch(() => {})
        .finally(() => { isLoading.value = false })
  }
}
const getTitle = fd(queryTitle, 1000)

watch(
    () => formData.originUrl,
    nV => {
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
    nV => {
      describeRows.value = (nV || '').split(/\r|\r\n|\n/)?.length ?? 0
    }
)

watch(
    () => props.groupInfo,
    nV => {
      groupInfo.value = nV
      // 这里的逻辑有点奇怪：编辑时应该回显原来的 gid，而不是默认第一个
      // 但原代码就是这样写的，如果 editData 有值，下面的 watch props.editData 会覆盖它
      if (nV && nV.length > 0) {
        formData.gid = nV[0].gid
      }
    },
    { immediate: true }
)

watch(
    () => props.editData,
    (nV) => {
      if (!nV) return
      formData.originUrl = nV.originUrl
      formData.gid = nV.gid
      formData.originGid = nV.gid
      formData.createdType = nV.createdType
      formData.validDate = nV.validDate
      formData.describe = nV.describe
      formData.validDateType = nV.validDateType
      formData.fullShortUrl = nV.fullShortUrl
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
          value.split(/\r|\r\n|\n/).forEach(item => {
            if (!reg.test(item)) {
              callback(new Error('请输入 http:// 或 https:// 开头的链接或应用跳转链接'))
            }
          })
        }
        if (originUrlRows.value > maxOriginUrlRows.value) {
          callback(new Error('超过输入' + maxOriginUrlRows.value + '行'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    },
  ],
  gid: [{ required: true, message: '请选择分组', trigger: 'blur' }],
  describe: [
    { required: true, message: '请输入描述信息', trigger: 'blur' },
    {
      validator: function (rule, value, callback) {
        if (describeRows.value > maxDescribeRows.value) {
          callback(new Error('超过输入' + maxDescribeRows.value + '行'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    },
  ],
  validDate: [{ required: false, message: '请输日期', trigger: 'blur' }]
})

const disabledDate = (time) => {
  return new Date(time).getTime() < new Date().getTime()
}

const emits = defineEmits(['onSubmit', 'cancel', 'updatePage'])
const ruleFormRef = ref()

// 提交修改
const onSubmit = async (formEl) => {
  if (!formEl) return
  await formEl.validate(async (valid, fields) => {
    if (valid) {
      try {
        loading.value = true
        // 调用接口 (拦截器已处理错误)
        await API.smallLinkPage.editSmallLink(formData)

        // 成功处理
        ElMessage.success('修改成功')
        emits('onSubmit', false) // 关闭弹窗
        emits('updatePage') // 刷新列表

      } catch (error) {
        console.error('修改短链接失败', error)
      } finally {
        loading.value = false
      }
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