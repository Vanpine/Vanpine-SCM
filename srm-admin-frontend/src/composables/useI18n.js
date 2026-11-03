import { ref } from 'vue'

const LANG_KEY = 'gsc-lang'
const current = ref(localStorage.getItem(LANG_KEY) || 'zh')

const messages = {
  zh: {
    welcome: '欢迎回来',
    subtitle: '登录您的企业账号',
    tenantCode: '租户编码',
    username: '用户名',
    password: '密码',
    login: '登 录',
    demoAccounts: '演示账号',
    superAdmin: '平台超管',
    tenantAdmin: '企业管理员',
    buyer: '采购员',
    supplier: '供应商',
    dualRole: '双向角色',
    rememberPwd: '记住密码',
    forgotPwd: '忘记密码？',
    brandTitle: '全球供应链 AI 赋能平台',
    brandSubtitle: '不脱钩、不断供 — 让每一次链接都值得信赖',
    forgotTitle: '忘记密码',
    forgotSubtitle: '请输入租户编码和注册邮箱',
    resetTitle: '重置密码',
    resetSubtitle: '验证码已发送至',
    emailAddress: '邮箱地址',
    sendCode: '获取验证码',
    sending: '发送中...',
    verifyCode: '6位验证码',
    newPasswordPlaceholder: '新密码（至少6位）',
    confirmPasswordPlaceholder: '确认新密码',
    resetPasswordBtn: '重置密码',
    backToLogin: '← 返回登录',
    dragText: '请按住滑块，拖动到最右边',
    dragSuccess: '验证通过',
    slideVerify: '请先完成滑动验证',
    codeSent: '验证码已发送',
    resend: '重新发送',
    codeResent: '验证码已重新发送',
    resetSuccess: '密码重置成功，请重新登录',
    passwordMismatch: '两次密码不一致',
  },
  en: {
    welcome: 'Welcome Back',
    subtitle: 'Sign in to your account',
    tenantCode: 'Tenant Code',
    username: 'Username',
    password: 'Password',
    login: 'Sign In',
    demoAccounts: 'Demo Accounts',
    superAdmin: 'Super Admin',
    tenantAdmin: 'Tenant Admin',
    buyer: 'Buyer',
    supplier: 'Supplier',
    dualRole: 'Dual Role',
    rememberPwd: 'Remember Me',
    forgotPwd: 'Forgot Password?',
    brandTitle: 'Global SCM AI Empowerment Platform',
    brandSubtitle: 'No Decoupling, No Disruption — Trust in Every Link',
    forgotTitle: 'Forgot Password',
    forgotSubtitle: 'Enter your tenant code and email',
    resetTitle: 'Reset Password',
    resetSubtitle: 'Verification code sent to',
    emailAddress: 'Email Address',
    sendCode: 'Send Code',
    sending: 'Sending...',
    verifyCode: '6-digit code',
    newPasswordPlaceholder: 'New password (min. 6 chars)',
    confirmPasswordPlaceholder: 'Confirm new password',
    resetPasswordBtn: 'Reset Password',
    backToLogin: '← Back to Login',
    dragText: 'Slide to the right to verify',
    dragSuccess: 'Verified',
    slideVerify: 'Please complete the slider verification',
    codeSent: 'Verification code sent',
    resend: 'Resend',
    codeResent: 'Verification code resent',
    resetSuccess: 'Password reset successful. Please log in again.',
    passwordMismatch: 'Passwords do not match',
  },
}

export function useI18n() {
  function t(key) {
    const keys = key.split('.')
    let result = messages[current.value]
    for (const k of keys) {
      result = result?.[k]
    }
    return result ?? key
  }

  function setLang(lang) {
    current.value = lang
    localStorage.setItem(LANG_KEY, lang)
  }

  return { t, current, setLang }
}
