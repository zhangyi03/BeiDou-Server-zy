<template>
  <div class="container">
    <Breadcrumb />
    <a-card class="general-card" :title="$t('menu.bot.dialogue')">
      <a-alert class="tips" type="info" show-icon>
        {{ $t('bot.dialogue.tips') }}
      </a-alert>

      <a-tabs v-model:active-key="activeTab" @change="onTabChange">
        <!-- 两个池共用下面同一张表，标签页只当切换器，避免同一份数据渲染两遍 -->
        <a-tab-pane v-for="tab in TABS" :key="tab.value" :title="$t(tab.labelKey)" />
      </a-tabs>

      <a-space style="margin-bottom: 12px">
        <a-button type="primary" status="success" @click="addRow">
          {{ $t('bot.dialogue.addRow') }}
        </a-button>
        <a-button type="primary" status="danger" :disabled="selected.length === 0" @click="deleteSelected">
          {{ $t('bot.dialogue.deleteSelected') }}
        </a-button>
        <a-button type="primary" status="warning" :loading="reloading" @click="reloadClick">
          {{ $t('bot.dialogue.reload') }}
        </a-button>
        <a-tag color="arcoblue">{{ $t('bot.dialogue.total', { total: rows.length }) }}</a-tag>
      </a-space>

      <a-alert class="tips" type="normal" show-icon>
        {{ $t(currentTipsKey) }}
      </a-alert>

      <a-table row-key="rowKey" :loading="loading" :data="rows" v-model:selectedKeys="selected"
        :row-selection="{ type: 'checkbox', showCheckedAll: true }" column-resizable
        :pagination="{ pageSize: 20, showTotal: true, showPageSize: true }" :bordered="{ cell: true }">
        <template #columns>
          <a-table-column title="ID" data-index="id" :width="80" align="center"
            :sortable="{ sortDirections: ['ascend', 'descend'], sorter: sortById }">
            <template #cell="{ record }">
              <span v-if="record.id">{{ record.id }}</span>
              <a-tag v-else color="orange">{{ $t('bot.dialogue.new') }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="$t('bot.dialogue.column.content')" :width="480">
            <template #cell="{ record }">
              <a-input v-model="record.content" size="small" :max-length="200" allow-clear />
            </template>
          </a-table-column>
          <a-table-column :title="$t('bot.dialogue.column.weight')" :width="110" align="center">
            <template #cell="{ record }">
              <a-input-number v-model="record.weight" :min="1" size="small" />
            </template>
          </a-table-column>
          <a-table-column :title="$t('bot.dialogue.column.note')" :width="200" align="center">
            <template #cell="{ record }">
              <a-input v-model="record.note" :max-length="64" size="small" allow-clear />
            </template>
          </a-table-column>
          <a-table-column :title="$t('bot.dialogue.column.enabled')" :width="100" align="center">
            <template #cell="{ record }">
              <a-switch v-model="record.enabled" />
            </template>
          </a-table-column>
          <a-table-column :title="$t('operation')" :width="160" align="center" fixed="right">
            <template #cell="{ record }">
              <a-button size="mini" type="text" @click="saveRow(record)">
                {{ $t('button.save') }}
              </a-button>
              <a-popconfirm type="error" :content="$t('bot.dialogue.delete.tips')" position="left"
                @ok="deleteOneRow(record)">
                <a-button size="mini" type="text" status="danger">
                  {{ $t('button.delete') }}
                </a-button>
              </a-popconfirm>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import { useI18n } from 'vue-i18n';
import useLoading from '@/hooks/loading';
import {
  BotDialoguePoolRow,
  BotDialoguePoolType,
  deleteDialoguePool,
  getDialoguePool,
  reloadDialoguePool,
  saveDialoguePool,
} from '@/api/bot';

const { t } = useI18n();
const { loading, setLoading } = useLoading(false);

/** 两个池共用一张表：切标签页就换 poolType 重新查库 */
const TABS: { value: BotDialoguePoolType; labelKey: string; tipsKey: string }[] = [
  {
    value: 'shop_name',
    labelKey: 'bot.dialogue.tab.shopName',
    tipsKey: 'bot.dialogue.tab.shopName.tips',
  },
  {
    value: 'shout',
    labelKey: 'bot.dialogue.tab.shout',
    tipsKey: 'bot.dialogue.tab.shout.tips',
  },
];

/** 表格 row-key：库里的行用 id，新增未保存的行用负数占位，保证唯一且不撞 id */
let draftSeq = -1;
const withKeys = <T extends { id?: number }>(list: T[]) =>
  list.map((r) => ({ ...r, rowKey: r.id ?? draftSeq-- }));

/**
 * ID 列的排序比较器。
 * 1. Arco 给了自定义 sorter 就不再按 direction 翻面，升降序要自己处理；
 * 2. 新增未保存的草稿行没有 id，升序降序都沉底。
 */
const sortById = (
  a: { id?: number; rowKey: number },
  b: { id?: number; rowKey: number },
  { direction }: { direction: 'ascend' | 'descend' },
) => {
  const aDraft = a.id == null;
  const bDraft = b.id == null;
  if (aDraft || bDraft) {
    if (aDraft && bDraft) return a.rowKey - b.rowKey;
    return aDraft ? 1 : -1;
  }
  const diff = (a.id as number) - (b.id as number);
  return direction === 'descend' ? -diff : diff;
};

const activeTab = ref<BotDialoguePoolType>('shop_name');
const reloading = ref(false);
const rows = ref<(BotDialoguePoolRow & { rowKey: number })[]>([]);
// Arco 的 row-selection 用 (string | number)[]，跟着它的类型走，避免 vue-tsc 报错
const selected = ref<Array<string | number>>([]);

/** 当前标签页对应的说明文案 key */
const currentTipsKey = computed(
  () => TABS.find((tab) => tab.value === activeTab.value)?.tipsKey || 'bot.dialogue.tips',
);

const load = async () => {
  setLoading(true);
  try {
    const { data } = await getDialoguePool(activeTab.value);
    rows.value = withKeys(data || []);
    selected.value = [];
  } finally {
    setLoading(false);
  }
};

const onTabChange = () => {
  load();
};

const addRow = () => {
  rows.value.unshift({
    rowKey: draftSeq--,
    poolType: activeTab.value,
    content: '',
    weight: 100,
    enabled: true,
    note: '',
  });
};

const saveRow = async (record: BotDialoguePoolRow & { rowKey: number }) => {
  if (!record.content || !record.content.trim()) {
    Message.warning(t('bot.dialogue.validate.required'));
    return;
  }
  setLoading(true);
  try {
    await saveDialoguePool({
      id: record.id,
      poolType: record.poolType || activeTab.value,
      content: record.content.trim(),
      weight: record.weight || 100,
      enabled: record.enabled,
      note: record.note || '',
    });
    Message.success(t('bot.dialogue.save.success'));
    await load();
  } finally {
    setLoading(false);
  }
};

const deleteOneRow = async (record: { id?: number; rowKey: number }) => {
  if (!record.id) {
    // 未保存的草稿行，直接摘掉
    rows.value = rows.value.filter((r) => r.rowKey !== record.rowKey);
    return;
  }
  await remove(record.id);
};

const deleteSelected = async () => {
  const ids = selected.value.map((k) => Number(k)).filter((k) => k > 0);
  if (ids.length === 0) {
    Message.warning(t('bot.dialogue.validate.selectSaved'));
    return;
  }
  await remove(ids);
};

/** 删除接口只认 id 列表（id 全表唯一），单个删除也走这里 */
const remove = async (idOrIds: number | number[]) => {
  const ids = Array.isArray(idOrIds) ? idOrIds : [idOrIds];
  setLoading(true);
  try {
    await deleteDialoguePool(ids);
    Message.success(t('bot.dialogue.delete.success'));
    await load();
  } finally {
    setLoading(false);
  }
};

const reloadClick = async () => {
  reloading.value = true;
  try {
    await reloadDialoguePool();
    Message.success(t('bot.dialogue.reload.success'));
    await load();
  } finally {
    reloading.value = false;
  }
};

load();
</script>

<script lang="ts">
export default {
  name: 'BotDialoguePool',
};
</script>

<style scoped lang="less">
.tips {
  margin-bottom: 12px;
}
</style>
