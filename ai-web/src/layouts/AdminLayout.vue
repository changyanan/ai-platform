<script setup lang="ts">
import {
  ArrowDown,
  ChatDotRound,
  Collection,
  Cpu,
  DataLine,
  Document,
  Expand,
  Fold,
  Location,
  Lock,
  MagicStick,
  Picture,
  SetUp,
  SwitchButton,
  UserFilled,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import BrandLogo from '@/components/BrandLogo.vue'
import { findNavigationByPath, navigationItems } from '@/config/navigation'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()

const iconMap: Record<string, Component> = {
  ChatDotRound,
  Cpu,
  MagicStick,
  SetUp,
  Document,
  Collection,
  Picture,
  Location,
  DataLine,
  UserFilled,
}

const visibleNavigation = computed(() =>
  navigationItems.filter((item) => !item.adminOnly || authStore.isAdmin),
)

const activeMenu = computed(() => {
  const item = findNavigationByPath(route.path)
  return item?.path ?? '/workbench'
})

const currentTitle = computed(
  () => route.meta.title ?? findNavigationByPath(route.path)?.title ?? '工作区',
)

const currentDescription = computed(
  () => route.meta.description ?? findNavigationByPath(route.path)?.description ?? '',
)

const userInitial = computed(() => {
  const name = authStore.user?.displayName || authStore.user?.username || 'A'
  return name.trim().charAt(0).toUpperCase()
})

const roleLabel = computed(() => (authStore.isAdmin ? '管理员' : '普通用户'))

async function handleUserCommand(command: string) {
  if (command === 'password') {
    await router.push('/change-password')
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('退出后需要重新登录，确定退出当前账号吗？', '退出登录', {
        confirmButtonText: '退出登录',
        cancelButtonText: '取消',
        type: 'warning',
        customClass: 'logout-confirm',
      })
    } catch {
      return
    }
    await authStore.logout()
    ElMessage.success('已安全退出')
    await router.replace('/login')
  }
}

function handleResize() {
  if (window.innerWidth < 1180 && !appStore.sidebarCollapsed) {
    appStore.setSidebarCollapsed(true)
  }
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="admin-shell" :class="{ 'is-collapsed': appStore.sidebarCollapsed }">
    <aside class="sidebar">
      <div class="sidebar__glow"></div>
      <div class="sidebar__brand">
        <BrandLogo :compact="appStore.sidebarCollapsed" tone="light" />
      </div>

      <div v-if="!appStore.sidebarCollapsed" class="sidebar__section-label">工作空间</div>

      <el-scrollbar class="sidebar__scroll">
        <el-menu
          :default-active="activeMenu"
          :collapse="appStore.sidebarCollapsed"
          :collapse-transition="false"
          router
          class="sidebar__menu"
        >
          <el-menu-item v-for="item in visibleNavigation" :key="item.path" :index="item.path">
            <el-icon :size="18">
              <component :is="iconMap[item.icon]" />
            </el-icon>
            <template #title>
              <span class="menu-title">{{ item.title }}</span>
            </template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>

      <div class="sidebar__footer">
        <div v-if="!appStore.sidebarCollapsed" class="environment-card">
          <div class="environment-card__top">
            <span class="status-dot"></span>
            <span>生产环境</span>
          </div>
          <p>独立 AI 平台 · 数据自主维护</p>
          <div class="environment-card__version">AI Platform v1.0.0</div>
        </div>
        <div v-else class="environment-dot" title="生产环境">
          <span class="status-dot"></span>
        </div>
      </div>
    </aside>

    <div class="main-shell">
      <header class="topbar">
        <div class="topbar__left">
          <button
            class="icon-button"
            type="button"
            :aria-label="appStore.sidebarCollapsed ? '展开菜单' : '收起菜单'"
            @click="appStore.toggleSidebar()"
          >
            <el-icon :size="18">
              <Expand v-if="appStore.sidebarCollapsed" />
              <Fold v-else />
            </el-icon>
          </button>
          <div class="topbar__divider"></div>
          <div class="breadcrumb">
            <span class="breadcrumb__root">AI 平台</span>
            <span class="breadcrumb__slash">/</span>
            <span class="breadcrumb__current">{{ currentTitle }}</span>
          </div>
          <span v-if="currentDescription" class="topbar__description">{{ currentDescription }}</span>
        </div>

        <div class="topbar__right">
          <el-tooltip content="修改密码" placement="bottom">
            <button class="icon-button icon-button--subtle" type="button" @click="router.push('/change-password')">
              <el-icon :size="17"><Lock /></el-icon>
            </button>
          </el-tooltip>

          <div class="topbar__separator"></div>

          <el-dropdown trigger="click" @command="handleUserCommand">
            <button class="user-trigger" type="button">
              <span class="user-avatar">{{ userInitial }}</span>
              <span class="user-meta">
                <strong>{{ authStore.displayName }}</strong>
                <small>{{ roleLabel }}</small>
              </span>
              <el-icon class="user-caret" :size="12"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">
                  <el-icon><Lock /></el-icon>
                  修改密码
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component: ViewComponent }">
          <transition name="fade-slide" mode="out-in">
            <component :is="ViewComponent" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
$sidebar-width: 248px;
$sidebar-collapsed-width: 76px;

.admin-shell {
  min-height: 100vh;
  background: var(--bg-page);
}

.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  width: $sidebar-width;
  color: #cbd5e1;
  background:
    radial-gradient(120% 70% at 0% 0%, rgba(79, 70, 229, 0.22), transparent 55%),
    radial-gradient(80% 50% at 100% 100%, rgba(6, 182, 212, 0.1), transparent 60%),
    linear-gradient(180deg, #0b1220 0%, #0d1526 58%, #0a101c 100%);
  border-right: 1px solid rgba(148, 163, 184, 0.08);
  transition: width 0.22s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    background-image: linear-gradient(rgba(148, 163, 184, 0.025) 1px, transparent 1px),
      linear-gradient(90deg, rgba(148, 163, 184, 0.025) 1px, transparent 1px);
    background-size: 40px 40px;
    mask-image: linear-gradient(to bottom, black, transparent 75%);
  }

  &__glow {
    position: absolute;
    top: -80px;
    left: -80px;
    width: 260px;
    height: 260px;
    border-radius: 50%;
    background: rgba(99, 102, 241, 0.14);
    filter: blur(60px);
    pointer-events: none;
  }

  &__brand {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    height: 74px;
    padding: 0 22px;
    border-bottom: 1px solid rgba(148, 163, 184, 0.08);
    flex: 0 0 auto;
  }

  &__section-label {
    position: relative;
    z-index: 1;
    padding: 22px 24px 8px;
    color: #64748b;
    font-size: 10px;
    font-weight: 700;
    letter-spacing: 0.16em;
    text-transform: uppercase;
  }

  &__scroll {
    position: relative;
    z-index: 1;
    flex: 1;
    min-height: 0;
    padding: 6px 0 12px;
  }

  &__menu {
    border-right: 0;
    background: transparent;
  }

  &__footer {
    position: relative;
    z-index: 1;
    padding: 14px;
    flex: 0 0 auto;
    border-top: 1px solid rgba(148, 163, 184, 0.08);
  }
}

.menu-title {
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.sidebar__menu :deep(.el-menu-item) {
  position: relative;
  height: 44px;
  margin: 3px 12px;
  padding: 0 14px !important;
  border-radius: 9px;
  color: #94a3b8;
  line-height: 44px;
  transition: color 0.18s ease, background 0.18s ease;

  .el-icon {
    margin-right: 11px;
  }

  &:hover {
    color: #e2e8f0;
    background: rgba(148, 163, 184, 0.09);
  }

  &.is-active {
    color: #ffffff;
    background: linear-gradient(90deg, rgba(99, 102, 241, 0.26), rgba(34, 211, 238, 0.07));
    box-shadow: inset 0 0 0 1px rgba(129, 140, 248, 0.2);

    &::before {
      content: '';
      position: absolute;
      left: -12px;
      top: 12px;
      bottom: 12px;
      width: 3px;
      border-radius: 0 3px 3px 0;
      background: linear-gradient(180deg, var(--brand-300), var(--cyan-400));
      box-shadow: 0 0 12px rgba(129, 140, 248, 0.55);
    }

    .el-icon {
      color: #c7d2fe;
    }
  }
}

.sidebar__menu :deep(.el-menu--collapse) {
  width: 100%;

  .el-menu-item {
    justify-content: center;
    padding: 0 !important;

    .el-icon {
      margin: 0;
    }

    &.is-active::before {
      left: 0;
    }
  }
}

.environment-card {
  padding: 13px 14px;
  border-radius: 11px;
  background: rgba(148, 163, 184, 0.06);
  border: 1px solid rgba(148, 163, 184, 0.08);

  &__top {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #dbeafe;
    font-size: 12px;
    font-weight: 600;
  }

  p {
    margin: 7px 0 10px;
    color: #64748b;
    font-size: 10.5px;
    line-height: 1.55;
  }

  &__version {
    padding-top: 9px;
    border-top: 1px solid rgba(148, 163, 184, 0.08);
    color: #475569;
    font-size: 9.5px;
    font-family: var(--font-mono);
    letter-spacing: 0.03em;
  }
}

.environment-dot {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  margin: 0 auto;
  border-radius: 10px;
  background: rgba(16, 185, 129, 0.08);
}

.main-shell {
  min-height: 100vh;
  margin-left: $sidebar-width;
  transition: margin-left 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.is-collapsed {
  .sidebar {
    width: $sidebar-collapsed-width;
  }

  .sidebar__brand {
    justify-content: center;
    padding: 0;
  }

  .main-shell {
    margin-left: $sidebar-collapsed-width;
  }
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 66px;
  padding: 0 30px;
  background: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
  backdrop-filter: blur(18px);

  &__left {
    display: flex;
    align-items: center;
    min-width: 0;
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 10px;
    flex: 0 0 auto;
  }

  &__divider {
    width: 1px;
    height: 20px;
    margin: 0 16px 0 14px;
    background: var(--border-strong);
  }

  &__description {
    margin-left: 18px;
    color: var(--text-tertiary);
    font-size: 12px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__separator {
    width: 1px;
    height: 22px;
    margin: 0 4px;
    background: var(--border-soft);
  }
}

.icon-button {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  color: #64748b;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover {
    color: var(--brand-600);
    background: var(--brand-50);
    border-color: var(--brand-100);
  }

  &--subtle {
    width: 32px;
    height: 32px;
  }
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  white-space: nowrap;

  &__root {
    color: var(--text-tertiary);
    font-weight: 500;
  }

  &__slash {
    color: #cbd5e1;
  }

  &__current {
    color: var(--text-primary);
    font-weight: 650;
  }
}

.demo-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 25px;
  padding: 0 9px;
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 999px;
  color: var(--brand-700);
  background: var(--brand-50);
  font-size: 9.5px;
  font-weight: 800;
  letter-spacing: 0.1em;

  &__dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--brand-500);
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
  }
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 5px 8px 5px 5px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  transition: all 0.18s ease;

  &:hover {
    background: #f8fafc;
    border-color: var(--border-soft);
  }
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 33px;
  height: 33px;
  border-radius: 9px;
  color: #fff;
  background: linear-gradient(135deg, var(--brand-500), #7c3aed 60%, #06b6d4);
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 4px 10px rgba(79, 70, 229, 0.2);
}

.user-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 120px;

  strong {
    overflow: hidden;
    max-width: 120px;
    color: var(--text-primary);
    font-size: 12.5px;
    font-weight: 650;
    line-height: 1.35;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  small {
    margin-top: 1px;
    color: var(--text-tertiary);
    font-size: 10px;
    line-height: 1.3;
  }
}

.user-caret {
  margin-left: 2px;
  color: var(--text-tertiary);
}

.content {
  padding: 28px 30px 38px;
  min-height: calc(100vh - 66px);
}

@media (max-width: 1280px) {
  .topbar {
    padding: 0 22px;

    &__description {
      display: none;
    }
  }

  .content {
    padding: 24px 22px 32px;
  }
}
</style>
