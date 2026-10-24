import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { ElButton, ElInput, ElForm, ElFormItem, ElAlert, ElDialog, ElTooltip } from 'element-plus'
import 'element-plus/theme-chalk/base.css'
import 'element-plus/theme-chalk/el-button.css'
import 'element-plus/theme-chalk/el-input.css'
import 'element-plus/theme-chalk/el-form.css'
import 'element-plus/theme-chalk/el-form-item.css'
import 'element-plus/theme-chalk/el-alert.css'
import 'element-plus/theme-chalk/el-dialog.css'
import 'element-plus/theme-chalk/el-tooltip.css'
import 'element-plus/theme-chalk/el-overlay.css'
import App from './App.vue'
import { router } from './app/router'
import { queryClient } from './app/services'
import './styles/tokens.css'
import './styles/global.css'

const app = createApp(App)
app.use(createPinia())
app.use(VueQueryPlugin, { queryClient })
for (const component of [ElButton, ElInput, ElForm, ElFormItem, ElAlert, ElDialog, ElTooltip]) app.use(component)
app.use(router)
app.mount('#app')
