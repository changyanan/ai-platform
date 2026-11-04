<script setup lang="ts">
withDefaults(
  defineProps<{
    compact?: boolean
    tone?: 'light' | 'dark'
  }>(),
  {
    compact: false,
    tone: 'light',
  },
)
</script>

<template>
  <div class="brand" :class="[`brand--${tone}`, { 'brand--compact': compact }]">
    <div class="brand__mark">
      <svg viewBox="0 0 48 48" aria-hidden="true">
        <defs>
          <linearGradient id="brand-gradient" x1="4" y1="4" x2="44" y2="44" gradientUnits="userSpaceOnUse">
            <stop stop-color="#A5B4FC" />
            <stop offset="0.55" stop-color="#818CF8" />
            <stop offset="1" stop-color="#22D3EE" />
          </linearGradient>
        </defs>
        <path
          d="M24 5.5L39.5 14.4V32.2L24 41.1L8.5 32.2V14.4L24 5.5Z"
          fill="url(#brand-gradient)"
        />
        <path d="M24 15L31.5 19.3V28L24 32.3L16.5 28V19.3L24 15Z" fill="#0B1220" />
        <circle cx="24" cy="23.65" r="3.2" fill="url(#brand-gradient)" />
      </svg>
    </div>
    <div v-if="!compact" class="brand__copy">
      <strong>AI 智能应用平台</strong>
      <span>ENTERPRISE INTELLIGENCE</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;

  &__mark {
    width: 40px;
    height: 40px;
    flex: 0 0 40px;
    border-radius: 12px;
    display: grid;
    place-items: center;
    background: rgba(99, 102, 241, 0.12);
    border: 1px solid rgba(129, 140, 248, 0.26);
    box-shadow: 0 8px 22px rgba(79, 70, 229, 0.16);

    svg {
      width: 30px;
      height: 30px;
    }
  }

  &__copy {
    display: flex;
    flex-direction: column;
    min-width: 0;

    strong {
      font-size: 15px;
      line-height: 1.35;
      letter-spacing: 0.01em;
      white-space: nowrap;
    }

    span {
      margin-top: 3px;
      font-size: 8.5px;
      letter-spacing: 0.16em;
      font-weight: 600;
      opacity: 0.45;
      white-space: nowrap;
    }
  }

  &--light {
    color: #f8fafc;
  }

  &--dark {
    color: #0f172a;

    .brand__mark {
      background: rgba(79, 70, 229, 0.08);
      border-color: rgba(79, 70, 229, 0.16);
    }
  }

  &--compact {
    justify-content: center;
  }
}
</style>
