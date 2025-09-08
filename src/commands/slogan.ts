import { APIBaseInteraction, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { BaseCommand, Candidate, ServerMember } from '../types';
import { globalState } from '..';
import { escapeMarkdown } from '@discordjs/formatters';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>, sender: ServerMember): Promise<any> {
		const slogan = escapeMarkdown(interaction.data.options[0].value).replace(/[\n\r\t]/g, ''); // remove fancy line chars

		if (!globalState.isPresidentialVoteActive()) {
			// Nick complaint. He wants to change the slogan when you're not campainging if you're the President
			if (globalState.serverData.presidentID == sender.getID()) {
				globalState.serverData.slogan = slogan;
				return {
					type: InteractionResponseType.ChannelMessageWithSource,
					data: {
						content: `Updated your slogan, Mr. President. You'll see it during the next campaign`,
					},
				};
			}
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `There's no active presidential election`,
				},
			};
		}

		console.log('Finding the candidate');
		// Check if already campaigning
		for (const sample of globalState.serverData.candidates) {
			if (sample.getID() == sender.getID()) {
				sample.slogan = slogan;
				return {
					type: InteractionResponseType.ChannelMessageWithSource,
					data: {
						content: `Updated your slogan to "${slogan}". The vote won't reflect your new slogan until a few hours have passed!`,
					},
				};
			}
		}

		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				flags: MessageFlags.Ephemeral,
				content: `You're not campaigning!`,
			},
		};
	}
}
