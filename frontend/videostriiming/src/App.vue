<template>
  <div id="app">
    <page-layout>
      <router-view/>
    </page-layout>
  </div>
</template>

<script>
import pageLayout from "./pages/layout/Pagelayout.vue";


export default {
  name: 'app',
  components: {
    pageLayout
  }

}
</script>

<style>
</style>
