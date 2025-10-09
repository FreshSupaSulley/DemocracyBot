// Entrypoint of the bot
// https://github.com/discord/cloudflare-sample-app/blob/main/src/server.js
import { AutoRouter, json } from 'itty-router';
import { InteractionResponseType, InteractionType, verifyKey } from 'discord-interactions';
import { APIBaseInteraction, APIDMChannel, ApplicationCommandOptionType, MessageFlags } from 'discord-api-types/v10';
import ServerData, { BaseCommand } from './types';
import { State } from './state';
import { instanceToPlain, plainToInstance } from 'class-transformer';
import register from './commands';
import { api } from './utils';

// What we use to access any server data / env vars / anything requiring a state
export let globalState: State = new State({}, {} as ServerData);

const router = AutoRouter();

// Overrides dev server data
router.get('/', async (request, env) => {
	const devEnv = env.DEV_ENV;
	console.log(devEnv);
	if (!devEnv) return new Response('nope');
	// Grab the template that best fits this env
	const json: ServerData = require(`./${devEnv == 'dev' ? 'devS' : 's'}erverData.json`);
	// The await here is important (too big of shit will cut off the writing process)
	await env.DBOT.put('data', JSON.stringify(json));
	return new Response(JSON.stringify(json));
});

// Grabbing the server data
router.get('/data', async (request, env) => {
	// meh who cares if the world can see it
	// if (env.ENV !== 'DEV') return new Response('nope');
	const rawData = await env.DBOT.get('data');
	let response;
	try {
		response = JSON.stringify(plainToInstance(ServerData, JSON.parse(rawData)));
	} catch (e: any) {
		console.error(e);
		response = `Parsing failed: ${e.stack ?? e}\n\n${rawData}`;
	}
	return new Response(response);
});

// Registering slash commands
router.get('/register', async (request, env) => {
	if (env.ENV !== 'DEV') return new Response('nope');
	globalState = new State(env, plainToInstance(ServerData, JSON.parse(await env.DBOT.get('data'))));
	const response = await register(env);
	return new Response(JSON.stringify(response));
});

// Our tick method
async function tick(env: any) {
	await errorWrapper(env, () => globalState.tick());
}

async function handleSlashCommand(env: any, interaction: APIBaseInteraction<any, any>) {
	const response = await errorWrapper(env, async () => {
		// Ensure we're only going to respond to the new server
		if (interaction.guild && interaction.guild.id !== globalState.serverData.serverID) {
			return json({
				type: InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `wrong server bimbo`,
				},
			});
		}

		const fullCommandName = getFullCommandName(interaction.data);
		console.log('Received', fullCommandName);

		// Find the command at the subfolder
		const commandModule = await import(`./commands/${fullCommandName}.ts`);
		const command: BaseCommand = commandModule.default;

		if (typeof command !== 'function') {
			throw new Error(`No valid default export class found in ${fullCommandName}`);
		}

		// Instantiate the command class with data
		const CommandClass = commandModule.default as new () => BaseCommand;
		// Run the command
		const sender = globalState.getMember(interaction);
		const response = await new CommandClass().handle(interaction, sender);
		if (!response) {
			throw new Error(`${fullCommandName} command didn't return a response`);
		}
		return json(response);
	});
	// If an error occurred
	if (!response) {
		return json({
			type: InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
			data: {
				content: `<@${env.OWNER_ID}> hey dumbass your bot broke`,
			},
		});
	}
	return response;
}

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
		return await handleSlashCommand(env, interaction);
	}

	console.error('Unknown post');
	return json({ error: 'Unknown Type' }, { status: 400 });
});

function getFullCommandName(data: any): string {
	let type = data.options?.[0].type;
	if (type == ApplicationCommandOptionType.SubcommandGroup || type == ApplicationCommandOptionType.Subcommand) {
		// options is an empty array when its part of a subcommand group
		if (data.options[0].options.length != 0) {
			return `${data.name}/${getFullCommandName(data.options[0])}`;
		}
		return `${data.name}/${data.options[0].name}`;
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

async function errorWrapper(env: any, fn: () => Promise<any>) {
	let rawData;
	try {
		rawData = plainToInstance(ServerData, JSON.parse(await env.DBOT.get('data')));
		if (!rawData) throw new Error('No server data found in KV');

		// Ensure the state is initialized
		globalState = new State(env, plainToInstance(ServerData, JSON.parse(await env.DBOT.get('data'))));

		// Now run the function
		return await fn();
	} catch (ogError) {
		console.error('Something went wrong:', ogError);

		// DM to me
		await api(`users/@me/channels`, {
			method: 'POST',
			body: {
				recipient_id: env.OWNER_ID,
			},
		})
			.then((response: APIDMChannel) => {
				return api(`channels/${response.id}/messages`, {
					method: 'POST',
					body: {
						content: `An error occurred:\n\`\`\`${ogError instanceof Error ? ogError.stack : ogError}\`\`\``,
					},
				});
			})
			.catch((e) => {
				console.error('Failed to send error to owner:', e);
			});
	} finally {
		// THIS CODE IN THIS BLOCK CAN NEVER FAIL OTHERWISE THE ENTIRE COMMAND FAILS
		// ^ because the return in the try will be abandoned if it fails
		// Check if it changed
		const newDataRaw = JSON.stringify(instanceToPlain(globalState.serverData));
		const oldData = JSON.stringify(instanceToPlain(rawData));
		if (!!oldData && oldData !== newDataRaw) {
			console.log('Server data changed!');
			console.log('Old:', oldData);
			console.log('New:', newDataRaw);
			await env.DBOT.put('data', newDataRaw);
		}
	}
}

// Combines it all into one export
const server = {
	verifyDiscordRequest,
	fetch: router.fetch,
	// For our ticking
	async scheduled(_event: ScheduledEvent, env: any, ctx: ExecutionContext) {
		ctx.waitUntil(tick(env));
	},
};

export default server;
