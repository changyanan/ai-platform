<script setup lang="ts">
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdownLanguage from 'highlight.js/lib/languages/markdown'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import MarkdownIt from 'markdown-it'
import { computed } from 'vue'

import 'highlight.js/styles/github-dark.css'

hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('css', css)
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('markdown', markdownLanguage)
hljs.registerLanguage('md', markdownLanguage)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)

const props = defineProps<{
  content: string
}>()

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: false,
})

const defaultLinkOpen = markdown.renderer.rules.link_open
markdown.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx]?.attrSet('target', '_blank')
  tokens[idx]?.attrSet('rel', 'noopener noreferrer')
  if (defaultLinkOpen) {
    return defaultLinkOpen(tokens, idx, options, env, self)
  }
  return self.renderToken(tokens, idx, options)
}

markdown.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  if (!token) return ''
  const info = token.info ? markdown.utils.unescapeAll(token.info).trim() : ''
  const language = info.split(/\s+/g)[0] ?? ''
  let highlighted: string
  if (language && hljs.getLanguage(language)) {
    highlighted = hljs.highlight(token.content, {
      language,
      ignoreIllegals: true,
    }).value
  } else {
    highlighted = markdown.utils.escapeHtml(token.content)
  }
  const label = language ? markdown.utils.escapeHtml(language) : 'code'
  return `<div class="code-shell"><div class="code-shell__head"><span>${label}</span><button type="button" class="code-copy">复制</button></div><pre class="hljs"><code>${highlighted}</code></pre></div>`
}

const renderedHtml = computed(() => {
  const raw = markdown.render(props.content || '')
  return DOMPurify.sanitize(raw, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target', 'rel'],
  })
})

async function handleContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement
  const button = target.closest<HTMLButtonElement>('.code-copy')
  if (!button) return
  const code = button.closest('.code-shell')?.querySelector('code')?.textContent ?? ''
  try {
    await navigator.clipboard.writeText(code)
    const previous = button.textContent
    button.textContent = '已复制'
    button.classList.add('is-copied')
    window.setTimeout(() => {
      button.textContent = previous || '复制'
      button.classList.remove('is-copied')
    }, 1600)
  } catch {
    button.textContent = '复制失败'
  }
}
</script>

<template>
  <div class="markdown-body" v-html="renderedHtml" @click="handleContentClick"></div>
</template>

<style scoped lang="scss">
.markdown-body {
  color: #334155;
  font-size: 13px;
  line-height: 1.85;
  word-break: break-word;

  :deep(> *:first-child) {
    margin-top: 0;
  }

  :deep(> *:last-child) {
    margin-bottom: 0;
  }

  :deep(p) {
    margin: 0 0 11px;
  }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 20px 0 9px;
    color: #0f172a;
    font-weight: 650;
    line-height: 1.45;
  }

  :deep(h1) {
    font-size: 20px;
  }

  :deep(h2) {
    font-size: 17px;
  }

  :deep(h3) {
    font-size: 15px;
  }

  :deep(ul),
  :deep(ol) {
    margin: 7px 0 12px;
    padding-left: 22px;
  }

  :deep(li) {
    margin: 3px 0;
  }

  :deep(li > p) {
    margin: 0;
  }

  :deep(blockquote) {
    margin: 12px 0;
    padding: 8px 14px;
    border-left: 3px solid #a5b4fc;
    color: #64748b;
    background: #f8faff;
    border-radius: 0 7px 7px 0;
  }

  :deep(a) {
    color: var(--brand-600);
    text-decoration: underline;
    text-underline-offset: 2px;
  }

  :deep(code:not(pre code)) {
    padding: 2px 5px;
    border: 1px solid #e7edf5;
    border-radius: 4px;
    color: #be185d;
    background: #fff7fb;
    font-family: var(--font-mono);
    font-size: 0.88em;
  }

  :deep(table) {
    width: 100%;
    margin: 12px 0;
    border-collapse: collapse;
    font-size: 12px;
  }

  :deep(th),
  :deep(td) {
    padding: 8px 10px;
    border: 1px solid #e9edf5;
    text-align: left;
  }

  :deep(th) {
    color: #475569;
    background: #f8fafc;
    font-weight: 600;
  }

  :deep(hr) {
    margin: 18px 0;
    border: 0;
    border-top: 1px solid #eef2f7;
  }

  :deep(.code-shell) {
    margin: 12px 0;
    overflow: hidden;
    border: 1px solid #1e293b;
    border-radius: 10px;
    background: #0b1220;
    box-shadow: 0 8px 22px rgba(15, 23, 42, 0.12);
  }

  :deep(.code-shell__head) {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 34px;
    padding: 0 13px;
    border-bottom: 1px solid rgba(148, 163, 184, 0.12);
    color: #64748b;
    font-family: var(--font-mono);
    font-size: 9px;
    letter-spacing: 0.03em;
    background: #0f172a;
  }

  :deep(.code-copy) {
    padding: 3px 7px;
    border: 1px solid rgba(148, 163, 184, 0.16);
    border-radius: 4px;
    color: #94a3b8;
    background: transparent;
    cursor: pointer;
    font-family: var(--font-sans);
    font-size: 9px;
    transition: all 0.16s ease;

    &:hover,
    &.is-copied {
      color: #e2e8f0;
      border-color: rgba(165, 180, 252, 0.3);
      background: rgba(99, 102, 241, 0.15);
    }
  }

  :deep(pre) {
    margin: 0;
    overflow-x: auto;
    background: #0b1220;
  }

  :deep(pre code) {
    display: block;
    padding: 14px 16px;
    color: #e2e8f0;
    background: transparent;
    font-family: var(--font-mono);
    font-size: 11.5px;
    line-height: 1.7;
  }

  :deep(pre.hljs) {
    background: #0b1220;
  }
}
</style>
