# Agent Grid UI Design Prompt

> 高精准度提示词工程 - 用于 AI 绘图 Agent 或 UI 辅助设计工具

---

## Role Definition

```
你是一位顶级 UI/UX 视觉设计师，擅长 Apple 风格的极简主义与现代科技感（Glassmorphism & Neumorphism）的完美融合。你对中文字体的排版美学有近乎苛刻的要求。
```

---

## Task Background

```
重塑一个名为"Agent Grid"的 AI 控制面板。

当前问题：
- 页面过于平铺，缺乏视觉焦点
- 视觉层级混乱，呼吸感不足
- 需要将特定文案融入页面并进行彻底的视觉升级

设计约束：
仅限使用以下色系：
- 主色：橙色 (Orange #FF6B35)、黄色 (Yellow #FFD166)
- 辅色：白色、暖白 (Off-white #F8F6F4)
- 中性色：深灰 (#2D3436)、浅灰 (#DFE6E9)、黑色
```

---

## Detailed Instructions

### 1. Hero Section (核心区域)

```
移除原有的 "SYSTEM PULSE" 杂乱布局，重建视觉焦点：

┌─────────────────────────────────────────────────────┐
│  ┌──────────┐                                       │
│  │ SYSTEM   │  ← 胶囊型小标签                       │
│  │ PULSE    │    背景：半透明白色                    │
│  └──────────┘    文字：深橙色                        │
│                    边框：1px 白色微光                 │
│                                                     │
│  把「模型、文件、知识和数据」                        │
│  压缩到一块控制面板里。                              │
│  ↑ 主标题                                           │
│    字体：加粗无衬线体                                │
│    颜色：深橙色 (#FF6B35) → 明黄色 (#FFD166) 微渐变  │
│    字重：700                                        │
│                                                     │
│  统一监控 AI Agent、数据、任务与接入能力             │
│  ↑ 副标题                                           │
│    字体：无衬线体                                    │
│    颜色：中性深灰 (#636E72)                         │
│    字重：400                                        │
│    letter-spacing: 0.05em                           │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 2. Visual Style (视觉风格)

```
┌─────────────────────────────────────────────────────┐
│ BACKGROUND                                          │
│                                                     │
│ 颜色：暖白色 (Off-white #F8F6F4)                    │
│ 纹理：极细微的布料纹理或磨砂感                       │
│ 实现：CSS noise 或 subtle gradient                  │
│                                                     │
├─────────────────────────────────────────────────────┤
│ CARD MATERIAL (磨砂玻璃材质)                        │
│                                                     │
│ background: rgba(255, 255, 255, 0.7)               │
│ backdrop-filter: blur(10px)                        │
│ border: 1px solid rgba(255, 255, 255, 0.8)         │
│ box-shadow:                                         │
│   0 4px 6px rgba(0, 0, 0, 0.05),                   │
│   0 10px 20px rgba(0, 0, 0, 0.08)                  │
│ border-radius: 16px                                 │
│                                                     │
│ 悬浮感：                                            │
│ transform: translateY(-2px) on hover               │
│ box-shadow 增强深度                                 │
│                                                     │
├─────────────────────────────────────────────────────┤
│ ICONS                                               │
│                                                     │
│ 禁止：蓝色、绿色图标                                │
│                                                     │
│ 选项A：橙黄色系单色线框图标                         │
│   颜色：#FF6B35 或 #FFD166                          │
│   stroke-width: 1.5px                               │
│                                                     │
│ 选项B：琥珀质感 3D 拟物图标                         │
│   渐变：linear-gradient(135deg, #FFD166, #FF6B35)  │
│   微阴影增加立体感                                  │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 3. Layout & Typography (布局与排版)

```
┌─────────────────────────────────────────────────────┐
│ NEGATIVE SPACE (负空间)                             │
│                                                     │
│ 目标：让呼吸感代替拥挤感                            │
│                                                     │
│ 卡片间距：24px - 32px                               │
│ 内边距：20px - 24px                                 │
│ 模块间距：48px                                      │
│                                                     │
├─────────────────────────────────────────────────────┤
│ ALIGNMENT                                           │
│                                                     │
│ 左侧导航栏 ║ 中心内容区                             │
│     ↑      ║    ↑                                   │
│   严格    ║  严格                                   │
│   对齐    ║  对齐                                   │
│                                                     │
│ 所有元素遵循 8px grid system                        │
│                                                     │
├─────────────────────────────────────────────────────┤
│ READING LOGIC                                       │
│                                                     │
│ ┌─────────────┬─────────────┐                       │
│ │ 重点        │    数据     │                       │
│ │ (左上)      │   (右下)    │                       │
│ │             │             │                       │
│ │ 高明度      │   低明度    │                       │
│ │ 吸引注意    │   信息展示  │                       │
│ └─────────────┴─────────────┘                       │
│                                                     │
│ 通过明度而非色相来区分功能                          │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 4. Typography Specifications

```
┌─────────────────────────────────────────────────────┐
│ FONT FAMILY                                         │
│                                                     │
│ 首选：苹方 (PingFang SC)                            │
│ 备选：思源黑体 (Source Han Sans / Noto Sans SC)    │
│ 英文：SF Pro Display                                │
│                                                     │
│ 禁止：宋体、生硬黑体、系统默认字体                  │
│                                                     │
├─────────────────────────────────────────────────────┤
│ FONT WEIGHT MAPPING                                 │
│                                                     │
│ ┌─────────────┬──────────┬────────────────────────┐│
│ │ 层级        │ 字重     │ 用途                   ││
│ ├─────────────┼──────────┼────────────────────────┤│
│ │ Display     │ 700      │ 主标题、核心数字       ││
│ │ Heading     │ 600      │ 模块标题               ││
│ │ Body        │ 400      │ 正文、描述             ││
│ │ Caption     │ 300      │ 辅助说明、时间戳       ││
│ └─────────────┴──────────┴────────────────────────┘│
│                                                     │
├─────────────────────────────────────────────────────┤
│ SIZE SCALE                                          │
│                                                     │
│ Display:  32px - 48px                               │
│ Heading:  20px - 24px                               │
│ Body:     14px - 16px                               │
│ Caption:  12px                                      │
│                                                     │
│ Line-height: 1.5 - 1.6                              │
│ Letter-spacing: 标题 0.02em, 正文 normal            │
│                                                     │
└─────────────────────────────────────────────────────┘
```

---

## Constraints (去 AI 味核心)

```
╔═══════════════════════════════════════════════════════╗
║                    STRICT PROHIBITIONS                ║
╠═══════════════════════════════════════════════════════╣
║                                                       ║
║  ❌ 蓝绿渐变 (Blue-Green Gradient)                   ║
║  ❌ 发光的网格线 (Glowing Grid Lines)                ║
║  ❌ 复杂的电路纹理 (Complex Circuit Patterns)        ║
║  ❌ 粒子效果 (Particle Effects)                      ║
║  ❌ 过度华丽的装饰 (Over-ornate Decorations)         ║
║  ❌ 科幻感光晕 (Sci-fi Glow Effects)                 ║
║  ❌ 默认宋体 (Default Songti/Serif)                  ║
║  ❌ 生硬黑体 (Stiff Heiti)                           ║
║                                                       ║
╚═══════════════════════════════════════════════════════╝
```

---

## Color Palette Reference

```css
/* Primary - Orange/Yellow */
--color-primary-orange: #FF6B35;
--color-primary-yellow: #FFD166;
--color-gradient-hero: linear-gradient(135deg, #FF6B35, #FFD166);

/* Secondary - Amber Tones */
--color-amber-light: #FFECB3;
--color-amber-medium: #FFB74D;
--color-amber-dark: #F57C00;

/* Neutral - White/Gray */
--color-off-white: #F8F6F4;
--color-white-glass: rgba(255, 255, 255, 0.7);
--color-gray-light: #DFE6E9;
--color-gray-medium: #636E72;
--color-gray-dark: #2D3436;
--color-black: #1A1A1A;

/* Semantic */
--color-text-primary: #2D3436;
--color-text-secondary: #636E72;
--color-text-caption: #B2BEC3;
--color-border-glass: rgba(255, 255, 255, 0.8);
--color-shadow-subtle: rgba(0, 0, 0, 0.05);
--color-shadow-medium: rgba(0, 0, 0, 0.08);
```

---

## Example Output Structure

```
Agent Grid Dashboard
├── Header (导航栏)
│   ├── Logo (橙黄色渐变)
│   ├── Nav Items (中性灰)
│   └── User Avatar (磨砂玻璃容器)
│
├── Hero Section
│   ├── Capsule Tag: "SYSTEM PULSE"
│   ├── Main Title: 把「模型、文件、知识和数据」压缩到一块控制面板里。
│   ├── Subtitle: 统一监控 AI Agent、数据、任务与接入能力
│   └── CTA Button (橙黄色填充)
│
├── Grid Cards
│   ├── Files Card (磨砂玻璃)
│   │   ├── Icon (琥珀线框)
│   │   ├── Title + Count
│   │   └── Mini Chart
│   │
│   ├── Model Card (磨砂玻璃)
│   │   ├── Icon (琥珀线框)
│   │   ├── Status Indicator (橙/黄)
│   │   └── Parameters Display
│   │
│   ├── RAG Card (磨砂玻璃)
│   │   ├── Icon (琥珀线框)
│   │   ├── Knowledge Base Stats
│   │   └── Retrieval Metrics
│   │
│   ├── Tasks Card (磨砂玻璃)
│   │   ├── Icon (琥珀线框)
│   │   ├── Active Tasks List
│   │   └── Progress Indicators
│
└── Footer
    └── Status Bar (中性灰)
    └── Timestamp (Caption字重)
```

---

## Usage

将此 Prompt 复制并输入给：

1. **AI 图像生成工具**：Midjourney、DALL-E、Stable Diffusion
2. **UI 设计辅助**：Claude Code /frontend-design skill
3. **前端开发 Agent**：用于生成 CSS 样式代码

---

## Version

- Version: 1.0
- Created: 2026-04-16
- Style: Apple Minimalist + Glassmorphism
- Color: Orange/Yellow Theme