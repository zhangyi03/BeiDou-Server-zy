<template>
  <div class="container">
    <Breadcrumb />
    <a-card class="general-card" :title="$t('menu.bot.list')">
      <a-alert class="tips" type="info" show-icon>
        {{ $t('bot.list.tips') }}
      </a-alert>

      <a-row>
        <a-col>
          <a-space>
            <a-input
              v-model="filterName"
              :placeholder="$t('bot.list.filter.name')"
              allow-clear
              :style="{ width: '220px' }"
              @keydown.enter="loadData(1)"
            />
            <a-button type="primary" @click="loadData(1)">
              {{ $t('button.search') }}
            </a-button>
            <a-button @click="resetClick">
              {{ $t('button.reset') }}
            </a-button>
            <a-button type="primary" status="success" @click="openCreate">
              <template #icon><icon-plus /></template>
              {{ $t('bot.list.create') }}
            </a-button>
          </a-space>
        </a-col>
      </a-row>

      <a-table
        row-key="id"
        :loading="loading"
        :data="tableData"
        column-resizable
        :pagination="false"
        :bordered="{ cell: true }"
        :scroll="{ x: 1680 }"
        style="margin-top: 12px"
      >
        <template #columns>
          <a-table-column title="ID" data-index="id" :width="70" align="center" />
          <a-table-column
            :title="$t('bot.list.column.name')"
            data-index="name"
            :width="150"
            align="center"
          >
            <template #cell="{ record }">
              <span>{{ record.name }}</span>
              <div class="sub-text">cid {{ record.characterId }}</div>
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bot.list.column.level')"
            :width="110"
            align="center"
          >
            <template #cell="{ record }">
              <a-input-number v-model="record.level" :min="1" :max="200" size="small" />
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bot.list.column.job')"
            :width="120"
            align="center"
          >
            <template #cell="{ record }">
              <a-input-number v-model="record.job" :min="0" size="small" />
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bot.list.column.type')"
            :width="170"
            align="center"
          >
            <template #cell="{ record }">
              <a-select v-model="record.botType" size="small">
                <a-option
                  v-for="opt in botTypeOptions"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </a-option>
              </a-select>
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bot.list.column.gender')"
            :width="80"
            align="center"
          >
            <template #cell="{ record }">
              {{ record.gender === 1 ? $t('bot.gender.female') : $t('bot.gender.male') }}
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bot.list.column.map')"
            :width="130"
            align="center"
          >
            <template #cell="{ record }">
              <a-input-number v-model="record.mapId" :min="0" size="small" />
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bot.list.column.position')"
            :width="190"
            align="center"
          >
            <template #cell="{ record }">
              <a-space>
                <a-input-number v-model="record.spawnX" size="small" :style="{ width: '85px' }" />
                <a-input-number v-model="record.spawnY" size="small" :style="{ width: '85px' }" />
              </a-space>
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bot.list.column.online')"
            :width="90"
            align="center"
          >
            <template #cell="{ record }">
              <a-tag :color="record.online ? 'green' : 'gray'">
                {{ record.online ? $t('bot.online') : $t('bot.offline') }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bot.list.column.autoStart')"
            :width="100"
            align="center"
          >
            <template #cell="{ record }">
              <a-switch v-model="record.autoStart" />
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('bot.list.column.note')"
            :width="160"
            align="center"
          >
            <template #cell="{ record }">
              <a-input v-model="record.note" :max-length="128" size="small" allow-clear />
            </template>
          </a-table-column>
          <a-table-column
            :title="$t('operation')"
            :width="300"
            align="center"
            fixed="right"
          >
            <template #cell="{ record }">
              <a-space>
                <a-button
                  v-if="record.online"
                  size="mini"
                  type="text"
                  status="warning"
                  @click="toggleOnline(record)"
                >
                  {{ $t('bot.list.button.despawn') }}
                </a-button>
                <a-button
                  v-else
                  size="mini"
                  type="text"
                  status="success"
                  @click="toggleOnline(record)"
                >
                  {{ $t('bot.list.button.spawn') }}
                </a-button>
                <a-button size="mini" type="text" @click="saveClick(record)">
                  {{ $t('button.save') }}
                </a-button>
                <a-popconfirm
                  :content="$t('bot.list.resetEquip.tips')"
                  position="left"
                  @ok="resetEquipClick(record)"
                >
                  <a-button size="mini" type="text" status="warning">
                    {{ $t('bot.list.button.resetEquip') }}
                  </a-button>
                </a-popconfirm>
                <a-popconfirm
                  type="error"
                  :content="$t('bot.list.delete.tips')"
                  position="left"
                  @ok="deleteClick(record)"
                >
                  <a-button size="mini" type="text" status="danger">
                    {{ $t('button.delete') }}
                  </a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>

      <a-pagination
        style="margin-top: 16px"
        :total="total"
        :page-size="size"
        :current="page"
        show-total
        show-jumper
        show-page-size
        :page-size-options="[10, 20, 50, 100]"
        @change="pageChange"
        @page-size-change="pageSizeChange"
      />
    </a-card>

    <a-modal
      v-model:visible="createVisible"
      :title="$t('bot.list.create')"
      :ok-loading="creating"
      :mask-closable="false"
      :esc-to-close="false"
      :ok-text="$t('button.submit')"
      :on-before-ok="submitCreate"
    >
      <a-form ref="createFormRef" :model="createForm" :rules="createRules">
        <a-form-item field="name" :label="$t('bot.list.column.name')">
          <a-input
            v-model="createForm.name"
            :placeholder="$t('bot.list.create.name.tips')"
            allow-clear
          />
        </a-form-item>
        <a-form-item field="level" :label="$t('bot.list.column.level')">
          <a-input-number
            v-model="createForm.level"
            :min="1"
            :max="200"
            :placeholder="$t('bot.list.create.level.tips')"
            allow-clear
          />
        </a-form-item>
        <a-form-item field="job" :label="$t('bot.list.column.job')">
          <a-input-number
            v-model="createForm.job"
            :min="0"
            :placeholder="$t('bot.list.create.job.tips')"
            allow-clear
          />
        </a-form-item>
        <a-form-item
          field="type"
          :label="$t('bot.list.column.type')"
          :extra="$t('bot.list.type.tips')"
        >
          <a-select v-model="createForm.botType">
            <a-option
              v-for="opt in botTypeOptions"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="gender" :label="$t('bot.list.column.gender')">
          <a-select v-model="createForm.gender" allow-clear :placeholder="$t('bot.list.create.random')">
            <a-option :value="0">{{ $t('bot.gender.male') }}</a-option>
            <a-option :value="1">{{ $t('bot.gender.female') }}</a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="mapId" :label="$t('bot.list.column.map')">
          <a-input-number v-model="createForm.mapId" :min="0" />
        </a-form-item>
        <a-form-item field="spawnX" :label="$t('bot.list.column.position')">
          <a-space>
            <a-input-number v-model="createForm.spawnX" :style="{ width: '120px' }" />
            <a-input-number v-model="createForm.spawnY" :style="{ width: '120px' }" />
          </a-space>
        </a-form-item>
        <a-form-item field="autoStart" :label="$t('bot.list.column.autoStart')">
          <a-switch v-model="createForm.autoStart" />
        </a-form-item>
        <a-form-item field="note" :label="$t('bot.list.column.note')">
          <a-input v-model="createForm.note" :max-length="128" allow-clear />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import { useI18n } from 'vue-i18n';
  import useLoading from '@/hooks/loading';
  import {
    BotCreateForm,
    BotProfileResult,
    BotUpdateForm,
    createBot,
    deleteBots,
    despawnBots,
    getBotList,
    resetBotEquip,
    spawnBots,
    updateBot,
  } from '@/api/bot';
  import {
    BOT_TYPE_DEFAULT,
    BOT_TYPE_OPTIONS,
  } from '@/views/bot/botTypes';

  const { t } = useI18n();
  const { loading, setLoading } = useLoading(false);

  /** 类型下拉：value 是落库码，label 走 i18n */
  const botTypeOptions = computed(() =>
    BOT_TYPE_OPTIONS.map((o) => ({ value: o.value, label: t(o.labelKey) })),
  );

  const tableData = ref<BotProfileResult[]>([]);
  const filterName = ref('');
  const total = ref(0);
  const page = ref(1);
  const size = ref(20);

  const createVisible = ref(false);
  const creating = ref(false);
  const createFormRef = ref();
  const createForm = reactive<BotCreateForm>({
    name: '',
    level: undefined,
    job: undefined,
    gender: undefined,
    mapId: 910000000,
    spawnX: 0,
    spawnY: 0,
    autoStart: false,
    botType: BOT_TYPE_DEFAULT,
    note: '',
  });
  const createRules = {
    name: [{ maxLength: 13, message: t('bot.list.validate.name') }],
  };

  const loadData = async (targetPage?: number) => {
    if (targetPage) {
      page.value = targetPage;
    }
    setLoading(true);
    try {
      const { data } = await getBotList(page.value, size.value, filterName.value.trim());
      tableData.value = data.records || [];
      total.value = data.totalRow || 0;
    } finally {
      setLoading(false);
    }
  };

  const resetClick = () => {
    filterName.value = '';
    loadData(1);
  };

  const pageChange = (current: number) => {
    page.value = current;
    loadData();
  };

  const pageSizeChange = (pageSize: number) => {
    page.value = 1;
    size.value = pageSize;
    loadData();
  };

  const openCreate = () => {
    createForm.name = '';
    createForm.level = undefined;
    createForm.job = undefined;
    createForm.gender = undefined;
    createForm.mapId = 910000000;
    createForm.spawnX = 0;
    createForm.spawnY = 0;
    createForm.autoStart = false;
    createForm.botType = BOT_TYPE_DEFAULT;
    createForm.note = '';
    createVisible.value = true;
  };

  const submitCreate = async () => {
    const validError = await createFormRef.value?.validate();
    if (validError) {
      return false;
    }
    creating.value = true;
    try {
      await createBot({ ...createForm });
      Message.success(t('bot.list.create.success'));
      createVisible.value = false;
      await loadData(1);
      return true;
    } finally {
      creating.value = false;
    }
  };

  const buildUpdate = (record: BotProfileResult): BotUpdateForm => ({
    id: record.id,
    level: record.level,
    job: record.job,
    mapId: record.mapId,
    spawnX: record.spawnX,
    spawnY: record.spawnY,
    autoStart: record.autoStart,
    botType: record.botType,
    note: record.note || '',
  });

  const saveClick = async (record: BotProfileResult) => {
    setLoading(true);
    try {
      await updateBot(buildUpdate(record));
      Message.success(t('bot.list.save.success'));
      await loadData();
    } finally {
      setLoading(false);
    }
  };

  const toggleOnline = async (record: BotProfileResult) => {
    setLoading(true);
    try {
      if (record.online) {
        await despawnBots([record.id]);
        Message.success(t('bot.list.despawn.success'));
      } else {
        await spawnBots([record.id]);
        Message.success(t('bot.list.spawn.success'));
      }
      await loadData();
    } finally {
      setLoading(false);
    }
  };

  const resetEquipClick = async (record: BotProfileResult) => {
    setLoading(true);
    try {
      await resetBotEquip([record.id]);
      Message.success(t('bot.list.resetEquip.success'));
      await loadData();
    } finally {
      setLoading(false);
    }
  };

  const deleteClick = async (record: BotProfileResult) => {
    setLoading(true);
    try {
      await deleteBots([record.id]);
      Message.success(t('bot.list.delete.success'));
      await loadData();
    } finally {
      setLoading(false);
    }
  };

  loadData();
</script>

<script lang="ts">
  export default {
    name: 'BotList',
  };
</script>

<style scoped lang="less">
  .tips {
    margin-bottom: 12px;
  }

  .sub-text {
    color: var(--color-text-3);
    font-size: 12px;
  }
</style>
