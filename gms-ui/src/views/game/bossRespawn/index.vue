<template>
  <div class="container">
    <Breadcrumb />
    <a-card class="general-card" :title="$t('menu.game.bossRespawn')">
      <a-alert class="tips" type="info" show-icon>
        {{ $t('bossRespawn.tips') }}
      </a-alert>
      <a-row>
        <a-col>
          <a-space>
            <a-input
              v-model="filterText"
              :placeholder="$t('bossRespawn.placeholder.filter')"
              allow-clear
              :style="{ width: '260px' }"
              @keydown.enter="loadConfigs"
            />
            <a-button type="primary" @click="loadConfigs">
              {{ $t('button.refresh') }}
            </a-button>
            <a-button
              type="primary"
              status="warning"
              :loading="reloading"
              @click="reloadClick"
            >
              {{ $t('bossRespawn.button.reload') }}
            </a-button>
            <a-tag color="arcoblue">
              {{ $t('bossRespawn.timeRate') }}: {{ timeRate }}
            </a-tag>
            <a-tag color="gray">
              {{ $t('bossRespawn.total', { total: filteredList.length }) }}
            </a-tag>
          </a-space>
        </a-col>
      </a-row>
      <a-table
        row-key="eventName"
        :loading="loading"
        :data="filteredList"
        column-resizable
        :pagination="false"
        :bordered="{ cell: true }"
        :scroll="{ x: 1700 }"
      >
        <template #columns>
          <a-table-column
            title="ID"
            data-index="id"
            :width="60"
            align="center"
          />
          <a-table-column
            :title="$t('bossRespawn.column.eventName')"
            :width="150"
            align="center"
          >
            <template #cell="{ record }">
              <a-tooltip :content="record.eventName">
                <a-tag color="arcoblue">{{ record.eventName }}</a-tag>
              </a-tooltip>
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bossRespawn.column.boss')"
            :width="150"
            align="center"
          >
            <template #cell="{ record }">
              <a-space>
                <img
                  v-if="record.bossId"
                  :src="getIconUrl('mob', record.bossId)"
                  alt=""
                  class="mob-icon"
                />
                <span>{{ record.bossId }}</span>
              </a-space>
              <div class="sub-text">
                {{ $t('bossRespawn.column.mapId') }}: {{ record.mapId }}
              </div>
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bossRespawn.column.bossName')"
            :width="140"
            align="center"
          >
            <template #cell="{ record }">
              <a-input
                v-model="record.bossName"
                :max-length="64"
                :placeholder="$t('bossRespawn.bossName.placeholder')"
              />
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bossRespawn.column.spawnInterval')"
            :width="140"
            align="center"
          >
            <template #cell="{ record }">
              <a-input-number
                v-model="record.spawnInterval"
                :min="0"
                :style="{ width: '110px' }"
              />
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bossRespawn.column.effectiveMinutes')"
            :width="120"
            align="center"
          >
            <template #cell="{ record }">
              <span v-if="record.effectiveMinutes !== null">
                {{ record.effectiveMinutes }}
              </span>
              <a-tag v-else color="gray">
                {{ $t('bossRespawn.scriptDefault') }}
              </a-tag>
            </template>
          </a-table-column>
          <!-- <a-table-column :title="$t('bossRespawn.column.point')" :width="230" align="center">
            <template #cell="{ record }">
              <a-space>
                <a-input-number v-model="record.pointX" :placeholder="$t('bossRespawn.point.placeholder')" allow-clear
                  :style="{ width: '95px' }" />
                <a-input-number v-model="record.pointY" :placeholder="$t('bossRespawn.point.placeholder')" allow-clear
                  :style="{ width: '95px' }" />
              </a-space>
            </template>
          </a-table-column> -->
          <a-table-column
            :title="$t('bossRespawn.column.notice')"
            :width="220"
            align="center"
          >
            <template #cell="{ record }">
              <a-input
                v-model="record.notice"
                :max-length="255"
                allow-clear
                :placeholder="$t('bossRespawn.notice.placeholder')"
              />
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bossRespawn.column.enabled')"
            :width="90"
            align="center"
          >
            <template #cell="{ record }">
              <a-switch v-model="record.enabled" />
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('operation')"
            :width="140"
            align="center"
            fixed="right"
          >
            <template #cell="{ record }">
              <a-button size="mini" type="text" @click="saveClick(record)">
                {{ $t('button.save') }}
              </a-button>
              <a-popconfirm
                type="error"
                :content="$t('bossRespawn.reset.tips')"
                position="left"
                @ok="resetClick(record)"
              >
                <a-button size="mini" status="danger" type="text">
                  {{ $t('bossRespawn.reset.button') }}
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
    BossRespawnConfigResult,
    BossRespawnConfigSubmit,
    deleteBossRespawnConfig,
    getBossRespawnConfigList,
    reloadBossRespawnConfig,
    updateBossRespawnConfig,
  } from '@/api/bossRespawn';
  import { getIconUrl } from '@/utils/mapleStoryAPI';

  const { t } = useI18n();
  const { loading, setLoading } = useLoading(false);
  const configList = ref<BossRespawnConfigResult[]>([]);
  const filterText = ref<string>('');
  const reloading = ref<boolean>(false);

  /** 全局刷新时间倍率，取列表首条返回的展示字段 */
  const timeRate = computed<number>(() => configList.value[0]?.timeRate ?? 1);

  const filteredList = computed<BossRespawnConfigResult[]>(() => {
    const keyword = filterText.value.trim().toLowerCase();
    if (!keyword) {
      return configList.value;
    }
    return configList.value.filter(
      (item) =>
        item.eventName.toLowerCase().includes(keyword) ||
        String(item.bossId).includes(keyword) ||
        String(item.mapId).includes(keyword)
    );
  });

  const loadConfigs = async () => {
    setLoading(true);
    try {
      const { data } = await getBossRespawnConfigList();
      configList.value = data;
    } finally {
      setLoading(false);
    }
  };

  /**
   * 只提交后端真正消费的字段；
   * 坐标/公告用 null 表示回落脚本默认值（后端会写 NULL，而不是忽略该字段）。
   */
  const buildSubmitData = (
    record: BossRespawnConfigResult
  ): BossRespawnConfigSubmit => ({
    eventName: record.eventName,
    bossId: record.bossId,
    mapId: record.mapId,
    bossName: record.bossName || null,
    spawnInterval: record.spawnInterval,
    pointX: record.pointX ?? null,
    pointY: record.pointY ?? null,
    notice: record.notice || null,
    enabled: record.enabled,
  });

  const saveClick = async (record: BossRespawnConfigResult) => {
    if (record.spawnInterval === undefined || record.spawnInterval === null) {
      Message.warning(t('bossRespawn.validate.intervalRequired'));
      return;
    }
    // 坐标必须成对：脚本只判断 pointX 是否为空，只填 X 会生成出非法坐标
    const hasX = record.pointX !== undefined && record.pointX !== null;
    const hasY = record.pointY !== undefined && record.pointY !== null;
    if (hasX !== hasY) {
      Message.warning(t('bossRespawn.validate.pointPair'));
      return;
    }

    setLoading(true);
    try {
      await updateBossRespawnConfig(buildSubmitData(record));
      Message.success(t('bossRespawn.save.success'));
      await loadConfigs();
    } finally {
      setLoading(false);
    }
  };

  const resetClick = async (record: BossRespawnConfigResult) => {
    setLoading(true);
    try {
      await deleteBossRespawnConfig(record.eventName);
      Message.success(t('bossRespawn.reset.success'));
      await loadConfigs();
    } finally {
      setLoading(false);
    }
  };

  const reloadClick = async () => {
    reloading.value = true;
    try {
      await reloadBossRespawnConfig();
      Message.success(t('bossRespawn.reload.success'));
      await loadConfigs();
    } finally {
      reloading.value = false;
    }
  };

  loadConfigs();
</script>

<script lang="ts">
  export default {
    name: 'BossRespawn',
  };
</script>

<style scoped lang="less">
  .tips {
    margin-bottom: 12px;
  }

  .mob-icon {
    width: 32px;
    height: 32px;
    object-fit: contain;
  }

  .sub-text {
    color: var(--color-text-3);
    font-size: 12px;
  }

  :deep(.arco-table-th) {
    min-width: 30px;
  }
</style>
