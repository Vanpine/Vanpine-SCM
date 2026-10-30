# CLAUDE.md — gs-vue 项目规范

## 排版与字体规范

### 标题字体
标题使用 `clamp` 实现流式缩放，不依赖媒体查询断点：
```
font-size: clamp(1.65rem, 4.2vw + 0.65rem, 3.35rem)
```

### 卡片标题字体大小
Tailwind 断点递进，根字号 16px 下的换算：
- `text-xl` → `sm:text-[1.3125rem]` → `md:text-[1.5rem]`
- 约等于：20px → 21px → 24px

### 内容字体大小
随视口在父级断点（20px / 21px / 24px）之间按比例切换：
- `text-base sm:text-[1.0625rem] md:text-[1.2125rem]`
- 约等于：16.2px → 17px → 19.4px

### 中文字距
```
letter-spacing: 0.06em
@media (min-width: 768px) {
  letter-spacing: 0.085em
}
```
项目中使用 `.text-cn` 工具类统一应用。

## 视觉规范

### 视频背景遮罩
视频背景上方叠加半透明黑色遮罩，透明度固定为 25%：
```html
<div class="absolute inset-0 z-10 bg-black/25" />
```

## 项目技术栈
- Vue 3 + Vite
- Tailwind CSS
- pnpm 包管理器
