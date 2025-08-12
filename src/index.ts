// Entrypoint of the bot
// https://github.com/discord/cloudflare-sample-app/blob/main/src/server.js
import { AutoRouter, json } from 'itty-router';
import { InteractionResponseType, InteractionType, verifyKey } from 'discord-interactions';
import { ApplicationCommandOptionType } from 'discord-api-types/v10';
import { BaseCommand } from './commands/command';
import ServerData from './types';

const router = AutoRouter();

router.get('/', async (request, env) => {
	// const json: ServerData = require('./serverData.json');
	// env.DBOT.put("data", JSON.stringify(json));
	// console.log(await env.DBOT.list());
	// console.log(await env.DBOT.get("data"))
	return new Response(`good job champ`);
});

// Forwards interactions for handling in their appropriate files
router.post('/', async (request, env) => {
	const { isValid, interaction } = await server.verifyDiscordRequest(request, env);
	if (!isValid || !interaction) {
		return new Response('Bad request signature', { status: 401 });
	}

	if (interaction.type === InteractionType.PING) {
		// The `PING` message is used during the initial webhook handshake, and is
		// required to configure the webhook in the developer portal.
		return json({
			type: InteractionResponseType.PONG,
		});
	}

	if (interaction.type === InteractionType.APPLICATION_COMMAND) {
		const fullCommandName = getFullCommandName(interaction.data);
		console.log('Received', fullCommandName);
		// Find the command at the subfolder
		try {
			const commandModule = await import(`./commands/${fullCommandName}.ts`);
			const command: BaseCommand = commandModule.default;

			if (typeof command !== 'function') {
				throw new Error(`No valid default export class found in ${fullCommandName}`);
			}

			const rawData = await env.DBOT.get('data');
			if (!rawData) throw new Error('No server data found in KV');

			// Instantiate the command class with data
			const CommandClass = commandModule.default as CommandConstructor;
			const commandInstance = new CommandClass(rawData);

			// Check for handle method
			if (typeof commandInstance.handle !== 'function') {
				throw new Error(`No valid handle() method found in ${fullCommandName}`);
			}

			// Run the command
			const response = await commandInstance.handle(interaction);
			return json(response);
		} catch (e) {
			console.error('Something went wrong responding to slash command', e);
			return json({
				type: InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
				data: {
					content: `<@${env.OWNER_ID}> hey dumbass your bot broke`,
				},
			});
		}
	}

	console.error('Unknown Type');
	return json({ error: 'Unknown Type' }, { status: 400 });
});

type CommandConstructor = new (data: ServerData) => BaseCommand;

function getFullCommandName(data: any): string {
	let type = data.options?.[0].type;
	if (type == ApplicationCommandOptionType.SubcommandGroup || type == ApplicationCommandOptionType.Subcommand) {
		console.log(data.options);
		return `${data.name}/` + getFullCommandName(data.options[0]);
	}
	return data.name;
}

router.all('*', () => new Response('what', { status: 404 }));

async function verifyDiscordRequest(request: any, env: any) {
	const signature = request.headers.get('x-signature-ed25519');
	const timestamp = request.headers.get('x-signature-timestamp');
	const body = await request.text();
	const isValidRequest = signature && timestamp && (await verifyKey(body, signature, timestamp, env.APP_PUBLIC_KEY));
	if (!isValidRequest) {
		return { isValid: false };
	}
	return { interaction: JSON.parse(body), isValid: true };
}

const server = {
	verifyDiscordRequest,
	fetch: router.fetch,
};

export default server;
