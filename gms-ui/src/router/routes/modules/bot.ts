import { DEFAULT_LAYOUT } from '../base';
import { AppRouteRecordRaw } from '../types';

/**
 * 机器人管理：独立顶级菜单。
 * 放在 order: 2，排在「游戏管理」「玩家管理」之后。
 */
const BOT: AppRouteRecordRaw = {
  path: '/bot',
  name: 'bot',
  component: DEFAULT_LAYOUT,
  meta: {
    locale: 'menu.bot',
    requiresAuth: true,
    icon: 'icon-robot',
    order: 2,
  },
  children: [
    {
      path: 'list',
      name: 'botList',
      component: () => import('@/views/bot/list/index.vue'),
      meta: {
        locale: 'menu.bot.list',
        requiresAuth: true,
        roles: ['admin'],
      },
    },
    {
      path: 'equipPool',
      name: 'botEquipPool',
      component: () => import('@/views/bot/equipPool/index.vue'),
      meta: {
        locale: 'menu.bot.equipPool',
        requiresAuth: true,
        roles: ['admin'],
      },
    },
    {
      path: 'dialogue',
      name: 'botDialogue',
      component: () => import('@/views/bot/dialogue/index.vue'),
      meta: {
        locale: 'menu.bot.dialogue',
        requiresAuth: true,
        roles: ['admin'],
      },
    },
  ],
};

export default BOT;
