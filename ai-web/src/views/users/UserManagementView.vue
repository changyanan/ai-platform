<script setup lang="ts">
import {
  CirclePlus,
  Key,
  Refresh,
  Search,
  User as UserIcon,
  UserFilled,
} from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'

import PageHeader from '@/components/PageHeader.vue'
import {
  createUser,
  fetchUsers,
  resetUserPassword,
  updateUserStatus,
} from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import type { CreateUserPayload, UserQuery, UserRecord } from '@/types/api'
import { getErrorMessage } from '@/utils/http'

const authStore = useAuthStore()

const loading = ref(false)
const records = ref<UserRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const statusLoading = ref<Set<number>>(new Set())

const filters = reactive({
  keyword: '',
  role: '',
  enabled: '',
})

const createDialogVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<CreateUserPayload & { email: string }>({
  username: '',
  displayName: '',
  email: '',
  role: 'USER',
  password: '',
})

const resetDialogVisible = ref(false)
const resetLoading = ref(false)
const resetFormRef = ref<FormInstance>()
const resetTarget = ref<UserRecord | null>(null)
const resetForm = reactive({ newPassword: '' })

const activeFilters = computed(
  () =>
    Boolean(filters.keyword.trim()) || Boolean(filters.role) || filters.enabled !== '',
)

const createRules: FormRules = {
  username: [
    { required: true, message: '请输入登录名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{3,32}$/,
      message: '登录名需为 3-32 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  displayName: [
    { required: true, message: '请输入显示名', trigger: 'blur' },
    { max: 64, message: '显示名不能超过 64 个字符', trigger: 'blur' },
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 8, max: 64, message: '初始密码长度需为 8-64 位', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
      message: '初始密码必须同时包含字母和数字',
      trigger: 'blur',
    },
  ],
}

const resetRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '新密码长度需为 8-64 位', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
      message: '新密码必须同时包含字母和数字',
      trigger: 'blur',
    },
  ],
}

function buildQuery(): UserQuery {
  const query: UserQuery = {
    page: page.value,
    size: size.value,
  }
  if (filters.keyword.trim()) query.keyword = filters.keyword.trim()
  if (filters.role) query.role = filters.role
  if (filters.enabled !== '') query.enabled = filters.enabled === 'true'
  return query
}

async function loadUsers() {
  loading.value = true
  try {
    const result = await fetchUsers(buildQuery())
    records.value = result.records
    total.value = result.total
    if (result.records.length === 0 && result.total > 0 && page.value > 1) {
      page.value -= 1
      await loadUsers()
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  void loadUsers()
}

function handleResetFilters() {
  filters.keyword = ''
  filters.role = ''
  filters.enabled = ''
  page.value = 1
  void loadUsers()
}

function handlePageChange(nextPage: number) {
  page.value = nextPage
  void loadUsers()
}

function handleSizeChange(nextSize: number) {
  size.value = nextSize
  page.value = 1
  void loadUsers()
}

function handleSwitchChange(row: UserRecord, value: string | number | boolean) {
  void handleStatusChange(row, value)
}

async function handleStatusChange(row: UserRecord, value: string | number | boolean) {
  const nextEnabled = Boolean(value)
  if (row.enabled === nextEnabled) return

  if (!nextEnabled) {
    try {
      await ElMessageBox.confirm(
        `停用后「${row.displayName || row.username}」将立即无法登录，确定继续吗？`,
        '停用账号',
        {
          type: 'warning',
          confirmButtonText: '确认停用',
          cancelButtonText: '取消',
        },
      )
    } catch {
      return
    }
  }

  statusLoading.value = new Set(statusLoading.value).add(row.id)
  try {
    await updateUserStatus(row.id, nextEnabled)
    row.enabled = nextEnabled
    ElMessage.success(nextEnabled ? '账号已启用' : '账号已停用')
  } catch (error) {
    ElMessage.error(getErrorMessage(error, '账号状态更新失败'))
  } finally {
    const next = new Set(statusLoading.value)
    next.delete(row.id)
    statusLoading.value = next
  }
}

function resetCreateForm() {
  createForm.username = ''
  createForm.displayName = ''
  createForm.email = ''
  createForm.role = 'USER'
  createForm.password = ''
}

function openCreateDialog() {
  resetCreateForm()
  createDialogVisible.value = true
  void nextTick(() => createFormRef.value?.clearValidate())
}

function generatePassword() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
  const array = new Uint32Array(10)
  crypto.getRandomValues(array)
  const body = Array.from(array, (value) => chars[value % chars.length]).join('')
  createForm.password = `Ai${body}8`
  ElMessage.success('已生成符合复杂度要求的初始密码')
}

async function submitCreate() {
  if (!createFormRef.value || createLoading.value) return
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  createLoading.value = true
  try {
    await createUser({
      username: createForm.username.trim(),
      displayName: createForm.displayName.trim(),
      email: createForm.email.trim() || undefined,
      role: createForm.role,
      password: createForm.password,
    })
    ElMessage.success('用户创建成功，首次登录需修改初始密码')
    createDialogVisible.value = false
    page.value = 1
    await loadUsers()
  } finally {
    createLoading.value = false
  }
}

function openResetDialog(row: UserRecord) {
  resetTarget.value = row
  resetForm.newPassword = ''
  resetDialogVisible.value = true
  void nextTick(() => resetFormRef.value?.clearValidate())
}

async function submitResetPassword() {
  if (!resetFormRef.value || !resetTarget.value || resetLoading.value) return
  const valid = await resetFormRef.value.validate().catch(() => false)
  if (!valid) return

  resetLoading.value = true
  try {
    await resetUserPassword(resetTarget.value.id, {
      newPassword: resetForm.newPassword,
    })
    ElMessage.success('密码已重置，该用户下次登录时必须修改密码')
    resetDialogVisible.value = false
    await loadUsers()
  } finally {
    resetLoading.value = false
  }
}

function formatTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '从未登录'
}

function roleLabel(role: string): string {
  return role === 'ADMIN' ? '管理员' : '普通用户'
}

onMounted(() => {
  void loadUsers()
})
</script>

<template>
  <div class="page-shell">
    <PageHeader
      eyebrow="ACCESS CONTROL"
      title="用户管理"
      description="管理平台账号、角色权限与首次登录强制改密策略"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadUsers">刷新</el-button>
        <el-button type="primary" :icon="CirclePlus" @click="openCreateDialog">新增用户</el-button>
      </template>
    </PageHeader>

    <section class="filter-bar">
      <div class="filter-bar__group">
        <el-input
          v-model="filters.keyword"
          class="keyword-input"
          placeholder="搜索登录名、显示名或邮箱"
          :prefix-icon="Search"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-select v-model="filters.role" class="filter-select" placeholder="全部角色" clearable>
          <el-option label="管理员" value="ADMIN" />
          <el-option label="普通用户" value="USER" />
        </el-select>
        <el-select v-model="filters.enabled" class="filter-select" placeholder="全部状态" clearable>
          <el-option label="已启用" value="true" />
          <el-option label="已停用" value="false" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :disabled="!activeFilters" @click="handleResetFilters">重置</el-button>
      </div>
      <div class="filter-bar__meta">
        <el-icon><UserFilled /></el-icon>
        共 <strong>{{ total }}</strong> 个账号
      </div>
    </section>

    <section class="table-card">
      <div class="table-card__head">
        <div>
          <h2>账号列表</h2>
          <p>默认账号与新建账号均需在首次登录时修改密码</p>
        </div>
        <span class="table-card__policy">
          <span class="status-dot"></span>
          安全策略已启用
        </span>
      </div>

      <el-table v-loading="loading" :data="records" row-key="id" style="width: 100%">
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }: { row: UserRecord }">
            <div class="user-cell">
              <span class="user-cell__avatar" :class="{ 'is-admin': row.role === 'ADMIN' }">
                {{ (row.displayName || row.username).charAt(0).toUpperCase() }}
              </span>
              <div class="user-cell__copy">
                <strong>
                  {{ row.displayName }}
                  <span v-if="row.id === authStore.user?.id" class="self-tag">当前账号</span>
                </strong>
                <span>@{{ row.username }}<template v-if="row.email"> · {{ row.email }}</template></span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="角色" width="110">
          <template #default="{ row }: { row: UserRecord }">
            <el-tag :type="row.role === 'ADMIN' ? 'primary' : 'info'" effect="light">
              {{ roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="130">
          <template #default="{ row }: { row: UserRecord }">
            <div class="status-cell">
              <el-switch
                :model-value="row.enabled"
                :loading="statusLoading.has(row.id)"
                :disabled="row.id === authStore.user?.id"
                @change="handleSwitchChange(row, $event)"
              />
              <span :class="row.enabled ? 'is-enabled' : 'is-disabled'">
                {{ row.enabled ? '已启用' : '已停用' }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="密码状态" width="120">
          <template #default="{ row }: { row: UserRecord }">
            <span class="password-state" :class="{ 'needs-change': row.mustChangePassword }">
              <el-icon><Key /></el-icon>
              {{ row.mustChangePassword ? '待修改' : '已设置' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="最近登录" width="160">
          <template #default="{ row }: { row: UserRecord }">
            <span class="cell-muted">{{ formatTime(row.lastLoginAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }: { row: UserRecord }">
            <span class="cell-muted">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }: { row: UserRecord }">
            <el-button type="primary" link :icon="Key" @click="openResetDialog(row)">
              重置密码
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="empty-state">
            <span class="empty-state__icon"><el-icon :size="22"><UserIcon /></el-icon></span>
            <strong>没有符合条件的账号</strong>
            <p>{{ activeFilters ? '尝试调整筛选条件，或新增一个平台账号。' : '还没有平台账号，创建第一个管理员或普通用户。' }}</p>
            <el-button v-if="!activeFilters" type="primary" plain :icon="CirclePlus" @click="openCreateDialog">
              新增用户
            </el-button>
          </div>
        </template>
      </el-table>

      <div class="table-card__footer">
        <span class="table-card__hint">账号停用后，已签发的 JWT 会立即失效</span>
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="createDialogVisible"
      title="新增平台账号"
      width="520px"
      :close-on-click-modal="false"
      @closed="createFormRef?.clearValidate()"
    >
      <div class="dialog-intro">
        <span class="dialog-intro__icon"><el-icon><UserFilled /></el-icon></span>
        <div>
          <strong>创建后需首次登录修改密码</strong>
          <p>初始密码仅在此处设置一次，系统不会保存明文。</p>
        </div>
      </div>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <div class="form-grid">
          <el-form-item label="登录名" prop="username">
            <el-input v-model="createForm.username" placeholder="如 zhangsan" maxlength="32" />
          </el-form-item>
          <el-form-item label="显示名" prop="displayName">
            <el-input v-model="createForm.displayName" placeholder="如 张三" maxlength="64" />
          </el-form-item>
        </div>
        <el-form-item label="邮箱（可选）" prop="email">
          <el-input v-model="createForm.email" placeholder="name@company.com" maxlength="128" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="createForm.role">
            <el-radio-button value="USER">普通用户</el-radio-button>
            <el-radio-button value="ADMIN">管理员</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <div class="password-input-row">
            <el-input
              v-model="createForm.password"
              type="password"
              show-password
              placeholder="至少 8 位，包含字母和数字"
              autocomplete="new-password"
            />
            <el-button @click="generatePassword">生成</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">创建账号</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="resetDialogVisible"
      title="重置登录密码"
      width="460px"
      :close-on-click-modal="false"
      @closed="resetFormRef?.clearValidate()"
    >
      <div class="dialog-intro dialog-intro--warning">
        <span class="dialog-intro__icon"><el-icon><Key /></el-icon></span>
        <div>
          <strong>{{ resetTarget?.displayName || resetTarget?.username }}</strong>
          <p>重置后该账号所有会话立即失效，并要求下次登录修改密码。</p>
        </div>
      </div>
      <el-form ref="resetFormRef" :model="resetForm" :rules="resetRules" label-position="top">
        <el-form-item label="新的初始密码" prop="newPassword">
          <el-input
            v-model="resetForm.newPassword"
            type="password"
            show-password
            placeholder="至少 8 位，包含字母和数字"
            autocomplete="new-password"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetLoading" @click="submitResetPassword">
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 18px;
  margin-bottom: 16px;
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  background: #fff;
  box-shadow: var(--shadow-xs);

  &__group {
    display: flex;
    align-items: center;
    gap: 9px;
    flex-wrap: wrap;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: 5px;
    color: var(--text-tertiary);
    font-size: 11px;
    white-space: nowrap;

    .el-icon {
      color: #a5b4fc;
    }

    strong {
      color: #475569;
      font-weight: 650;
    }
  }
}

.keyword-input {
  width: 248px;
}

.filter-select {
  width: 130px;
}

.table-card {
  overflow: hidden;
  border: 1px solid var(--border-soft);
  border-radius: 13px;
  background: #fff;
  box-shadow: var(--shadow-xs);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 19px 22px;
    border-bottom: 1px solid #f1f5f9;

    h2 {
      margin: 0;
      color: var(--text-primary);
      font-size: 14px;
      font-weight: 650;
    }

    p {
      margin: 4px 0 0;
      color: var(--text-tertiary);
      font-size: 10.5px;
    }
  }

  &__policy {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    padding: 5px 9px;
    border: 1px solid rgba(16, 185, 129, 0.12);
    border-radius: 6px;
    color: #047857;
    background: #ecfdf5;
    font-size: 9.5px;
    font-weight: 600;

    .status-dot {
      width: 5px;
      height: 5px;
    }
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 16px 20px;
    border-top: 1px solid #f1f5f9;
  }

  &__hint {
    color: var(--text-tertiary);
    font-size: 9.5px;
  }
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 11px;

  &__avatar {
    display: grid;
    place-items: center;
    flex: 0 0 34px;
    width: 34px;
    height: 34px;
    border-radius: 9px;
    color: #0e7490;
    background: #ecfeff;
    font-size: 12px;
    font-weight: 700;

    &.is-admin {
      color: #4338ca;
      background: #eef2ff;
    }
  }

  &__copy {
    min-width: 0;

    strong {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #1e293b;
      font-size: 12.5px;
      font-weight: 600;
    }

    span {
      display: block;
      margin-top: 2px;
      overflow: hidden;
      color: #94a3b8;
      font-size: 10px;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
  }
}

.self-tag {
  display: inline-flex !important;
  margin: 0 !important;
  padding: 1px 5px;
  border-radius: 4px;
  color: var(--brand-600) !important;
  background: var(--brand-50);
  font-size: 8px !important;
  font-weight: 600;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  span {
    font-size: 10.5px;

    &.is-enabled {
      color: #059669;
    }

    &.is-disabled {
      color: #94a3b8;
    }
  }
}

.password-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #64748b;
  font-size: 10.5px;

  .el-icon {
    font-size: 12px;
  }

  &.needs-change {
    color: #b45309;
  }
}

.cell-muted {
  color: #7c8ba3;
  font-size: 10.5px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 54px 20px;

  &__icon {
    display: grid;
    place-items: center;
    width: 48px;
    height: 48px;
    border: 1px dashed #cbd5e1;
    border-radius: 13px;
    color: #94a3b8;
    background: #f8fafc;
  }

  strong {
    margin-top: 14px;
    color: #475569;
    font-size: 12.5px;
    font-weight: 600;
  }

  p {
    max-width: 320px;
    margin: 6px 0 15px;
    color: #94a3b8;
    font-size: 10.5px;
    line-height: 1.65;
    text-align: center;
  }
}

.dialog-intro {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  margin-bottom: 20px;
  padding: 12px 13px;
  border: 1px solid rgba(99, 102, 241, 0.1);
  border-radius: 9px;
  background: #f8faff;

  &__icon {
    display: grid;
    place-items: center;
    flex: 0 0 31px;
    width: 31px;
    height: 31px;
    border-radius: 8px;
    color: var(--brand-600);
    background: var(--brand-50);
  }

  strong {
    display: block;
    color: #334155;
    font-size: 11px;
    font-weight: 650;
  }

  p {
    margin: 3px 0 0;
    color: #94a3b8;
    font-size: 9.5px;
    line-height: 1.6;
  }

  &--warning {
    border-color: rgba(245, 158, 11, 0.13);
    background: #fffbeb;

    .dialog-intro__icon {
      color: #b45309;
      background: #fef3c7;
    }
  }
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.password-input-row {
  display: flex;
  width: 100%;
  gap: 8px;

  .el-input {
    flex: 1;
  }
}
</style>
