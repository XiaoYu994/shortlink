<template>
  <div class="login-page">
    <h1 class="title">SaaS 短 链 接 平 台</h1>
    <div class="login-box">
      <div class="logon" :class="{ hidden: !isLogin }">
        <h2>用户登录</h2>
        <el-form ref="loginFormRef1" :model="loginForm" label-width="50px" :rules="loginFormRule">
          <div class="form-container1">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="请输入用户名" maxlength="11" show-word-limit clearable>
                <template v-slot:prepend> 用户名 </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" clearable placeholder="请输入密码" show-password
                        style="margin-top: 20px">
                <template v-slot:prepend> 密<span class="second-font">码</span> </template>
              </el-input>
            </el-form-item>
          </div>
          <div class="btn-gourp">
            <div>
              <el-checkbox class="remeber-password" v-model="checked"
                           style="color: #a0a0a0; margin: 0">记住密码</el-checkbox>
            </div>
            <div>
              <el-button :loading="loading" type="primary" plain
                         @click="login(loginFormRef1)">登录</el-button>
            </div>
          </div>
        </el-form>
      </div>
      <div class="register" :class="{ hidden: isLogin }">
        <h2>用户注册</h2>
        <el-form ref="loginFormRef2" :model="addForm" label-width="50px" class="form-container"
                 :rules="addFormRule">
          <el-form-item prop="username">
            <el-input v-model="addForm.username" placeholder="请输入用户名" maxlength="11" show-word-limit clearable>
              <template v-slot:prepend> 用户名 </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="mail">
            <el-input v-model="addForm.mail" placeholder="请输入邮箱" show-word-limit clearable>
              <template v-slot:prepend> 邮<span class="second-font">箱</span> </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="phone">
            <el-input v-model="addForm.phone" placeholder="请输入手机号" show-word-limit clearable>
              <template v-slot:prepend> 手机号 </template>
            </el-input>
          </el-form-item>
          <el-form-item prop="realName">
            <el-input v-model="addForm.realName" placeholder="请输入姓名" show-word-limit clearable>
              <template v-slot:prepend> 姓<span class="second-font">名</span> </template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input v-model="addForm.password" type="password" clearable placeholder="请输入密码" show-password>
              <template v-slot:prepend> 密<span class="second-font">码</span> </template>
            </el-input>
          </el-form-item>

          <div class="btn-gourp">
            <div></div>
            <div>
              <el-button :loading="loading" type="primary" plain
                         @click="addUser(loginFormRef2)">注册</el-button>
            </div>
          </div>
        </el-form>
      </div>
      <div class="move" ref="moveRef">
        <span style="font-size: 18px; margin-bottom: 25px; color: rgb(225, 238, 250)">{{
            !isLogin ? '已有账号？' : '还没有账号？'
          }}</span>
        <span style="font-size: 16px; color: rgb(225, 238, 250)">{{
            !isLogin ? '欢迎登录账号！' : '欢迎注册账号！'
          }}</span>
        <el-button style="width: 100px; margin-top: 30px" @click="changeLogin">{{
            !isLogin ? '去登录' : '去注册'
          }}</el-button>
      </div>
    </div>
    <div ref="vantaRef" class="vanta"></div>
  </div>

  <el-dialog v-model="isWC" title="人机验证" width="40%" :before-close="handleClose">
    <div class="verification-flex">
      <span>扫码下方二维码，关注后回复：<strong><span style="color:blue;">link</span></strong>，获取拿个offer-SaaS短链接系统人机验证码</span>
      <img class="img" src="@/assets/png/公众号二维码.png" alt="">
      <el-form class="form" :model="verification" :rules="verificationRule" ref="verificationRef">
        <el-form-item prop="code" label="验证码">
          <el-input v-model="verification.code" />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="isWC = false">取消</el-button>
        <el-button type="primary" @click="verificationLogin(verificationRef)" :loading="loading">
          确认
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import {setToken, setUsername} from '@/core/auth.js'
import {getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import * as THREE from 'three'
import WAVES from 'vanta/src/vanta.waves'

const { proxy } = getCurrentInstance()
const API = proxy.$API
const router = useRouter()

const loginFormRef1 = ref()
const loginFormRef2 = ref()
const loading = ref(false) // 全局 loading 状态

const loginForm = reactive({
  username: 'admin',
  password: 'admin123456',
})
const addForm = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  mail: ''
})

// 表单验证规则
const addFormRule = reactive({
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3|5|7|8|9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
    { min: 11, max: 11, message: '手机号必须是11位', trigger: 'blur' }
  ],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 15, message: '密码长度请在八位以上', trigger: 'blur' }
  ],
  mail: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    {
      pattern: /^([a-zA-Z]|[0-9])(\w|\-)+@[a-zA-Z0-9]+\.([a-zA-Z]{2,4})$/,
      message: '请输入正确的邮箱号',
      trigger: 'blur'
    }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
  ]
})

const loginFormRule = reactive({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 15, message: '密码长度请在八位以上', trigger: 'blur' }
  ],
})

// --- 核心逻辑 ---

// 处理登录成功后的逻辑
const handleLoginSuccess = (token, username) => {
  if (token) {
    setToken(token)
    setUsername(username)
    localStorage.setItem('token', token)
    localStorage.setItem('username', username)
  }
  ElMessage.success('登录成功！')
  router.push('/home')
}

// 注册逻辑
const addUser = (formEl) => {
  if (!formEl) return
  formEl.validate(async (valid) => {
    if (valid) {
      try {
        loading.value = true
        // 1. 检测用户名 (如果已存在，后端应返回错误码，拦截器会拦截并弹窗)
        // 注意：这里假设 hasUsername 接口：存在->报错；不存在->成功。
        // 如果后端是返回 {code:0, data: true/false}，则需要这里手动判断。
        // 根据你之前的逻辑，这里先假设拦截器处理异常。
        await API.user.hasUsername({ username: addForm.username })

        // 2. 注册
        await API.user.addUser(addForm)

        // 3. 自动登录
        const resLogin = await API.user.login({ username: addForm.username, password: addForm.password })

        // 4. 登录成功处理 (拦截器已解包，resLogin 就是 data 部分)
        // 注意：根据你的拦截器代码，return data。后端结构通常是 {code, message, data: {token: '...'}}
        // 所以这里取 resLogin.data.token
        handleLoginSuccess(resLogin.data?.token, addForm.username)

      } catch (error) {
        // 拦截器已经弹出错误提示了，这里只需关闭 loading
        console.error('注册流程中断', error)
      } finally {
        loading.value = false
      }
    }
  })
}

// 登录逻辑
const login = (formEl) => {
  if (!formEl) return
  formEl.validate(async (valid) => {
    if (valid) {
      // 演示环境拦截
      let domain = window.location.host
      // if (domain === 'shortlink.magestack.cn' || domain === 'shortlink.nageoffer.com') {
      //   isWC.value = true
      //   return
      // }

      try {
        loading.value = true
        const res = await API.user.login(loginForm)

        // 如果代码能走到这里，说明 code === '0'
        // 根据拦截器 return data (即 JSON body)，token 在 res.data.token
        handleLoginSuccess(res.data?.token, loginForm.username)

      } catch (error) {
        // 拦截器已处理错误提示
        console.error('登录失败', error)
      } finally {
        loading.value = false
      }
    }
  })
}

// 人机验证逻辑
const isWC = ref(false)
const verificationRef = ref()
const verification = reactive({ code: '' })
const verificationRule = reactive({
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
})

const handleClose = () => { isWC.value = false }

const verificationLogin = (formEl) => {
  if (!formEl) return
  formEl.validate(async (valid) => {
    if (valid) {
      const tempPassword = loginForm.password
      loginForm.password = verification.code

      try {
        loading.value = true
        const res = await API.user.login(loginForm)
        handleLoginSuccess(res.data?.token, loginForm.username)
        isWC.value = false
      } catch (error) {
        console.error('验证码登录失败', error)
      } finally {
        loginForm.password = tempPassword
        loading.value = false
      }
    }
  })
}

// --- 样式效果逻辑 ---

const checked = ref(true)
const vantaRef = ref()
let vantaEffect = null

onMounted(() => {
  vantaEffect = WAVES({
    el: vantaRef.value,
    THREE: THREE,
    mouseControls: true,
    touchControls: true,
    gyroControls: false,
    minHeight: 200.0,
    minWidth: 200.0,
    scale: 1.0,
    scaleMobile: 1.0
  })
})

onBeforeUnmount(() => {
  if (vantaEffect) {
    vantaEffect.destroy()
  }
})

const isLogin = ref(true)
const moveRef = ref()

const changeLogin = () => {
  let domain = window.location.host
  if (domain === 'shortlink.magestack.cn' || domain === 'shortlink.nageoffer.com') {
    ElMessage.warning('演示环境暂不支持注册')
    return
  }
  isLogin.value = !isLogin.value
  if (isLogin.value) {
    moveRef.value.style.transform = 'translate(0, 0)'
  } else {
    moveRef.value.style.transform = 'translate(-420px, 0)'
  }
}
</script>

<style lang="less" scoped>
/* 保持原有样式不变 */
.login-box {
  border: 2px solid #0984e3;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  border-radius: 20px;
  padding: 0 40px 0 40px;
  width: 700px;
  position: absolute;
  z-index: 999;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  box-sizing: border-box;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.2);
  background-color: #fff;
  animation: hideIndex 0.5s;

  h2 {
    font-size: 30px;
    font-weight: 600;
    color: #3a3f63;
    width: 100%;
    text-align: center;
    padding: 20px;
  }

  .el-form-item {
    margin-bottom: 23px;
  }

  .btn-gourp {
    margin-top: 30px;
    display: flex;
    justify-content: space-between;
    margin-bottom: 20px;

    .el-button {
      width: 100px;
    }
  }
}

/deep/ .el-form-item__content {
  margin-left: 0 !important;
}

@keyframes hideIndex {
  0% { opacity: 0; transform: translate(7.3125rem, -50%); }
  100% { opacity: 1; transform: translate(-50%, -50%); }
}

.login-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}

.vanta {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  bottom: 0;
  z-index: 0;
}

.logon {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.hidden {
  animation: hidden 1s;
  animation-fill-mode: forwards;
}

@keyframes hidden {
  0% { opacity: 1; }
  70% { opacity: 0; }
  100% { opacity: 0; }
}

.move {
  position: absolute;
  right: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  width: 40%;
  transition-duration: 0.5s;
  align-items: center;
  background: linear-gradient(to right, #1a8fd5, #0984e3);
}

.title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  top: 15%;
  z-index: 999;
  font-size: 40px;
  color: #fff;
  font-weight: bolder;
}

:deep(.el-input__suffix-inner) {
  width: 60px;
}

/* 注意：这里的 translateY 可能会导致布局错位，建议检查 */
.form-container1 {
  transform: translateY(-80%);
}

.second-font {
  margin-left: 13px;
}

.verification-flex {
  display: flex;
  flex-direction: column;
  align-items: flex-start;

  .img {
    margin-top: 10px;
    align-self: center;
  }
  .form {
    transform: translateY(15px);
    width: 90%;
  }
}
</style>