<script setup lang="ts">
import {
  Delete,
  DocumentCopy,
  EditPen,
  InfoFilled,
  Location,
  MagicStick,
  Picture,
  Refresh,
  Upload,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import { extractAddressFromImage, extractAddressFromText } from '@/api/address'
import { fetchModelOptions } from '@/api/models'
import PageHeader from '@/components/PageHeader.vue'
import type { AddressExtractionForm, AddressExtractionResult } from '@/types/address'
import type { ModelOption } from '@/types/model'
import { getErrorMessage } from '@/utils/http'

const mode = ref<'TEXT' | 'IMAGE'>('TEXT')
const text = ref('')
const file = ref<File | null>(null)
const previewUrl = ref('')
const fileInput = ref<HTMLInputElement>()
const dragActive = ref(false)
const models = ref<ModelOption[]>([])
const modelId = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const result = ref<AddressExtractionResult | null>(null)
const filledCount = ref(0)
const overwrite = ref(false)

const form = reactive<AddressExtractionForm>({
  receiverName: '',
  phone: '',
  email: '',
  province: '',
  city: '',
  district: '',
  street: '',
  detailAddress: '',
  country: '',
  state: '',
  addressLine1: '',
  addressLine2: '',
  postalCode: '',
  doorNumber: '',
  fullAddress: '',
})

const textModels = computed(() =>
  models.value.filter(
    (model) =>
      model.available && (model.modelType === 'TEXT' || model.modelType === 'VISION'),
  ),
)

const visionModels = computed(() =>
  models.value.filter(
    (model) =>
      model.available &&
      (model.modelType === 'VISION' || model.capabilities.includes('VISION')),
  ),
)

const availableModels = computed(() =>
  mode.value === 'IMAGE' ? visionModels.value : textModels.value,
)

const selectedModelLabel = computed(() => {
  if (!modelId.value) return '自动选择可用模型'
  return models.value.find((model) => model.id === modelId.value)?.displayName ?? '未知模型'
})

const canSubmit = computed(() => {
  if (loading.value) return false
  return mode.value === 'TEXT' ? text.value.trim().length > 0 : file.value !== null
})

const confidencePercent = computed(() => {
  const value = result.value?.confidence
  if (value === undefined || value === null) return null
  return Math.round(value * 100)
})

const isInternational = computed(
  () =>
    result.value?.addressType === 'INTERNATIONAL' ||
    (!!form.country && form.country !== '中国' && form.country !== 'China'),
)

const modelWarning = computed(() => {
  if (mode.value === 'IMAGE' && visionModels.value.length === 0) {
    return textModels.value.length > 0
      ? '当前没有可用视觉模型，将尝试本地 OCR + 文本模型；OCR 不可用时会明确失败'
      : '当前没有可用的视觉或文本模型，请先在模型管理中配置模型'
  }
  if (mode.value === 'TEXT' && textModels.value.length === 0) {
    return '当前没有可用的文本模型，请先在模型管理中配置 TEXT 或 VISION 模型'
  }
  return ''
})

function resetPreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function applyFile(selected: File) {
  const extension = selected.name.split('.').pop()?.toLowerCase() ?? ''
  if (!['png', 'jpg', 'jpeg', 'webp'].includes(extension)) {
    ElMessage.warning('仅支持 PNG / JPG / WEBP 图片')
    return
  }
  if (selected.size > 15 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 15MB')
    return
  }
  file.value = selected
  errorMessage.value = ''
  resetPreview()
  previewUrl.value = URL.createObjectURL(selected)
}

function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const selected = target.files?.[0]
  target.value = ''
  if (selected) applyFile(selected)
}

function onDrop(event: DragEvent) {
  dragActive.value = false
  const selected = event.dataTransfer?.files?.[0]
  if (selected) applyFile(selected)
}

function clearFile() {
  file.value = null
  resetPreview()
}

function joinDomesticAddress(parts: {
  province?: string | null
  city?: string | null
  district?: string | null
  street?: string | null
  detailAddress?: string | null
}) {
  return [parts.province, parts.city, parts.district, parts.street, parts.detailAddress]
    .map((value) => value?.trim())
    .filter(Boolean)
    .join('')
}

function joinInternationalAddress(parts: {
  addressLine1?: string | null
  addressLine2?: string | null
  city?: string | null
  state?: string | null
  postalCode?: string | null
  country?: string | null
}) {
  return [
    parts.addressLine1,
    parts.addressLine2,
    parts.city,
    parts.state,
    parts.postalCode,
    parts.country,
  ]
    .map((value) => value?.trim())
    .filter(Boolean)
    .join(', ')
}

function fillForm(value: AddressExtractionResult) {
  const international = value.addressType === 'INTERNATIONAL'
  const values: Record<keyof AddressExtractionForm, string> = {
    receiverName: value.receiverName || '',
    phone: value.phone || '',
    email: value.email || '',
    province: value.province || '',
    city: value.city || '',
    district: value.district || '',
    street: value.street || '',
    detailAddress: value.detailAddress || '',
    country: value.country || '',
    state: value.state || '',
    addressLine1: value.addressLine1 || (international ? value.detailAddress || '' : ''),
    addressLine2: value.addressLine2 || '',
    postalCode: value.postalCode || '',
    doorNumber: value.doorNumber || '',
    fullAddress:
      value.fullAddress ||
      (international
        ? joinInternationalAddress({
            addressLine1: value.addressLine1 || value.detailAddress,
            addressLine2: value.addressLine2,
            city: value.city,
            state: value.state,
            postalCode: value.postalCode,
            country: value.country,
          })
        : joinDomesticAddress({
            province: value.province,
            city: value.city,
            district: value.district,
            street: value.street,
            detailAddress: value.detailAddress,
          })),
  }
  let count = 0
  for (const [key, next] of Object.entries(values) as [keyof AddressExtractionForm, string][]) {
    if (!next) continue
    if (overwrite.value || !form[key]) {
      if (form[key] !== next) {
        form[key] = next
        count += 1
      }
    }
  }
  filledCount.value = count
}

async function submit() {
  if (!canSubmit.value) {
    ElMessage.warning(mode.value === 'TEXT' ? '请先粘贴订单文本' : '请先选择订单图片')
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const value =
      mode.value === 'TEXT'
        ? await extractAddressFromText(text.value, modelId.value || null)
        : await extractAddressFromImage(file.value as File, modelId.value || null)
    result.value = value
    fillForm(value)
    ElMessage.success(
      filledCount.value > 0 ? `已填充 ${filledCount.value} 个输入框` : '识别完成，表单已有内容未覆盖',
    )
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '地址识别失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function copyJson() {
  if (!result.value) return
  const payload = {
    ...result.value,
    form: { ...form },
  }
  try {
    await navigator.clipboard.writeText(JSON.stringify(payload, null, 2))
    ElMessage.success('已复制识别结果 JSON')
  } catch {
    ElMessage.warning('当前浏览器不允许访问剪贴板，请手动选择复制')
  }
}

function clearForm() {
  form.receiverName = ''
  form.phone = ''
  form.email = ''
  form.province = ''
  form.city = ''
  form.district = ''
  form.street = ''
  form.detailAddress = ''
  form.country = ''
  form.state = ''
  form.addressLine1 = ''
  form.addressLine2 = ''
  form.postalCode = ''
  form.doorNumber = ''
  form.fullAddress = ''
  result.value = null
  filledCount.value = 0
  errorMessage.value = ''
}

async function loadModels() {
  try {
    models.value = await fetchModelOptions()
  } catch {
    // 请求层已提示
  }
}

watch(mode, () => {
  modelId.value = 0
  errorMessage.value = ''
})

onMounted(() => {
  void loadModels()
})

onBeforeUnmount(() => {
  resetPreview()
})
</script>

<template>
  <div class="page-shell">
    <PageHeader
      eyebrow="AI ADDRESS ASSIST"
      title="地址识别"
      description="上传订单截图或粘贴文字，AI 提取收货信息并直接填入下方表单，减少手工录入"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="loadModels">刷新模型</el-button>
      </template>
    </PageHeader>

    <section class="address-hero">
      <div class="address-hero__copy">
        <span class="address-hero__eyebrow">FILL THE FORM, NOT THE DATABASE</span>
        <h2>识别结果只回填当前页面，不写入数据库</h2>
        <p>
          平台只负责调用模型识别地址：国内返回省、市、区、街道，国外返回国家、州省、城市、地址1/2、邮编。
          订单是否保存、保存到哪个字段，完全由 ERP 页面决定；刷新页面后识别结果即清空。
        </p>
      </div>
      <div class="address-hero__flow">
        <span><el-icon><Picture /></el-icon>图片 / 文字</span>
        <i>→</i>
        <span><el-icon><MagicStick /></el-icon>模型识别</span>
        <i>→</i>
        <span><el-icon><EditPen /></el-icon>回填输入框</span>
      </div>
    </section>

    <div class="address-workspace">
      <section class="address-input panel">
        <div class="panel-heading">
          <div>
            <span class="panel-eyebrow">INPUT</span>
            <h3>识别来源</h3>
          </div>
          <span class="panel-note">单次识别，不保存</span>
        </div>

        <div class="mode-switch">
          <button
            type="button"
            :class="{ 'is-active': mode === 'TEXT' }"
            @click="mode = 'TEXT'"
          >
            <el-icon><EditPen /></el-icon>
            粘贴文字
          </button>
          <button
            type="button"
            :class="{ 'is-active': mode === 'IMAGE' }"
            @click="mode = 'IMAGE'"
          >
            <el-icon><Picture /></el-icon>
            上传图片
          </button>
        </div>

        <div v-if="mode === 'TEXT'" class="field-block">
          <label>订单文本</label>
          <el-input
            v-model="text"
            type="textarea"
            :autosize="{ minRows: 9, maxRows: 16 }"
            maxlength="20000"
            show-word-limit
            placeholder="例如：张三 13800001234 浙江省杭州市西湖区文三路90号5楼，打印机2台，货到付款"
          />
          <span class="field-hint">支持订单截图文字、聊天记录、快递面单文字等。</span>
        </div>

        <div v-else class="field-block">
          <label>订单图片</label>
          <div
            v-if="!file"
            class="address-dropzone"
            :class="{ 'is-dragging': dragActive }"
            @click="fileInput?.click()"
            @dragenter.prevent="dragActive = true"
            @dragover.prevent="dragActive = true"
            @dragleave.prevent="dragActive = false"
            @drop.prevent="onDrop"
          >
            <span class="address-dropzone__mark"><el-icon :size="24"><Upload /></el-icon></span>
            <strong>拖拽图片到此处，或点击选择</strong>
            <p>PNG / JPG / WEBP · 单张最大 15MB</p>
            <p class="address-dropzone__hint">优先使用视觉模型；不可用时自动尝试本地 Tesseract 中文 OCR</p>
          </div>
          <div v-else class="image-preview">
            <img :src="previewUrl" alt="订单图片预览" />
            <div class="image-preview__meta">
              <span>{{ file.name }}</span>
              <el-button :icon="Delete" circle text size="small" @click="clearFile" />
            </div>
          </div>
          <input
            ref="fileInput"
            type="file"
            class="hidden-input"
            accept=".png,.jpg,.jpeg,.webp"
            @change="onFileChange"
          />
        </div>

        <div class="field-block">
          <label>
            识别模型
            <span>可选</span>
          </label>
          <el-select v-model="modelId" style="width: 100%">
            <el-option label="自动选择可用模型" :value="0" />
            <el-option
              v-for="model in availableModels"
              :key="model.id"
              :label="model.displayName"
              :value="model.id"
            />
          </el-select>
          <span class="field-hint">当前：{{ selectedModelLabel }}</span>
        </div>

        <div class="overwrite-row">
          <el-switch v-model="overwrite" size="small" />
          <div>
            <strong>覆盖已有内容</strong>
            <span>关闭时只填充空输入框，保留用户已填写的内容</span>
          </div>
        </div>

        <div v-if="modelWarning" class="inline-warning">
          <el-icon><WarningFilled /></el-icon>
          <span>{{ modelWarning }}</span>
        </div>

        <div v-if="errorMessage" class="inline-error">
          <el-icon><WarningFilled /></el-icon>
          <div>
            <strong>识别失败</strong>
            <p>{{ errorMessage }}</p>
          </div>
        </div>

        <el-button
          type="primary"
          class="submit-button"
          :loading="loading"
          :disabled="!canSubmit"
          @click="submit"
        >
          <el-icon><MagicStick /></el-icon>
          {{ loading ? '模型识别中…' : '识别并填充表单' }}
        </el-button>

        <p class="privacy-note">
          识别结果不落库、不保存图片；仅保留不含地址内容的模型运行日志。
        </p>
      </section>

      <section class="address-result panel">
        <div class="panel-heading">
          <div>
            <span class="panel-eyebrow">FORM</span>
            <h3>收货信息</h3>
          </div>
          <div class="result-actions">
            <el-button :icon="DocumentCopy" text size="small" :disabled="!result" @click="copyJson">
              复制 JSON
            </el-button>
            <el-button :icon="Delete" text size="small" @click="clearForm">清空</el-button>
          </div>
        </div>

        <div v-if="result" class="result-meta">
          <span class="result-meta__badge" :class="`is-${result.sourceType.toLowerCase()}`">
            {{ result.sourceType === 'TEXT' ? '文本识别' : '图片识别' }}
          </span>
          <span
            v-if="result.addressType"
            class="result-meta__badge"
            :class="result.addressType === 'INTERNATIONAL' ? 'is-intl' : 'is-domestic'"
          >
            {{ result.addressType === 'INTERNATIONAL' ? '国际地址' : '国内地址' }}
          </span>
          <span v-if="result.orderNo">订单号 {{ result.orderNo }}</span>
          <span v-if="result.buyerId">买家ID {{ result.buyerId }}</span>
          <span v-if="confidencePercent !== null">
            置信度 {{ confidencePercent }}%
          </span>
          <span>{{ result.modelName || '自动模型' }}</span>
          <span v-if="result.latencyMs !== undefined">{{ (result.latencyMs / 1000).toFixed(2) }}s</span>
          <span v-if="result.totalTokens">{{ result.totalTokens }} tokens</span>
          <span v-if="result.tokenEstimated" class="is-estimated">估算用量</span>
          <span v-if="result.ocrUsed" class="is-ocr">OCR 预处理</span>
        </div>

        <el-form label-position="top" class="address-form">
          <div v-if="isInternational" class="form-grid">
            <el-form-item label="收货人 / Buyer Name">
              <el-input v-model="form.receiverName" maxlength="80" placeholder="Cheryll Ann Gibson" />
            </el-form-item>
            <el-form-item label="电话 / Phone">
              <el-input v-model="form.phone" maxlength="40" placeholder="61238138871-3170" />
            </el-form-item>
            <el-form-item label="邮箱 / Email">
              <el-input v-model="form.email" maxlength="120" placeholder="buyer@example.com" />
            </el-form-item>
            <el-form-item label="国家 / Country">
              <el-input v-model="form.country" maxlength="60" placeholder="Australia" />
            </el-form-item>
            <el-form-item label="州 / 省 / State">
              <el-input v-model="form.state" maxlength="60" placeholder="QLD" />
            </el-form-item>
            <el-form-item label="城市 / City">
              <el-input v-model="form.city" maxlength="60" placeholder="BOROREN" />
            </el-form-item>
            <el-form-item label="邮编 / Postal Code">
              <el-input v-model="form.postalCode" maxlength="20" placeholder="4678" />
            </el-form-item>
            <el-form-item label="门牌 / Door No.">
              <el-input v-model="form.doorNumber" maxlength="40" placeholder="可选" />
            </el-form-item>
            <el-form-item label="地址1 / Address Line 1" class="is-full">
              <el-input v-model="form.addressLine1" maxlength="200" placeholder="46 Daisy Dell Road" />
            </el-form-item>
            <el-form-item label="地址2 / Address Line 2" class="is-full">
              <el-input v-model="form.addressLine2" maxlength="200" placeholder="Apartment, suite, unit, etc." />
            </el-form-item>
            <el-form-item label="完整地址 / Full Address" class="is-full">
              <el-input
                v-model="form.fullAddress"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                maxlength="400"
                placeholder="Address Line 1, City, State, Postal Code, Country"
              />
            </el-form-item>
          </div>

          <div v-else class="form-grid">
            <el-form-item label="收货人">
              <el-input v-model="form.receiverName" maxlength="60" placeholder="识别后自动填充" />
            </el-form-item>
            <el-form-item label="联系电话">
              <el-input v-model="form.phone" maxlength="32" placeholder="识别后自动填充" />
            </el-form-item>
            <el-form-item label="省 / 自治区 / 直辖市">
              <el-input v-model="form.province" maxlength="60" placeholder="例如：浙江省" />
            </el-form-item>
            <el-form-item label="市 / 州 / 盟">
              <el-input v-model="form.city" maxlength="60" placeholder="例如：杭州市" />
            </el-form-item>
            <el-form-item label="区 / 县 / 旗">
              <el-input v-model="form.district" maxlength="60" placeholder="例如：西湖区" />
            </el-form-item>
            <el-form-item label="街道 / 镇 / 乡">
              <el-input v-model="form.street" maxlength="80" placeholder="例如：文三路" />
            </el-form-item>
            <el-form-item label="详细地址" class="is-full">
              <el-input
                v-model="form.detailAddress"
                maxlength="200"
                placeholder="例如：90号5楼"
              />
            </el-form-item>
            <el-form-item label="完整地址" class="is-full">
              <el-input
                v-model="form.fullAddress"
                type="textarea"
                :autosize="{ minRows: 2, maxRows: 4 }"
                maxlength="300"
                placeholder="省市区街道详细地址"
              />
            </el-form-item>
          </div>
        </el-form>

        <div class="form-footer">
          <span><el-icon><InfoFilled /></el-icon>所有字段均可继续手动修改</span>
          <span v-if="filledCount > 0">本次填入 {{ filledCount }} 个字段</span>
        </div>

        <div v-if="!result" class="result-placeholder">
          <span class="result-placeholder__mark"><el-icon :size="25"><Location /></el-icon></span>
          <h3>等待识别</h3>
          <p>选择文本或图片后点击“识别并填充表单”，识别结果会直接写入左侧输入框。</p>
        </div>
      </section>
    </div>

    <section class="integration-card panel">
      <div class="integration-card__copy">
        <span class="panel-eyebrow">ERP INTEGRATION</span>
        <h3>ERP 页面如何接入</h3>
        <p>
          ERP 后端调用开放接口，拿到 JSON 后由前端填充表单。API Key 只放在 ERP 服务端，
          不要写入浏览器代码。
        </p>
      </div>
      <div class="integration-card__code">
        <div class="code-line">
          <span class="code-method">POST</span>
          <code>/api/open/v1/address/extract</code>
        </div>
        <div class="code-line">
          <span class="code-method is-key">HEADER</span>
          <code>X-API-Key: ***</code>
        </div>
        <pre>{
  "text": "张三 13800001234 浙江省杭州市西湖区文三路90号5楼",
  "modelId": null
}</pre>
        <p class="code-hint">返回 province / city / district / street / detailAddress，可直接映射到 ERP 输入框。</p>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.address-hero {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 22px 24px;
  margin-bottom: 16px;
  border: 1px solid rgba(14, 165, 233, 0.14);
  border-radius: 15px;
  color: #cbd5e1;
  background:
    radial-gradient(80% 140% at 0% 0%, rgba(14, 165, 233, 0.2), transparent 55%),
    radial-gradient(70% 120% at 100% 100%, rgba(99, 102, 241, 0.16), transparent 60%),
    linear-gradient(120deg, #0d1726, #101d30);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.1);

  &__copy {
    flex: 1;

    h2 {
      margin: 8px 0 0;
      color: #f8fafc;
      font-size: 19px;
      font-weight: 650;
      letter-spacing: -0.02em;
    }

    p {
      max-width: 780px;
      margin: 7px 0 0;
      color: #8fa2bb;
      font-size: 12px;
      line-height: 1.7;
    }
  }

  &__eyebrow {
    color: #67e8f9;
    font-family: var(--font-mono);
    font-size: 9px;
    font-weight: 600;
    letter-spacing: 0.14em;
  }

  &__flow {
    display: flex;
    align-items: center;
    gap: 7px;
    flex: 0 0 auto;
    color: #a5f3fc;
    font-family: var(--font-mono);
    font-size: 9px;

    span {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 5px 7px;
      border: 1px solid rgba(103, 232, 249, 0.16);
      border-radius: 5px;
      background: rgba(14, 165, 233, 0.08);
    }

    i {
      color: #475569;
      font-style: normal;
    }
  }
}

.address-workspace {
  display: grid;
  grid-template-columns: 410px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.panel {
  padding: 20px;
  border: 1px solid var(--border-soft);
  border-radius: 14px;
  background: #fff;
  box-shadow: var(--shadow-xs);
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;

  h3 {
    margin: 4px 0 0;
    color: #334155;
    font-size: 14px;
    font-weight: 650;
  }
}

.panel-eyebrow {
  color: #9aa8ba;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.12em;
}

.panel-note {
  color: #a4afbf;
  font-size: 9px;
}

.mode-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 4px;
  margin-bottom: 16px;
  border-radius: 9px;
  background: #f4f7fb;

  button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 5px;
    height: 32px;
    border: 1px solid transparent;
    border-radius: 7px;
    color: #64748b;
    background: transparent;
    font-size: 11px;
    font-weight: 600;
    cursor: pointer;

    &.is-active {
      border-color: rgba(14, 165, 233, 0.2);
      color: #0369a1;
      background: #fff;
      box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
    }
  }
}

.field-block {
  margin-bottom: 15px;

  > label {
    display: flex;
    align-items: center;
    gap: 5px;
    margin-bottom: 6px;
    color: #475569;
    font-size: 11px;
    font-weight: 600;

    span {
      color: #a4afbf;
      font-size: 9px;
      font-weight: 400;
    }
  }
}

.field-hint {
  display: block;
  margin-top: 5px;
  color: #a4afbf;
  font-size: 9px;
  line-height: 1.5;
}

.address-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 170px;
  padding: 20px;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  background: #fbfcfe;
  cursor: pointer;

  &:hover,
  &.is-dragging {
    border-color: #0ea5e9;
    background: #f0f9ff;
  }

  &__mark {
    display: grid;
    place-items: center;
    width: 46px;
    height: 46px;
    margin-bottom: 9px;
    border-radius: 13px;
    color: #0284c7;
    background: #e0f2fe;
  }

  strong {
    color: #475569;
    font-size: 11px;
    font-weight: 600;
  }

  p {
    margin: 5px 0 0;
    color: #a4afbf;
    font-size: 9px;
  }

  &__hint {
    max-width: 300px;
    color: #b45309 !important;
    line-height: 1.5;
    text-align: center;
  }
}

.image-preview {
  overflow: hidden;
  border: 1px solid #e9edf5;
  border-radius: 10px;
  background: #fff;

  img {
    display: block;
    width: 100%;
    height: 180px;
    object-fit: contain;
    background: #f8fafc;
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 8px 10px;

    span {
      overflow: hidden;
      color: #64748b;
      font-size: 10px;
      white-space: nowrap;
      text-overflow: ellipsis;
    }
  }
}

.hidden-input {
  display: none;
}

.overwrite-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 10px;
  margin-bottom: 13px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: #fbfcfe;

  > div {
    display: flex;
    flex-direction: column;

    strong {
      color: #475569;
      font-size: 10px;
      font-weight: 600;
    }

    span {
      margin-top: 2px;
      color: #a4afbf;
      font-size: 9px;
      line-height: 1.45;
    }
  }
}

.inline-warning,
.inline-error {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 9px 10px;
  margin-bottom: 12px;
  border-radius: 7px;
  font-size: 10px;
  line-height: 1.55;
}

.inline-warning {
  color: #b45309;
  background: #fffbeb;
  border: 1px solid rgba(245, 158, 11, 0.16);
}

.inline-error {
  color: #b91c1c;
  background: #fef2f2;
  border: 1px solid rgba(239, 68, 68, 0.14);

  strong {
    font-size: 10px;
  }

  p {
    margin: 3px 0 0;
    font-size: 9.5px;
  }
}

.submit-button {
  width: 100%;
  height: 38px;
  gap: 5px;
}

.privacy-note {
  margin: 8px 0 0;
  color: #a4afbf;
  font-size: 9px;
  line-height: 1.5;
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  margin-bottom: 14px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  color: #7c8ba3;
  background: #fbfcfe;
  font-family: var(--font-mono);
  font-size: 9px;

  &__badge {
    padding: 2px 6px;
    border-radius: 4px;
    color: #0369a1;
    background: #e0f2fe;
    font-family: var(--font-sans);
    font-size: 9px;
    font-weight: 600;

    &.is-image {
      color: #7e22ce;
      background: #f3e8ff;
    }

    &.is-intl {
      color: #0f766e;
      background: #ccfbf1;
    }

    &.is-domestic {
      color: #4338ca;
      background: #eef2ff;
    }
  }

  .is-estimated {
    color: #b45309;
  }

  .is-ocr {
    color: #0369a1;
  }
}

.address-form {
  :deep(.el-form-item) {
    margin-bottom: 13px;
  }

  :deep(.el-form-item__label) {
    padding-bottom: 4px;
    color: #64748b;
    font-size: 10px;
    font-weight: 600;
    line-height: 1.4;
  }
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;

  .is-full {
    grid-column: 1 / -1;
  }
}

.form-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
  color: #a4afbf;
  font-size: 9px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
}

.result-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 170px;
  margin-top: 12px;
  border: 1px dashed #dbe3ee;
  border-radius: 10px;
  text-align: center;
  background: #fbfcfe;

  &__mark {
    display: grid;
    place-items: center;
    width: 46px;
    height: 46px;
    border-radius: 13px;
    color: #0ea5e9;
    background: #e0f2fe;
  }

  h3 {
    margin: 10px 0 0;
    color: #475569;
    font-size: 12px;
    font-weight: 600;
  }

  p {
    max-width: 360px;
    margin: 4px 0 0;
    color: #a4afbf;
    font-size: 9.5px;
    line-height: 1.55;
  }
}

.integration-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr);
  gap: 26px;
  margin-top: 16px;

  &__copy {
    h3 {
      margin: 5px 0 0;
      color: #334155;
      font-size: 14px;
      font-weight: 650;
    }

    p {
      margin: 8px 0 0;
      color: #7c8ba3;
      font-size: 11px;
      line-height: 1.7;
    }
  }

  &__code {
    padding: 12px;
    border: 1px solid #e8eef6;
    border-radius: 9px;
    background: #0f172a;

    pre {
      margin: 9px 0 0;
      color: #cbd5e1;
      font-family: var(--font-mono);
      font-size: 9.5px;
      line-height: 1.6;
      white-space: pre-wrap;
    }
  }
}

.code-line {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 4px;

  code {
    color: #e2e8f0;
    font-family: var(--font-mono);
    font-size: 9.5px;
  }
}

.code-method {
  min-width: 48px;
  padding: 2px 5px;
  border-radius: 3px;
  color: #6ee7b7;
  background: rgba(16, 185, 129, 0.1);
  font-family: var(--font-mono);
  font-size: 8px;
  text-align: center;

  &.is-key {
    color: #fcd34d;
    background: rgba(245, 158, 11, 0.1);
  }
}

.code-hint {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 9px;
  line-height: 1.5;
}

@media (max-width: 1180px) {
  .address-workspace {
    grid-template-columns: 360px minmax(0, 1fr);
  }

  .address-hero__flow {
    display: none;
  }
}

@media (max-width: 900px) {
  .address-workspace,
  .integration-card {
    grid-template-columns: 1fr;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
