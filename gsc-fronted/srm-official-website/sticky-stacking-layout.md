# Sticky Stacking Layout (滚动堆叠布局)

## 简介
这是一个基于纯 CSS 实现的滚动堆叠动画布局（Sticky Stacking Effect）。当用户向下滚动页面时，各个区块会像卡片一样依次从下方滑出并覆盖在上一张卡片之上，形成一种非常具有沉浸感和故事性的视觉体验。

## 适用场景
- 产品特性的分步介绍
- 流程或时间线的展示
- 强调图片与文字对应关系的视觉模块（如供应链的多重危机、全球网络节点等）

## 核心原理
利用 CSS 的 `position: sticky` 和 `position: absolute` 结合不同的高度 (`height: 200vh`, `300vh`) 和偏移量 (`top: -100vh`)，使得每个模块在特定的滚动位置固定住，而后续模块继续向上滚动覆盖。

## HTML 结构规范

```html
<div class="scrolling-rows">
  <!-- data-sticky-wrap 用于在移动端或高度不足时通过 JS 关闭特效 -->
  <div class="scrolling-rows__rows" data-sticky-wrap="true">
    
    <!-- 循环渲染的每一行 -->
    <div class="scrolling-rows__row" v-for="(item, index) in items" :key="index">
      <div class="scrolling-rows__row-inner">
        <div class="scrolling-rows__row-sections">
          
          <!-- 左侧/上侧 图片区域 -->
          <div class="scrolling-rows__row-section--img">
            <img :src="item.img" :alt="item.title">
          </div>
          
          <!-- 右侧/下侧 文本区域 -->
          <div class="scrolling-rows__row-section--txt">
            <div class="scrolling-rows__row-title">
              <h2>{{ item.title }}</h2>
              <p class="sub-title">{{ item.sub }}</p>
            </div>
            <div class="scrolling-rows__row-txt">
              <!-- 文本内容 -->
            </div>
          </div>
          
        </div>
      </div>
    </div>

  </div>
</div>
```

## CSS 样式规范

```css
/* 容器基础设置 */
.scrolling-rows {
  width: 100%;
}

/* 每一行占满屏幕高度，并裁剪溢出部分 */
.scrolling-rows__rows[data-sticky-wrap="true"] .scrolling-rows__row {
  position: relative;
  height: 100vh;
  overflow-y: clip;
}

/* 内部容器绝对定位，开启 3D 变换以解决部分浏览器的渲染层级问题 */
.scrolling-rows__rows[data-sticky-wrap="true"] .scrolling-rows__row-inner {
  position: absolute;
  left: 0;
  width: 100%;
  transform-style: preserve-3d;
  z-index: 2;
}

/* 第一项：高度 200vh，从顶部开始 */
.scrolling-rows__rows[data-sticky-wrap="true"] .scrolling-rows__row:first-child .scrolling-rows__row-inner {
  top: 0;
  height: 200vh;
}

/* 非第一项：向上偏移 100vh，以便被前一项覆盖 */
.scrolling-rows__rows[data-sticky-wrap="true"] .scrolling-rows__row:not(:first-child) .scrolling-rows__row-inner {
  top: -100vh;
}

/* 中间项：高度 300vh，保证在滚动过程中有足够的停留时间 */
.scrolling-rows__rows[data-sticky-wrap="true"] .scrolling-rows__row:not(:first-child):not(:last-child) .scrolling-rows__row-inner {
  height: 300vh;
}

/* 最后一项：高度 200vh */
.scrolling-rows__rows[data-sticky-wrap="true"] .scrolling-rows__row:last-child .scrolling-rows__row-inner {
  height: 200vh;
}

/* 实际的吸顶内容区域 */
.scrolling-rows__rows[data-sticky-wrap="true"] .scrolling-rows__row-sections {
  position: sticky;
  top: 0;
  height: 100vh;
  backface-visibility: hidden;
  transform: translate3d(0, 0, 0);
  display: flex;
}

/* 移动端适配：改为上下布局 */
@media (max-width: 768px) {
  .scrolling-rows__rows[data-sticky-wrap="true"] .scrolling-rows__row-sections {
    flex-direction: column;
  }
}

/* 图片区域样式 */
.scrolling-rows__row-section--img {
  flex: 1;
  overflow: hidden;
}

.scrolling-rows__row-section--img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 文本区域样式 */
.scrolling-rows__row-section--txt {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 10%;
  background-color: #fff;
  color: #333;
}
```

## JS 交互规范 (Vue 3 示例)

为了保证在移动端横屏或屏幕高度过小的情况下，布局不会崩溃，需要加入一个简单的 JS 监听逻辑，动态切换 `data-sticky-wrap` 属性。

```javascript
import { ref, onMounted, onUnmounted } from 'vue'

const isStickyWrap = ref(true)

function checkSticky() {
  // 当屏幕高度大于 400px 时开启特效，否则关闭
  isStickyWrap.value = window.innerHeight > 400
}

onMounted(() => {
  window.addEventListener('resize', checkSticky)
  checkSticky()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkSticky)
})
```