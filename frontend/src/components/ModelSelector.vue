<template>
  <!-- MVP 阶段由 Python Graph 统一负责模型选择，前端暂不提供模型切换。 -->
</template>

<script setup lang="ts">
/**
 * MVP 阶段的模型选择占位组件。
 *
 * 当前聊天链路由 Python Graph 统一决定使用的模型，因此这里不再请求
 * `/api/model/available`，避免前端依赖尚未提供的 Java 模型选择接口。
 */
</script>
