<template>
    <div class="PageLayout">
        <navbar class="fixed"/>
        <main class="PageLayout__content">
            <slot/>
        </main>
        <footer class="PageLayout__footer">
            <p class="float-right pr-5">Authors: prroos ja joveis</p>
        </footer>
    </div>
</template>

<script>
    import navbar from "@/components/Navbar.vue"

    export default {
        name: 'page-layout',
        components: {
            navbar
        }

    }
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
    .PageLayout__footer {
        position: absolute;
        bottom: 0;
        width: 100%;
    }

    #nav {
        padding: 30px;
    }

    #nav a {
        font-weight: bold;
        color: #2c3e50;
    }

    #nav a.router-link-exact-active {
        color: #42b983;
    }
</style>
