<template>
  <div class="container">
    <Breadcrumb />
    <a-card class="general-card" :title="$t('menu.bot.equipPool')">
      <a-alert class="tips" type="info" show-icon>
        {{ $t('bot.equipPool.tips') }}
      </a-alert>

      <a-tabs v-model:active-key="activeTab" @change="onTabChange">
        <!-- ============================ 通用装备池 ============================ -->
        <a-tab-pane key="GENERIC" :title="$t('bot.equipPool.tab.generic')">
          <a-space style="margin-bottom: 12px">
            <a-select v-model="genericCategory" :placeholder="$t('bot.equipPool.filter.category')" allow-clear
              :style="{ width: '180px' }" @change="loadGeneric">
              <a-option v-for="c in GENERIC_CATEGORY_OPTIONS" :key="c.value" :value="c.value">
                {{ c.label }}
              </a-option>
            </a-select>
            <a-button type="primary" status="success" @click="addGenericRow">
              {{ $t('bot.equipPool.addRow') }}
            </a-button>
            <a-button type="primary" status="danger" :disabled="selectedGeneric.length === 0"
              @click="deleteSelected('GENERIC')">
              {{ $t('bot.equipPool.deleteSelected') }}
            </a-button>
            <a-button type="primary" status="warning" :loading="reloading" @click="reloadClick">
              {{ $t('bot.equipPool.reload') }}
            </a-button>
            <a-tag color="arcoblue">{{ $t('bot.equipPool.total', { total: genericRows.length }) }}</a-tag>
          </a-space>
          <a-table row-key="rowKey" :loading="loading" :data="genericRows" v-model:selectedKeys="selectedGeneric"
            :row-selection="{ type: 'checkbox', showCheckedAll: true }" column-resizable
            :pagination="{ pageSize: 20, showTotal: true, showPageSize: true }" :bordered="{ cell: true }">
            <template #columns>
              <a-table-column title="ID" data-index="id" :width="80" align="center"
                :sortable="{ sortDirections: ['ascend', 'descend'], sorter: sortById }">
                <template #cell="{ record }">
                  <span v-if="record.id">{{ record.id }}</span>
                  <a-tag v-else color="orange">{{ $t('bot.equipPool.new') }}</a-tag>
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.category')" :width="180" align="center">
                <template #cell="{ record }">
                  <a-select v-model="record.category" size="small" allow-create>
                    <a-option v-for="c in GENERIC_CATEGORY_OPTIONS" :key="c.value" :value="c.value">
                      {{ c.label }}
                    </a-option>
                  </a-select>
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.itemId')" :width="160" align="center">
                <template #cell="{ record }">
                  <a-input-number v-model="record.itemId" :min="0" size="small" />
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.enabled')" :width="100" align="center">
                <template #cell="{ record }">
                  <a-switch v-model="record.enabled" />
                </template>
              </a-table-column>
              <a-table-column :title="$t('operation')" :width="160" align="center" fixed="right">
                <template #cell="{ record }">
                  <a-button size="mini" type="text" @click="saveGeneric(record)">
                    {{ $t('button.save') }}
                  </a-button>
                  <a-popconfirm type="error" :content="$t('bot.equipPool.delete.tips')" position="left"
                    @ok="deleteOneRow('GENERIC', record)">
                    <a-button size="mini" type="text" status="danger">
                      {{ $t('button.delete') }}
                    </a-button>
                  </a-popconfirm>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- ============================ NX 点装池 ============================ -->
        <a-tab-pane key="NX" :title="$t('bot.equipPool.tab.nx')">
          <a-space style="margin-bottom: 12px">
            <a-select v-model="nxCategory" :placeholder="$t('bot.equipPool.filter.category')" allow-clear
              :style="{ width: '180px' }" @change="loadNx">
              <a-option v-for="c in NX_CATEGORY_OPTIONS" :key="c.value" :value="c.value">{{ c.label }}</a-option>
            </a-select>
            <a-button type="primary" status="success" @click="addNxRow">
              {{ $t('bot.equipPool.addRow') }}
            </a-button>
            <a-button type="primary" status="danger" :disabled="selectedNx.length === 0" @click="deleteSelected('NX')">
              {{ $t('bot.equipPool.deleteSelected') }}
            </a-button>
            <a-button type="primary" status="warning" :loading="reloading" @click="reloadClick">
              {{ $t('bot.equipPool.reload') }}
            </a-button>
            <a-tag color="arcoblue">{{ $t('bot.equipPool.total', { total: nxRows.length }) }}</a-tag>
          </a-space>
          <a-table row-key="rowKey" :loading="loading" :data="nxRows" v-model:selectedKeys="selectedNx"
            :row-selection="{ type: 'checkbox', showCheckedAll: true }" column-resizable
            :pagination="{ pageSize: 20, showTotal: true, showPageSize: true }" :bordered="{ cell: true }">
            <template #columns>
              <a-table-column title="ID" data-index="id" :width="80" align="center"
                :sortable="{ sortDirections: ['ascend', 'descend'], sorter: sortById }">
                <template #cell="{ record }">
                  <span v-if="record.id">{{ record.id }}</span>
                  <a-tag v-else color="orange">{{ $t('bot.equipPool.new') }}</a-tag>
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.category')" :width="180" align="center">
                <template #cell="{ record }">
                  <a-select v-model="record.category" size="small" allow-create>
                    <a-option v-for="c in NX_CATEGORY_OPTIONS" :key="c.value" :value="c.value">{{ c.label }}</a-option>
                  </a-select>
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.itemId')" :width="160" align="center">
                <template #cell="{ record }">
                  <a-input-number v-model="record.itemId" :min="0" size="small" />
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.gender')" :width="150" align="center">
                <template #cell="{ record }">
                  <a-select v-model="record.gender" size="small" allow-clear>
                    <a-option value="">{{ $t('bot.equipPool.gender.derive') }}</a-option>
                    <a-option value="male">{{ $t('bot.gender.male') }}</a-option>
                    <a-option value="female">{{ $t('bot.gender.female') }}</a-option>
                    <a-option value="unisex">{{ $t('bot.equipPool.gender.unisex') }}</a-option>
                  </a-select>
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.enabled')" :width="100" align="center">
                <template #cell="{ record }">
                  <a-switch v-model="record.enabled" />
                </template>
              </a-table-column>
              <a-table-column :title="$t('operation')" :width="160" align="center" fixed="right">
                <template #cell="{ record }">
                  <a-button size="mini" type="text" @click="saveNx(record)">
                    {{ $t('button.save') }}
                  </a-button>
                  <a-popconfirm type="error" :content="$t('bot.equipPool.delete.tips')" position="left"
                    @ok="deleteOneRow('NX', record)">
                    <a-button size="mini" type="text" status="danger">
                      {{ $t('button.delete') }}
                    </a-button>
                  </a-popconfirm>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </a-tab-pane>

        <!-- ============================ 排除表 ============================ -->
        <a-tab-pane key="OMIT" :title="$t('bot.equipPool.tab.omit')">
          <a-space style="margin-bottom: 12px">
            <a-select v-model="omitBlockType" :placeholder="$t('bot.equipPool.filter.blockType')" allow-clear
              :style="{ width: '180px' }" @change="loadOmit">
              <a-option value="ID">ID</a-option>
              <a-option value="RANGE">RANGE</a-option>
            </a-select>
            <a-button type="primary" status="success" @click="addOmitRow">
              {{ $t('bot.equipPool.addRow') }}
            </a-button>
            <a-button type="primary" status="danger" :disabled="selectedOmit.length === 0"
              @click="deleteSelected('OMIT')">
              {{ $t('bot.equipPool.deleteSelected') }}
            </a-button>
            <a-button type="primary" status="warning" :loading="reloading" @click="reloadClick">
              {{ $t('bot.equipPool.reload') }}
            </a-button>
            <a-tag color="arcoblue">{{ $t('bot.equipPool.total', { total: omitRows.length }) }}</a-tag>
          </a-space>
          <a-table row-key="rowKey" :loading="loading" :data="omitRows" v-model:selectedKeys="selectedOmit"
            :row-selection="{ type: 'checkbox', showCheckedAll: true }" column-resizable
            :pagination="{ pageSize: 20, showTotal: true, showPageSize: true }" :bordered="{ cell: true }">
            <template #columns>
              <a-table-column title="ID" data-index="id" :width="80" align="center"
                :sortable="{ sortDirections: ['ascend', 'descend'], sorter: sortById }">
                <template #cell="{ record }">
                  <span v-if="record.id">{{ record.id }}</span>
                  <a-tag v-else color="orange">{{ $t('bot.equipPool.new') }}</a-tag>
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.blockType')" :width="140" align="center">
                <template #cell="{ record }">
                  <a-select v-model="record.blockType" size="small">
                    <a-option value="ID">ID</a-option>
                    <a-option value="RANGE">RANGE</a-option>
                  </a-select>
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.itemId')" :width="150" align="center">
                <template #cell="{ record }">
                  <a-input-number v-model="record.itemId" :min="0" size="small"
                    :disabled="record.blockType === 'RANGE'" />
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.range')" :width="190" align="center">
                <template #cell="{ record }">
                  <a-space>
                    <a-input-number v-model="record.rangeStart" :min="0" size="small"
                      :disabled="record.blockType !== 'RANGE'" :style="{ width: '80px' }" />
                    <a-input-number v-model="record.rangeEnd" :min="0" size="small"
                      :disabled="record.blockType !== 'RANGE'" :style="{ width: '80px' }" />
                  </a-space>
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.list.column.note')" :width="240" align="center">
                <template #cell="{ record }">
                  <a-input v-model="record.note" :max-length="64" size="small" allow-clear />
                </template>
              </a-table-column>
              <a-table-column :title="$t('bot.equipPool.column.enabled')" :width="100" align="center">
                <template #cell="{ record }">
                  <a-switch v-model="record.enabled" />
                </template>
              </a-table-column>
              <a-table-column :title="$t('operation')" :width="160" align="center" fixed="right">
                <template #cell="{ record }">
                  <a-button size="mini" type="text" @click="saveOmit(record)">
                    {{ $t('button.save') }}
                  </a-button>
                  <a-popconfirm type="error" :content="$t('bot.equipPool.delete.tips')" position="left"
                    @ok="deleteOneRow('OMIT', record)">
                    <a-button size="mini" type="text" status="danger">
                      {{ $t('button.delete') }}
                    </a-button>
                  </a-popconfirm>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import { useI18n } from 'vue-i18n';
import useLoading from '@/hooks/loading';
import {
  BotEquipGenericRow,
  BotEquipNxRow,
  BotEquipOmitRow,
  BotPoolType,
  deletePoolRows,
  getGenericPool,
  getNxPool,
  getOmitPool,
  reloadEquipPools,
  saveGenericPool,
  saveNxPool,
  saveOmitPool,
} from '@/api/bot';

const { t } = useI18n();
const { loading, setLoading } = useLoading(false);

const GENERIC_CATEGORIES = [
  'tops',
  'bottoms',
  'overalls',
  'weapons',
  'caps',
  'shoes',
  'gloves',
  'capes',
];

const NX_CATEGORIES = [
  'caps',
  'tops',
  'bottoms',
  'overalls',
  'weapons',
  'shoes',
  'gloves',
  'capes',
  'earrings',
  'face',
  'eye',
  'rings',
];

/**
 * 分类下拉的展示项：value 始终是入库用的英文 key，label 只是界面文案。
 * 库里的分类可能被人工改成任意值（下拉支持 allow-create），那种情况没有对应文案，
 * 直接用原始值显示，避免露出 `bot.equipPool.category.xxx`。
 *
 * 注意：不要用 vue-i18n 的 te() 来判断 key 存在与否。本项目的语言包是「扁平点号键」
 * （形如 'bot.equipPool.category.tops'），而 te() 内部走 resolveValue 按点号逐层下钻、
 * 不认整串扁平键，会恒返回 false；t() 则先做整串直接命中，所以 t 正常、te 恒 false。
 */
const KNOWN_CATEGORIES = new Set([...GENERIC_CATEGORIES, ...NX_CATEGORIES]);

const categoryLabel = (c: string) =>
  KNOWN_CATEGORIES.has(c) ? t(`bot.equipPool.category.${c}`) : c;

const GENERIC_CATEGORY_OPTIONS = computed(() =>
  GENERIC_CATEGORIES.map((c) => ({ value: c, label: categoryLabel(c) })),
);

const NX_CATEGORY_OPTIONS = computed(() =>
  NX_CATEGORIES.map((c) => ({ value: c, label: categoryLabel(c) })),
);

/** 表格 row-key：库里的行用 id，新增未保存的行用负数占位，保证唯一且不撞 id */
let draftSeq = -1;
const withKeys = <T extends { id?: number }>(rows: T[]) =>
  rows.map((r) => ({ ...r, rowKey: r.id ?? draftSeq-- }));

/**
 * ID 列的排序比较器。
 * 两点注意：
 * 1. Arco 一旦给了自定义 sorter 函数，就直接采用返回值、不再按 direction 翻面，
 *    所以升降序要在这里自己处理（用 extra.direction）。
 * 2. 新增未保存的草稿行没有 id，无论升序还是降序都沉底，彼此之间按新增顺序排列。
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

const activeTab = ref<BotPoolType>('GENERIC');
const reloading = ref(false);

const genericRows = ref<(BotEquipGenericRow & { rowKey: number })[]>([]);
const nxRows = ref<(BotEquipNxRow & { rowKey: number })[]>([]);
const omitRows = ref<(BotEquipOmitRow & { rowKey: number })[]>([]);

// Arco 的 row-selection 用 (string | number)[]，这里跟着它的类型走，避免 vue-tsc 报错
const selectedGeneric = ref<Array<string | number>>([]);
const selectedNx = ref<Array<string | number>>([]);
const selectedOmit = ref<Array<string | number>>([]);

const genericCategory = ref('');
const nxCategory = ref('');
const omitBlockType = ref('');

const loadGeneric = async () => {
  setLoading(true);
  try {
    const { data } = await getGenericPool(genericCategory.value || undefined);
    genericRows.value = withKeys(data || []);
    selectedGeneric.value = [];
  } finally {
    setLoading(false);
  }
};

const loadNx = async () => {
  setLoading(true);
  try {
    const { data } = await getNxPool(nxCategory.value || undefined);
    nxRows.value = withKeys(data || []);
    selectedNx.value = [];
  } finally {
    setLoading(false);
  }
};

const loadOmit = async () => {
  setLoading(true);
  try {
    const { data } = await getOmitPool(omitBlockType.value || undefined);
    omitRows.value = withKeys(data || []);
    selectedOmit.value = [];
  } finally {
    setLoading(false);
  }
};

const onTabChange = (key: string | number) => {
  if (key === 'GENERIC') loadGeneric();
  else if (key === 'NX') loadNx();
  else loadOmit();
};

const addGenericRow = () => {
  genericRows.value.unshift({
    rowKey: draftSeq--,
    category: genericCategory.value || 'tops',
    itemId: 0,
    enabled: true,
  });
};

const addNxRow = () => {
  nxRows.value.unshift({
    rowKey: draftSeq--,
    category: nxCategory.value || 'caps',
    itemId: 0,
    gender: '',
    enabled: true,
  });
};

const addOmitRow = () => {
  omitRows.value.unshift({
    rowKey: draftSeq--,
    blockType: 'ID',
    itemId: 0,
    rangeStart: 0,
    rangeEnd: 0,
    note: '',
    enabled: true,
  });
};

const saveGeneric = async (record: BotEquipGenericRow & { rowKey: number }) => {
  if (!record.category || !record.itemId) {
    Message.warning(t('bot.equipPool.validate.required'));
    return;
  }
  setLoading(true);
  try {
    await saveGenericPool({
      id: record.id,
      category: record.category,
      itemId: record.itemId,
      enabled: record.enabled,
    });
    Message.success(t('bot.equipPool.save.success'));
    await loadGeneric();
  } finally {
    setLoading(false);
  }
};

const saveNx = async (record: BotEquipNxRow & { rowKey: number }) => {
  if (!record.category || !record.itemId) {
    Message.warning(t('bot.equipPool.validate.required'));
    return;
  }
  setLoading(true);
  try {
    await saveNxPool({
      id: record.id,
      category: record.category,
      itemId: record.itemId,
      gender: record.gender || '',
      enabled: record.enabled,
    });
    Message.success(t('bot.equipPool.save.success'));
    await loadNx();
  } finally {
    setLoading(false);
  }
};

const saveOmit = async (record: BotEquipOmitRow & { rowKey: number }) => {
  if (record.blockType === 'RANGE') {
    if (!record.rangeStart || !record.rangeEnd) {
      Message.warning(t('bot.equipPool.validate.range'));
      return;
    }
  } else if (!record.itemId) {
    Message.warning(t('bot.equipPool.validate.required'));
    return;
  }
  setLoading(true);
  try {
    await saveOmitPool({
      id: record.id,
      blockType: record.blockType,
      itemId: record.itemId || 0,
      rangeStart: record.rangeStart || 0,
      rangeEnd: record.rangeEnd || 0,
      note: record.note || '',
      enabled: record.enabled,
    });
    Message.success(t('bot.equipPool.save.success'));
    await loadOmit();
  } finally {
    setLoading(false);
  }
};

const deleteOneRow = async (
  poolType: BotPoolType,
  record: { id?: number; rowKey: number }
) => {
  if (!record.id) {
    // 未保存的草稿行，直接摘掉
    if (poolType === 'GENERIC') genericRows.value = genericRows.value.filter((r) => r.rowKey !== record.rowKey);
    if (poolType === 'NX') nxRows.value = nxRows.value.filter((r) => r.rowKey !== record.rowKey);
    if (poolType === 'OMIT') omitRows.value = omitRows.value.filter((r) => r.rowKey !== record.rowKey);
    return;
  }
  await deleteRows(poolType, [record.id]);
};

const deleteSelected = async (poolType: BotPoolType) => {
  const selected =
    poolType === 'GENERIC'
      ? selectedGeneric.value
      : poolType === 'NX'
        ? selectedNx.value
        : selectedOmit.value;
  const ids = selected.map((k) => Number(k)).filter((k) => k > 0);
  if (ids.length === 0) {
    Message.warning(t('bot.equipPool.validate.selectSaved'));
    return;
  }
  await deleteRows(poolType, ids);
};

const deleteRows = async (poolType: BotPoolType, ids: number[]) => {
  setLoading(true);
  try {
    await deletePoolRows(poolType, ids);
    Message.success(t('bot.equipPool.delete.success'));
    if (poolType === 'GENERIC') await loadGeneric();
    else if (poolType === 'NX') await loadNx();
    else await loadOmit();
  } finally {
    setLoading(false);
  }
};

const reloadClick = async () => {
  reloading.value = true;
  try {
    await reloadEquipPools();
    Message.success(t('bot.equipPool.reload.success'));
    onTabChange(activeTab.value);
  } finally {
    reloading.value = false;
  }
};

loadGeneric();
</script>

<script lang="ts">
export default {
  name: 'BotEquipPool',
};
</script>

<style scoped lang="less">
.tips {
  margin-bottom: 12px;
}
</style>
