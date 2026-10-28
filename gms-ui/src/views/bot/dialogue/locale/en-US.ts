export default {
  'bot.dialogue.tips':
    'The dialogue pool holds what bots say when they run into players. Every change is saved and hot-reloaded — no restart needed. A shop name is rolled once each time a stall opens and becomes the PlayerShop title; shout lines are broadcast by the merchant in place every 12-20 seconds. The two pools are rolled independently, and each falls back to the built-in text when empty.',
  'bot.dialogue.tab.shopName': 'Shop Names',
  'bot.dialogue.tab.shout': 'Shout Lines',
  'bot.dialogue.tab.shopName.tips':
    'One entry is rolled when a stall opens and shown as the PlayerShop title above the owner. The client shows a single line, so keep it under ~20 characters; higher weight means more likely to be rolled.',
  'bot.dialogue.tab.shout.tips':
    'The merchant broadcasts one line every 12-20 seconds via the normal chat packet, so nearby players see it. Higher weight means more likely to appear; consecutive shouts are rolled independently.',
  'bot.dialogue.addRow': 'Add Row',
  'bot.dialogue.deleteSelected': 'Delete Selected',
  'bot.dialogue.reload': 'Reload',
  'bot.dialogue.total': '{total} rows',
  'bot.dialogue.new': 'Unsaved',
  'bot.dialogue.column.content': 'Text',
  'bot.dialogue.column.weight': 'Weight',
  'bot.dialogue.column.note': 'Note',
  'bot.dialogue.column.enabled': 'Enabled',
  'bot.dialogue.delete.tips': 'Bots will stop rolling this line. Delete?',
  'bot.dialogue.save.success': 'Saved and reloaded',
  'bot.dialogue.delete.success': 'Deleted and reloaded',
  'bot.dialogue.reload.success': 'Dialogue pool reloaded from DB',
  'bot.dialogue.validate.required': 'Text is required',
  'bot.dialogue.validate.selectSaved': 'Selected rows are unsaved drafts, save them first',
};
