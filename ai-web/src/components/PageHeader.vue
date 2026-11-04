<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>()
</script>

<template>
  <header class="page-header">
    <div class="page-header__copy">
      <div v-if="eyebrow" class="page-header__eyebrow">{{ eyebrow }}</div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;

  &__copy {
    min-width: 0;

    h1 {
      margin: 0;
      color: var(--text-primary);
      font-size: 26px;
      line-height: 1.25;
      font-weight: 700;
      letter-spacing: -0.02em;
    }

    p {
      margin: 8px 0 0;
      color: var(--text-secondary);
      font-size: 13.5px;
      line-height: 1.6;
    }
  }

  &__eyebrow {
    margin-bottom: 8px;
    color: var(--brand-600);
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.12em;
    text-transform: uppercase;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex: 0 0 auto;
  }
}
</style>
