import { APIBaseInteraction, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { PartyEditCommand, ServerMember } from '../../../types';
import { globalState } from '../../..';
import { api } from '../../../utils';
import { PoliticalParty } from '../../../political-party';
import { escapeMarkdown } from '@discordjs/formatters';

export default class extends PartyEditCommand {
	async handleInternal(interaction: APIBaseInteraction<any, any>, sender: ServerMember, party: PoliticalParty): Promise<any> {
		// holy fuck
		const name = interaction.data.options[0].options[0].options[0].value;
		// Change the color
		return api(`/guilds/${globalState.serverData.serverID}/roles/${party.getID()}`, {
			method: 'PATCH',
			body: {
				name,
			},
		}).then(() => {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Changed party name to ${escapeMarkdown(name)}`,
				},
			};
		});
	}
}
