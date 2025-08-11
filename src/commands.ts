import { DiscordRequest } from "./utils";

// Simple test command
const TEST_COMMAND = {
  name: 'test',
  description: 'Basic command',
  type: 1,
  integration_types: [0, 1],
  contexts: [0, 1, 2],
};

const ALL_COMMANDS = [TEST_COMMAND];

// `npm run register` will add the commands to the bot
try {
  // This is calling the bulk overwrite endpoint: https://discord.com/developers/docs/interactions/application-commands#bulk-overwrite-global-application-commands
  await DiscordRequest(`applications/${process.env.APP_ID}/commands`, { method: 'PUT', body: ALL_COMMANDS });
} catch (err) {
  console.error(err);
}
