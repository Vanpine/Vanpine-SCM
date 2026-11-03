/**
 * SRM Admin - ESLint 配置（Flat Config）
 * Vue 3 + ES2021
 *
 * 命令行：
 *   npm run lint       自动修复
 *   npm run lint:check 只检查不修复
 */
import pluginVue from 'eslint-plugin-vue'

export default [
  // Vue 3 推荐规则
  ...pluginVue.configs['flat/recommended'],

  // 自定义规则
  {
    rules: {
      // ---- 强制 ----
      'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
      'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      'no-var': 'error',
      'prefer-const': 'warn',

      // ---- Vue ----
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'warn',
      'vue/require-default-prop': 'off',
      'vue/require-prop-types': 'warn',
      'vue/block-order': ['warn', {
        order: ['template', 'script', 'style'],
      }],

      // ---- 风格 ----
      'semi': ['warn', 'never'],
      'quotes': ['warn', 'single', { avoidEscape: true }],
      'comma-dangle': ['warn', 'always-multiline'],
      'arrow-parens': ['warn', 'always'],
      'object-curly-spacing': ['warn', 'always'],
      'array-bracket-spacing': ['warn', 'never'],
      'indent': ['warn', 2, { SwitchCase: 1 }],
      'max-len': ['warn', { code: 140, ignoreStrings: true, ignoreTemplateLiterals: true }],
    },
  },
]
