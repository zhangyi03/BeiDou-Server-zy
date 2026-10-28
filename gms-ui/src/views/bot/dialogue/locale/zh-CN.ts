export default {
  'bot.dialogue.tips':
    '对话池存放机器人跟玩家「打照面」时说的文案，全部改动保存后立即热重载，不用重启服务端。摆摊名称在每次开摊时抽一条当摊位标题；喊话内容由摊主在自由市场里原地喊出（每 12~20 秒一句）。两个池子各自独立抽取，池里一条都没有时退回代码里的兜底文案。',
  'bot.dialogue.tab.shopName': '摆摊名称',
  'bot.dialogue.tab.shout': '喊话内容',
  'bot.dialogue.tab.shopName.tips':
    '开摊时从这个池里抽一条，作为个人商店的摊位标题显示在角色头顶的摊位框里。客户端摊位框只有一行，建议控制在 20 个字以内；权重越高越容易被抽到。',
  'bot.dialogue.tab.shout.tips':
    '摊主每隔 12~20 秒从这里抽一句，用普通聊天包原地广播，附近玩家都能看到。权重越高越容易出现；同一句不会连发（下一句重新抽）。',
  'bot.dialogue.addRow': '新增一行',
  'bot.dialogue.deleteSelected': '删除选中',
  'bot.dialogue.reload': '重新加载',
  'bot.dialogue.total': '共 {total} 条',
  'bot.dialogue.new': '未保存',
  'bot.dialogue.column.content': '文案内容',
  'bot.dialogue.column.weight': '权重',
  'bot.dialogue.column.note': '备注',
  'bot.dialogue.column.enabled': '启用',
  'bot.dialogue.delete.tips': '删除后机器人不会再抽到这句，确认删除？',
  'bot.dialogue.save.success': '已保存并重载',
  'bot.dialogue.delete.success': '已删除并重载',
  'bot.dialogue.reload.success': '对话池已重新读库',
  'bot.dialogue.validate.required': '文案内容不能为空',
  'bot.dialogue.validate.selectSaved': '选中的都是未保存的草稿行，请先保存',
};
