<template>
  <n-result status="403" title="此功能仅供部署维护使用" description="当前账号无法执行项目扫描与维护操作，请从左侧导航选择其他功能。">
    <template #footer><n-button @click="$router.push('/')">返回首页</n-button></template>
  </n-result>
</template>
