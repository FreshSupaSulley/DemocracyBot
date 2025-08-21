import { APIBaseInteraction, APIMessage, ChannelType, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { BaseCommand } from '../types';
import { api } from '../utils';
import { globalState } from '..';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>): Promise<any> {
		// Guarantee we're the owner AND we're in a DM
		if (!interaction.user || interaction.user.id !== globalState.env.OWNER_ID) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `Nope`,
				},
			};
		}

		// This might take a while so defer the reply
		await api(`interactions/${interaction.id}/${interaction.token}/callback`, {
			method: 'POST',
			body: {
				type: InteractionResponseType.DeferredChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
				},
			},
		});

		// Lets grab 50 ig
		const count = 50; // range was 1-100 last i checked
		const messages: APIMessage[] = await api(`channels/${interaction.channel?.id}/messages?limit=${count}`);
		console.log(`Deleting ${messages.length} message(s)`);
		for (const message of messages) {
			// We can't delete user messages
			// for some reason .bot isn't defined?
			if (message.author.id === globalState.env.OWNER_ID) continue;
			await api(`channels/${interaction.channel?.id}/messages/${message.id}`, {
				method: 'DELETE',
			}).catch((e) => {
				console.error('Failed to delete message:', message);
				console.error('Error:', e);
			});
		}

		// Delete the OG deferred message indicating we're done
		await api(`webhooks/${globalState.env.APP_ID}/${interaction.token}/messages/@original`, {
			method: 'DELETE',
		});

		// Whatever we return won't matter anymore
		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				flags: MessageFlags.Ephemeral,
				content: `This is going to be scrapped`,
			},
		};
	}
}
