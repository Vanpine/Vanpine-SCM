<template>
  <div class="brand-panel">
    <!-- 背景网格 -->
    <div class="bg-grid" />
    <!-- 光晕 -->
    <div class="glow glow-1" />
    <div class="glow glow-2" />

    <div class="brand-illustration">
      <svg
        viewBox="0 0 520 380"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <!-- 发光滤镜 -->
          <filter id="glow">
            <feGaussianBlur
              stdDeviation="3"
              result="blur"
            />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <filter id="glowStrong">
            <feGaussianBlur
              stdDeviation="6"
              result="blur"
            />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
          <!-- 渐变 -->
          <linearGradient
            id="grad1"
            x1="0"
            y1="0"
            x2="1"
            y2="1"
          >
            <stop
              offset="0%"
              stop-color="#00e5ff"
            />
            <stop
              offset="100%"
              stop-color="#3b82f6"
            />
          </linearGradient>
          <linearGradient
            id="grad2"
            x1="0"
            y1="1"
            x2="1"
            y2="0"
          >
            <stop
              offset="0%"
              stop-color="#6366f1"
            />
            <stop
              offset="100%"
              stop-color="#00e5ff"
            />
          </linearGradient>
          <linearGradient
            id="grad3"
            x1="0"
            y1="0"
            x2="0"
            y2="1"
          >
            <stop
              offset="0%"
              stop-color="#60a5fa"
              stop-opacity="0.6"
            />
            <stop
              offset="100%"
              stop-color="#3b82f6"
              stop-opacity="0.1"
            />
          </linearGradient>
        </defs>

        <!-- 背景六边形网格 -->
        <g
          opacity="0.08"
          stroke="#60a5fa"
          stroke-width="0.5"
        >
          <polygon
            points="260,40 280,50 280,70 260,80 240,70 240,50"
            fill="none"
          />
          <polygon
            points="280,50 300,60 300,80 280,90 260,80 260,70"
            fill="none"
          />
          <polygon
            points="240,50 260,60 260,80 240,90 220,80 220,60"
            fill="none"
          />
          <polygon
            points="220,60 240,70 240,90 220,100 200,90 200,70"
            fill="none"
          />
          <polygon
            points="300,60 320,70 320,90 300,100 280,90 280,80"
            fill="none"
          />
          <polygon
            points="260,80 280,90 280,110 260,120 240,110 240,90"
            fill="none"
          />
        </g>

        <!-- 轨道环 -->
        <ellipse
          cx="260"
          cy="180"
          rx="160"
          ry="30"
          stroke="url(#grad1)"
          stroke-width="0.6"
          opacity="0.2"
          stroke-dasharray="6 3"
          transform="rotate(-15 260 180)"
        />
        <ellipse
          cx="260"
          cy="180"
          rx="140"
          ry="80"
          stroke="#3b82f6"
          stroke-width="0.5"
          opacity="0.15"
          transform="rotate(-15 260 180)"
        />
        <ellipse
          cx="260"
          cy="180"
          rx="140"
          ry="80"
          stroke="#00e5ff"
          stroke-width="0.8"
          opacity="0.08"
          stroke-dasharray="2 8"
          transform="rotate(25 260 180)"
        />

        <!-- 主球体 -->
        <circle
          cx="260"
          cy="180"
          r="70"
          fill="url(#grad3)"
          stroke="url(#grad1)"
          stroke-width="1"
          opacity="0.9"
          filter="url(#glow)"
        />

        <!-- 经纬线 -->
        <ellipse
          cx="260"
          cy="180"
          rx="28"
          ry="70"
          stroke="#60a5fa"
          stroke-width="0.5"
          opacity="0.3"
        />
        <ellipse
          cx="260"
          cy="180"
          rx="56"
          ry="70"
          stroke="#60a5fa"
          stroke-width="0.3"
          opacity="0.15"
        />
        <line
          x1="190"
          y1="180"
          x2="330"
          y2="180"
          stroke="#60a5fa"
          stroke-width="0.5"
          opacity="0.25"
        />
        <line
          x1="220"
          y1="115"
          x2="220"
          y2="245"
          stroke="#60a5fa"
          stroke-width="0.3"
          opacity="0.12"
        />
        <line
          x1="300"
          y1="115"
          x2="300"
          y2="245"
          stroke="#60a5fa"
          stroke-width="0.3"
          opacity="0.12"
        />

        <!-- 数据节点（球体上的亮点） -->
        <circle
          cx="260"
          cy="130"
          r="3"
          fill="#00e5ff"
          filter="url(#glowStrong)"
          opacity="0.9"
        />
        <circle
          cx="230"
          cy="155"
          r="2.5"
          fill="#3b82f6"
          filter="url(#glow)"
          opacity="0.8"
        />
        <circle
          cx="290"
          cy="150"
          r="2.5"
          fill="#00e5ff"
          filter="url(#glow)"
          opacity="0.85"
        />
        <circle
          cx="215"
          cy="195"
          r="2"
          fill="#6366f1"
          filter="url(#glow)"
          opacity="0.7"
        />
        <circle
          cx="305"
          cy="200"
          r="2.5"
          fill="#3b82f6"
          filter="url(#glow)"
          opacity="0.75"
        />
        <circle
          cx="250"
          cy="220"
          r="2"
          fill="#00e5ff"
          filter="url(#glow)"
          opacity="0.7"
        />
        <circle
          cx="275"
          cy="175"
          r="3.5"
          fill="#fff"
          filter="url(#glowStrong)"
          opacity="0.5"
        />

        <!-- 外围轨道卫星节点 -->
        <circle
          cx="140"
          cy="185"
          r="4"
          fill="#00e5ff"
          filter="url(#glowStrong)"
          opacity="0.9"
        >
          <animate
            attributeName="opacity"
            values="0.9;0.4;0.9"
            dur="2s"
            repeatCount="indefinite"
          />
        </circle>
        <circle
          cx="380"
          cy="175"
          r="4"
          fill="#6366f1"
          filter="url(#glowStrong)"
          opacity="0.8"
        >
          <animate
            attributeName="opacity"
            values="0.8;0.3;0.8"
            dur="2.5s"
            repeatCount="indefinite"
          />
        </circle>
        <circle
          cx="260"
          cy="90"
          r="3"
          fill="#3b82f6"
          filter="url(#glow)"
          opacity="0.7"
        />
        <circle
          cx="210"
          cy="270"
          r="3"
          fill="#00e5ff"
          filter="url(#glow)"
          opacity="0.6"
        />

        <!-- 数据流弧线 -->
        <path
          d="M140 185 Q180 130 260 130"
          stroke="url(#grad1)"
          stroke-width="1.5"
          opacity="0.4"
          fill="none"
          filter="url(#glow)"
        />
        <path
          d="M260 130 Q340 130 380 175"
          stroke="url(#grad1)"
          stroke-width="1.2"
          opacity="0.35"
          fill="none"
        />
        <path
          d="M380 175 Q360 220 305 200"
          stroke="url(#grad2)"
          stroke-width="1"
          opacity="0.3"
          fill="none"
        />
        <path
          d="M260 130 Q200 160 230 155"
          stroke="#60a5fa"
          stroke-width="0.8"
          opacity="0.3"
          fill="none"
        />
        <path
          d="M210 270 Q180 230 215 195"
          stroke="url(#grad2)"
          stroke-width="1"
          opacity="0.3"
          fill="none"
        />
        <path
          d="M290 150 Q330 220 305 200"
          stroke="#60a5fa"
          stroke-width="0.8"
          opacity="0.3"
          fill="none"
        />

        <!-- 左侧数据方块（AI芯片） -->
        <g
          transform="translate(50, 130)"
          filter="url(#glow)"
        >
          <rect
            x="0"
            y="0"
            width="52"
            height="52"
            rx="6"
            stroke="url(#grad1)"
            stroke-width="1.2"
            fill="rgba(0,229,255,0.04)"
          />
          <rect
            x="8"
            y="8"
            width="16"
            height="16"
            rx="3"
            fill="rgba(0,229,255,0.15)"
            stroke="#00e5ff"
            stroke-width="0.6"
          />
          <rect
            x="28"
            y="8"
            width="16"
            height="16"
            rx="3"
            fill="rgba(59,130,246,0.12)"
            stroke="#3b82f6"
            stroke-width="0.6"
          />
          <rect
            x="8"
            y="28"
            width="16"
            height="16"
            rx="3"
            fill="rgba(99,102,241,0.12)"
            stroke="#6366f1"
            stroke-width="0.6"
          />
          <rect
            x="28"
            y="28"
            width="16"
            height="16"
            rx="3"
            fill="rgba(0,229,255,0.1)"
            stroke="#00e5ff"
            stroke-width="0.6"
          />
          <!-- 数据点 -->
          <circle
            cx="16"
            cy="16"
            r="1.5"
            fill="#00e5ff"
          />
          <circle
            cx="36"
            cy="16"
            r="1.5"
            fill="#3b82f6"
          />
          <circle
            cx="16"
            cy="36"
            r="1.5"
            fill="#6366f1"
          />
          <circle
            cx="36"
            cy="36"
            r="1.5"
            fill="#00e5ff"
          />
        </g>

        <!-- 右侧数据方块（AI芯片） -->
        <g
          transform="translate(418, 140)"
          filter="url(#glow)"
        >
          <rect
            x="0"
            y="0"
            width="52"
            height="52"
            rx="6"
            stroke="url(#grad2)"
            stroke-width="1.2"
            fill="rgba(99,102,241,0.04)"
          />
          <rect
            x="6"
            y="6"
            width="18"
            height="18"
            rx="3"
            fill="rgba(99,102,241,0.12)"
            stroke="#6366f1"
            stroke-width="0.6"
          />
          <rect
            x="28"
            y="6"
            width="18"
            height="18"
            rx="3"
            fill="rgba(0,229,255,0.12)"
            stroke="#00e5ff"
            stroke-width="0.6"
          />
          <rect
            x="6"
            y="28"
            width="18"
            height="18"
            rx="3"
            fill="rgba(59,130,246,0.1)"
            stroke="#3b82f6"
            stroke-width="0.6"
          />
          <rect
            x="28"
            y="28"
            width="18"
            height="18"
            rx="3"
            fill="rgba(0,229,255,0.08)"
            stroke="#00e5ff"
            stroke-width="0.6"
          />
          <circle
            cx="15"
            cy="15"
            r="1.5"
            fill="#6366f1"
          />
          <circle
            cx="37"
            cy="15"
            r="1.5"
            fill="#00e5ff"
          />
          <circle
            cx="15"
            cy="37"
            r="1.5"
            fill="#3b82f6"
          />
          <circle
            cx="37"
            cy="37"
            r="1.5"
            fill="#00e5ff"
          />
        </g>

        <!-- 连线：芯片→球体 -->
        <path
          d="M102 156 Q150 170 190 185"
          stroke="#00e5ff"
          stroke-width="1"
          opacity="0.3"
          stroke-dasharray="3 3"
        />
        <path
          d="M418 166 Q380 170 330 180"
          stroke="#6366f1"
          stroke-width="1"
          opacity="0.3"
          stroke-dasharray="3 3"
        />

        <!-- 底部数据流扫描线 -->
        <g opacity="0.15">
          <line
            x1="80"
            y1="310"
            x2="440"
            y2="310"
            stroke="url(#grad1)"
            stroke-width="0.8"
          />
          <line
            x1="80"
            y1="318"
            x2="440"
            y2="318"
            stroke="#00e5ff"
            stroke-width="0.4"
            stroke-dasharray="2 6"
          />
          <line
            x1="80"
            y1="326"
            x2="440"
            y2="326"
            stroke="#3b82f6"
            stroke-width="0.6"
            stroke-dasharray="8 4"
          />
          <line
            x1="80"
            y1="334"
            x2="440"
            y2="334"
            stroke="#6366f1"
            stroke-width="0.3"
            stroke-dasharray="1 10"
          />
        </g>

        <!-- 漂浮粒子 -->
        <circle
          cx="120"
          cy="100"
          r="1"
          fill="#00e5ff"
          opacity="0.5"
        >
          <animate
            attributeName="opacity"
            values="0.5;0.1;0.5"
            dur="3s"
            repeatCount="indefinite"
          />
        </circle>
        <circle
          cx="400"
          cy="280"
          r="1"
          fill="#3b82f6"
          opacity="0.4"
        >
          <animate
            attributeName="opacity"
            values="0.4;0.1;0.4"
            dur="2.8s"
            repeatCount="indefinite"
          />
        </circle>
        <circle
          cx="350"
          cy="100"
          r="1.2"
          fill="#6366f1"
          opacity="0.3"
        >
          <animate
            attributeName="opacity"
            values="0.3;0.1;0.3"
            dur="3.5s"
            repeatCount="indefinite"
          />
        </circle>
      </svg>
    </div>

    <div class="brand-text">
      <h1>{{ title }}</h1>
      <p>{{ subtitle }}</p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '全球供应链 AI 赋能平台' },
  subtitle: { type: String, default: '' },
})
</script>

<style scoped>
.brand-panel {
  flex: 0 0 50vw;
  background: linear-gradient(160deg, #060d1a 0%, #0a1528 30%, #0d1d38 60%, #091220 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

/* 背景网格 */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(59, 130, 246, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 130, 246, 0.04) 1px, transparent 1px);
  background-size: 40px 40px;
  z-index: 0;
}

/* 光晕 */
.glow {
  position: absolute;
  border-radius: 50%;
  z-index: 0;
}

.glow-1 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(0, 229, 255, 0.08) 0%, transparent 70%);
  top: -100px;
  left: 10%;
}

.glow-2 {
  width: 350px;
  height: 350px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.06) 0%, transparent 70%);
  bottom: -80px;
  right: 5%;
}

.brand-panel::after {
  content: '';
  position: absolute;
  right: -60px;
  top: 0;
  bottom: 0;
  width: 120px;
  background: linear-gradient(to right, transparent 0%, rgba(0, 0, 0, 0.08) 50%, rgba(0, 0, 0, 0.12) 100%);
  z-index: 2;
  pointer-events: none;
}

.brand-illustration {
  position: relative;
  z-index: 1;
  margin-bottom: 20px;
}

.brand-illustration svg {
  width: 680px;
  height: auto;
}

.brand-text {
  position: absolute;
  bottom: 60px;
  left: 0;
  right: 0;
  z-index: 1;
  text-align: center;
  padding: 0 40px;
}

.brand-text h1 {
  font-size: 32px;
  font-weight: 800;
  color: #ffffff;
  margin-bottom: 12px;
  letter-spacing: 2px;
  line-height: 1.4;
}

.brand-text p {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.7);
}

@media (max-width: 1180px) {
  .brand-panel {
    display: none;
  }
}
</style>
