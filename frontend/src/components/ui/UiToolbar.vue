<template>
  <!-- UI 工具栏布局组件：左侧默认槽位与右侧 extra 槽位组成的工具栏 -->
  <div class="ui-toolbar">
    <div class="ui-toolbar__group">
      <slot />
    </div>
    <div v-if="$slots.extra" class="ui-toolbar__group">
      <slot name="extra" />
    </div>
  </div>
</template>
