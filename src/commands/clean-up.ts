import { APIBaseInteraction, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { BaseCommand } from '../types';
import { globalState } from '..';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>): Promise<any> {
		// Guarantee we're the owner AND we're in a DM
		if (!interaction.user || interaction.user.id !== globalState.env.OWNER_ID) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Nope`,
				},
			};
		}

		// Will be scooped up in scheduled tasks
		if (globalState.serverData.deleteMessagesChannel !== '0') {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Deletion already queued`,
				},
			};
		}

		globalState.serverData.deleteMessagesChannel = interaction.channel!.id;

		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: `Deletion queued`,
			},
		};
	}
}
