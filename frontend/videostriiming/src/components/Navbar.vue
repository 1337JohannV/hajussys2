<template>
  <nav class="navbar navbar-light bg-light">
    <a class="navbar-brand" v-on:click="navigate('Dashboard')">Dashboard</a>
    <ul class="navbar-nav">
      <li class="nav-item active">
          <a class="nav-link" v-on:click="navigate('About')">About</a>
      </li>
    </ul>
  </nav>
</template>

<script>
export default {
  name: 'navbar',
  methods: {
    navigate(routeName){
      this.$router.push({name: routeName}).catch(error => {
        if (error.name != "NavigationDuplicated") {
          console.error(error);
        }
      })
    }
  }
}
</script>

<style scoped>
</style>
