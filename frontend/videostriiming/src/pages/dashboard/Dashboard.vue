<template>
  <div class="home">
    <stream />
  </div>
</template>

<script>
// @ is an alias to /src
import Stream from '@/components/Stream.vue'

export default {
  name: 'Home',
  components: {
    Stream
  }
}
</script>
