import { mergeConfig } from 'vite';
import baseConfig from './vite.config.base';

// 注意：这里不再挂 vite-plugin-eslint。
// 该插件默认把 error 级 lint 问题（多为 prettier 格式）当编译失败抛出，
// Vite 会对该 .vue 模块返回 500 + ErrorOverlay，导致开发时整页红屏。
// 代码风格请在提交前手动执行 eslint（或依赖 lint-staged / 编辑器格式化）。
export default mergeConfig(
  {
    mode: 'development',
    server: {
      open: true,
      fs: {
        strict: true,
      },
    },
  },
  baseConfig
);
