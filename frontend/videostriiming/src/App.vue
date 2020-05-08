<template>
  <div id="app">
    <page-layout>
      <router-view/>
    </page-layout>
  </div>
</template>

<script>
import HelloWorld from './components/HelloWorld.vue'
import pageLayout from "./pages/layout/Pagelayout.vue";


export default {
  name: 'app',
  components: {
    HelloWorld, pageLayout

  }

}
</script>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
}
</style>
